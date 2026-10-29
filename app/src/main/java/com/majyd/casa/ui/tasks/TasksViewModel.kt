package com.majyd.casa.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.TaskAssignee
import com.majyd.casa.data.model.TaskAssignmentType
import com.majyd.casa.data.model.TaskItem
import com.majyd.casa.data.model.TaskSuggestion
import com.majyd.casa.data.repository.TaskRepository
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.data.model.HouseholdState
import com.majyd.casa.util.Normalizer
import com.majyd.casa.notifications.NotificationScheduler
import com.majyd.casa.notifications.TaskNotificationPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class TaskFilter {
    ALL, MINE, PERSON, EVERYONE, UNASSIGNED, GROUP,
}

data class TaskDraft(
    val taskId: String? = null,
    val title: String = "",
    val notes: String = "",
    val assignee: TaskAssignee = TaskAssignee.UNASSIGNED,
    val completed: Boolean = false,
    val dueDate: String = TaskInputFormatters.formatDate(LocalDate.now()),
    val dueTime: String = "23:59",
    val reminderMinutesBefore: String = "60",
    val assignmentType: TaskAssignmentType = TaskAssignmentType.NONE,
    val personIds: Set<String> = emptySet(),
    val groupId: String? = null,
)

data class TasksUiState(
    val pending: List<TaskItem> = emptyList(),
    val overdue: List<TaskItem> = emptyList(),
    val today: List<TaskItem> = emptyList(),
    val upcoming: List<TaskItem> = emptyList(),
    val completed: List<TaskItem> = emptyList(),
    val deleted: List<TaskItem> = emptyList(),
    val draft: TaskDraft = TaskDraft(),
    val filter: TaskFilter = TaskFilter.ALL,
    val household: HouseholdState = HouseholdState(),
    val totalPending: Int = 0,
    val unassignedPending: Int = 0,
    val everyonePending: Int = 0,
    val personPendingCounts: Map<String, Int> = emptyMap(),
    val minePending: Int = 0,
    val selectedGroupId: String? = null,
    val selectedPersonId: String? = null,
    val groupPendingCounts: Map<String, Int> = emptyMap(),
    val suggestions: List<TaskSuggestion> = emptyList(),
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val householdRepository: HouseholdRepository,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {

    private val draft = MutableStateFlow(TaskDraft())
    private val tasks = taskRepository.observeTasks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val household = householdRepository.observeState().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HouseholdState(),
    )
    private val filter = MutableStateFlow(TaskFilter.ALL)
    private val selectedGroupId = MutableStateFlow<String?>(null)
    private val selectedPersonId = MutableStateFlow<String?>(null)
    private val suggestions = taskRepository.observeSuggestions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val baseUiState: StateFlow<TasksUiState> = combine(
        listOf(tasks, draft, household, filter, selectedGroupId, selectedPersonId),
    ) { values ->
        val items = values[0] as List<TaskItem>
        val draftValue = values[1] as TaskDraft
        val householdValue = values[2] as HouseholdState
        val filterValue = values[3] as TaskFilter
        val groupId = values[4] as String?
        val personId = values[5] as String?
        val visibleItems = items.filter { matchesFilter(it, filterValue, householdValue, groupId, personId) }
        val activeItems = visibleItems.filterNot { it.completed || it.deleted }
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        TasksUiState(
            pending = activeItems,
            overdue = activeItems.filter { isOverdue(it, today, now) }.sortedWith(compareBy<TaskItem> { it.dueDate }.thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }),
            today = activeItems.filter { it.dueDate == today.toEpochDay() && !isOverdue(it, today, now) },
            upcoming = activeItems.filter { it.dueDate <= 0L || (it.dueDate > today.toEpochDay()) }.sortedBy { if (it.dueDate <= 0L) Long.MAX_VALUE else it.dueDate },
            completed = visibleItems.filter { it.completed && !it.deleted },
            deleted = visibleItems.filter { it.deleted },
            draft = draftValue,
            filter = filterValue,
            household = householdValue,
            totalPending = items.count { !it.completed && !it.deleted },
            unassignedPending = items.count { !it.completed && !it.deleted && it.assignmentType == TaskAssignmentType.NONE },
            everyonePending = items.count { !it.completed && !it.deleted && it.assignmentType == TaskAssignmentType.EVERYONE },
            personPendingCounts = householdValue.people.associate { person ->
                person.id to items.count { item ->
                    !item.completed && !item.deleted && belongsToPerson(item, person.id, householdValue)
                }
            },
            minePending = items.count { !it.completed && !it.deleted && matchesFilter(it, TaskFilter.MINE, householdValue, null, null) },
            selectedGroupId = groupId,
            selectedPersonId = personId,
            groupPendingCounts = householdValue.groups.associate { group ->
                group.id to items.count { !it.completed && !it.deleted && it.groupId == group.id }
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasksUiState(),
    )

    val uiState: StateFlow<TasksUiState> = baseUiState.combine(suggestions) { state, suggestionsValue ->
        state.copy(
            suggestions = suggestionsValue.filter { suggestion ->
                state.draft.title.isNotBlank() && Normalizer.normalizeText(suggestion.title).startsWith(Normalizer.normalizeText(state.draft.title))
            }.take(5),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasksUiState(),
    )

    init {
        viewModelScope.launch {
            taskRepository.ensureDefaults()
        }
    }

    fun editTask(task: TaskItem) {
        draft.value = TaskDraft(
            taskId = task.id,
            title = task.title,
            notes = task.notes,
            assignee = task.assignee,
            completed = task.completed,
            dueDate = if (task.dueDate > 0L) TaskInputFormatters.formatDate(LocalDate.ofEpochDay(task.dueDate)) else TaskInputFormatters.formatDate(LocalDate.now()),
            dueTime = task.dueTimeMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "23:59",
            reminderMinutesBefore = task.reminderMinutesBefore?.toString().orEmpty(),
            assignmentType = task.assignmentType,
            personIds = task.personIds,
            groupId = task.groupId,
        )
    }

    fun clearDraft() {
        draft.value = TaskDraft()
    }

    fun selectSuggestion(suggestion: TaskSuggestion) {
        draft.update {
            it.copy(
                title = suggestion.title,
                notes = suggestion.notes,
                assignmentType = suggestion.assignmentType,
                personIds = suggestion.personIds,
                groupId = suggestion.groupId,
                dueTime = suggestion.dueTimeMinutes?.let { minutes -> "%02d:%02d".format(minutes / 60, minutes % 60) } ?: "23:59",
                reminderMinutesBefore = suggestion.reminderMinutesBefore?.toString() ?: "60",
                // A reused task always needs a new due date selected by the user.
                dueDate = TaskInputFormatters.formatDate(LocalDate.now()),
            )
        }
    }

    fun selectFilter(value: TaskFilter) {
        filter.value = value
        if (value != TaskFilter.PERSON) selectedPersonId.value = null
    }
    fun selectPersonFilter(personId: String) {
        selectedPersonId.value = personId
        filter.value = TaskFilter.PERSON
    }
    fun selectGroupFilter(groupId: String) {
        selectedGroupId.value = groupId
        selectedPersonId.value = null
        filter.value = TaskFilter.GROUP
    }

    private fun matchesFilter(task: TaskItem, selected: TaskFilter, household: HouseholdState, groupId: String?, personId: String?): Boolean {
        return when (selected) {
            TaskFilter.ALL -> true
            TaskFilter.UNASSIGNED -> task.assignmentType == TaskAssignmentType.NONE
            TaskFilter.EVERYONE -> task.assignmentType == TaskAssignmentType.EVERYONE
            TaskFilter.GROUP -> task.assignmentType == TaskAssignmentType.GROUP && task.groupId == groupId
            TaskFilter.PERSON -> personId?.let { selectedPersonId -> belongsToPerson(task, selectedPersonId, household) } == true
            TaskFilter.MINE -> {
                val personId = household.assignedPersonId
                belongsToPerson(task, personId, household)
            }
        }
    }

    private fun belongsToPerson(task: TaskItem, personId: String?, household: HouseholdState): Boolean {
        if (personId == null) return false
        return when (task.assignmentType) {
            // Global and group assignments have their own summary buttons. They must not
            // inflate a person's direct counter or the current user's counter.
            TaskAssignmentType.EVERYONE,
            TaskAssignmentType.GROUP,
            -> false
            TaskAssignmentType.PERSON,
            TaskAssignmentType.MULTIPLE_PEOPLE,
            -> task.personIds.contains(personId)
            TaskAssignmentType.NONE -> when (household.people.indexOfFirst { it.id == personId }) {
                // Compatibility for tasks created before personIds was introduced.
                0 -> task.assignee == TaskAssignee.PERSON_ONE || task.assignee == TaskAssignee.BOTH
                1 -> task.assignee == TaskAssignee.PERSON_TWO || task.assignee == TaskAssignee.BOTH
                else -> false
            }
        }
    }

    private fun isOverdue(task: TaskItem, today: LocalDate, nowMillis: Long): Boolean {
        if (task.dueDate <= 0L) return false
        if (task.dueDate < today.toEpochDay()) return true
        if (task.dueDate > today.toEpochDay() || task.dueTimeMinutes == null) return false
        val due = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + task.dueTimeMinutes * 60_000L
        return due < nowMillis
    }

    fun updateTitle(value: String) = draft.update { it.copy(title = value) }
    fun updateNotes(value: String) = draft.update { it.copy(notes = value) }
    fun updateAssignee(value: TaskAssignee) = draft.update { it.copy(assignee = value) }
    fun updateDueDate(value: String) = draft.update { it.copy(dueDate = value) }
    fun updateDueTime(value: String) = draft.update { it.copy(dueTime = value) }
    fun updateReminder(value: String) = draft.update { it.copy(reminderMinutesBefore = value) }
    fun updateAssignmentType(value: TaskAssignmentType) = draft.update { it.copy(assignmentType = value, personIds = emptySet(), groupId = null) }
    fun togglePerson(personId: String) = draft.update {
        it.copy(personIds = toggleTaskPerson(it.assignmentType, it.personIds, personId))
    }
    fun selectGroup(groupId: String) = draft.update { it.copy(groupId = groupId) }

    fun saveTask(onSaved: () -> Unit = {}) = viewModelScope.launch {
        val current = draft.value
        if (Normalizer.normalizeText(current.title).isBlank()) return@launch
        val dueDate = TaskInputFormatters.parseDate(current.dueDate)?.toEpochDay() ?: return@launch
        if (current.taskId == null && dueDate < LocalDate.now().toEpochDay()) return@launch
        val dueTime = current.dueTime.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalTime.parse(it).let { time -> time.hour * 60 + time.minute } }.getOrNull()
        }
        val reminder = current.reminderMinutesBefore.toIntOrNull()?.takeIf { it >= 0 }
        val saved = taskRepository.upsertTask(
            taskId = current.taskId,
            title = current.title,
            notes = current.notes,
            assignee = current.assignee,
            // New tasks always enter the pending list. Existing completion state is preserved on edit.
            // Editing a completed task creates a new pending version of that task.
            completed = false,
            dueDate = dueDate,
            dueTimeMinutes = dueTime,
            reminderMinutesBefore = reminder,
            assignmentType = current.assignmentType,
            personIds = current.personIds,
            groupId = current.groupId,
        )
        val scheduledTask = saved
        if (TaskNotificationPolicy.isRelevant(scheduledTask, household.value)) notificationScheduler.schedule(scheduledTask)
        else notificationScheduler.cancel(saved.id)
        draft.value = TaskDraft()
        onSaved()
    }

    fun toggleCompleted(
        task: TaskItem,
        completedByPersonId: String? = null,
        completedByGroupId: String? = null,
    ) = viewModelScope.launch {
        val updated = taskRepository.toggleCompleted(
            taskId = task.id,
            completed = !task.completed,
            completedByPersonId = completedByPersonId,
            completedByGroupId = completedByGroupId,
        )
        if (updated?.completed == true) notificationScheduler.cancel(task.id)
        else if (updated != null && TaskNotificationPolicy.isRelevant(updated, household.value)) notificationScheduler.schedule(updated)
    }

    fun deleteTask(task: TaskItem) = viewModelScope.launch {
        taskRepository.deleteTask(task.id)
        notificationScheduler.cancel(task.id)
    }

    fun permanentlyDeleteTask(task: TaskItem) = viewModelScope.launch {
        taskRepository.permanentlyDeleteTask(task.id)
    }

    fun restoreTask(task: TaskItem) = viewModelScope.launch {
        val restored = taskRepository.restoreTask(task.id)
        if (restored != null && TaskNotificationPolicy.isRelevant(restored, household.value)) notificationScheduler.schedule(restored)
        else if (restored != null) notificationScheduler.cancel(restored.id)
    }

    fun archiveCompleted() = viewModelScope.launch {
        taskRepository.archiveCompleted()
    }

}
