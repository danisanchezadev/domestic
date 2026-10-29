package com.majyd.casa.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.TaskAssignee
import com.majyd.casa.data.model.TaskAssignmentType
import com.majyd.casa.data.model.TaskItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onBack: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(TaskScreen.SUMMARY) }
    val onToggleCompleted: (TaskItem, String?, String?) -> Unit = { task, personId, groupId ->
        viewModel.toggleCompleted(task, personId, groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = { Text("Tareas") },
                actions = {
                    TextButton(onClick = viewModel::archiveCompleted) {
                        Text("Archivar completadas")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (screen == TaskScreen.SUMMARY) {
                item {
                    TaskOverview(
                        state = state,
                        onSelect = { filter ->
                            viewModel.selectFilter(filter)
                            screen = TaskScreen.LIST
                        },
                        onPersonSelect = { personId ->
                            viewModel.selectPersonFilter(personId)
                            screen = TaskScreen.LIST
                        },
                        onGroupSelect = { groupId ->
                            viewModel.selectGroupFilter(groupId)
                            screen = TaskScreen.LIST
                        },
                    )
                }
                item {
                    Button(
                        onClick = {
                            viewModel.clearDraft()
                            screen = TaskScreen.FORM
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(),
                    ) { Text("Añadir tarea") }
                }
                item {
                    Button(
                        onClick = {
                            viewModel.selectFilter(TaskFilter.ALL)
                            screen = TaskScreen.LIST
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(),
                    ) { Text("Tareas pendientes") }
                }
            } else if (screen == TaskScreen.FORM) {
                item {
                    Button(onClick = { screen = TaskScreen.SUMMARY }, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver al resumen")
                    }
                }
                item {
                    TaskDraftCard(
                        draft = state.draft,
                        onTitleChange = viewModel::updateTitle,
                        suggestions = state.suggestions,
                        onSuggestionSelected = viewModel::selectSuggestion,
                        onNotesChange = viewModel::updateNotes,
                        onDueDateChange = viewModel::updateDueDate,
                        onDueTimeChange = viewModel::updateDueTime,
                        onReminderChange = viewModel::updateReminder,
                        onAssignmentTypeChange = viewModel::updateAssignmentType,
                        onTogglePerson = viewModel::togglePerson,
                        onGroupSelect = viewModel::selectGroup,
                        household = state.household,
                        onSave = { viewModel.saveTask { screen = TaskScreen.SUMMARY } },
                        onClear = viewModel::clearDraft,
                    )
                }
            } else {
                item {
                    Button(onClick = { screen = TaskScreen.SUMMARY }, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver al resumen")
                    }
                }
                item {
                    TaskSection("Vencidas", state.overdue, onToggleCompleted, { task -> viewModel.editTask(task); screen = TaskScreen.FORM }, viewModel::deleteTask, state.household, false, onRestore = null)
                }
                item {
                    TaskSection("Hoy", state.today, onToggleCompleted, { task -> viewModel.editTask(task); screen = TaskScreen.FORM }, viewModel::deleteTask, state.household, false, onRestore = null)
                }
                item {
                    TaskSection("Próximas", state.upcoming, onToggleCompleted, { task -> viewModel.editTask(task); screen = TaskScreen.FORM }, viewModel::deleteTask, state.household, false, onRestore = null)
                }
                item {
                    TaskSection("Completadas", state.completed, onToggleCompleted, { task -> viewModel.editTask(task); screen = TaskScreen.FORM }, viewModel::deleteTask, state.household, false, onRestore = null)
                }
                item {
                    TaskSection("Papelera", state.deleted, onToggleCompleted, { task -> viewModel.editTask(task); screen = TaskScreen.FORM }, viewModel::permanentlyDeleteTask, state.household, true, onRestore = viewModel::restoreTask)
                }
            }
        }
    }
}

private enum class TaskScreen { SUMMARY, FORM, LIST }

@Composable
private fun TaskOverview(
    state: TasksUiState,
    onSelect: (TaskFilter) -> Unit,
    onPersonSelect: (String) -> Unit,
    onGroupSelect: (String) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resumen de tareas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskOverviewButton(
                    label = "Mis tareas",
                    count = state.minePending,
                    selected = state.filter == TaskFilter.MINE,
                    onClick = { onSelect(TaskFilter.MINE) },
                )
                if (state.household.assignedPersonId == null) {
                    Text(
                        "Asocia este dispositivo a una persona desde Ajustes para personalizar este bloque.",
                        modifier = Modifier.fillMaxWidth(0.47f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.household.people
                    .filterNot { it.id == state.household.assignedPersonId }
                    .forEach { person ->
                        TaskOverviewButton(
                            label = person.name,
                            count = state.personPendingCounts[person.id] ?: 0,
                            selected = state.filter == TaskFilter.PERSON && state.selectedPersonId == person.id,
                            onClick = { onPersonSelect(person.id) },
                        )
                    }
                TaskOverviewButton("Sin dueño", state.unassignedPending, state.filter == TaskFilter.UNASSIGNED) { onSelect(TaskFilter.UNASSIGNED) }
                TaskOverviewButton("Ver todas", state.totalPending, selected = false) { onSelect(TaskFilter.ALL) }
            }
            Text("Grupos", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.household.groups.forEach { group ->
                        TaskOverviewButton(
                            label = group.name.take(MAX_GROUP_NAME_LENGTH),
                            count = state.groupPendingCounts[group.id] ?: 0,
                            selected = state.filter == TaskFilter.GROUP && state.selectedGroupId == group.id,
                            onClick = { onGroupSelect(group.id) },
                        )
                    }
                    TaskOverviewButton("Todos", state.everyonePending, state.filter == TaskFilter.EVERYONE) { onSelect(TaskFilter.EVERYONE) }
            }
        }
    }
}

@Composable
private fun TaskOverviewButton(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(0.47f), colors = ButtonDefaults.buttonColors()) {
        Text("$label    $count")
    }
}

private const val MAX_GROUP_NAME_LENGTH = 9

@Composable
private fun TaskDraftCard(
    draft: TaskDraft,
    onTitleChange: (String) -> Unit,
    suggestions: List<com.majyd.casa.data.model.TaskSuggestion>,
    onSuggestionSelected: (com.majyd.casa.data.model.TaskSuggestion) -> Unit,
    onNotesChange: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onDueTimeChange: (String) -> Unit,
    onReminderChange: (String) -> Unit,
    onAssignmentTypeChange: (TaskAssignmentType) -> Unit,
    onTogglePerson: (String) -> Unit,
    onGroupSelect: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    household: com.majyd.casa.data.model.HouseholdState = com.majyd.casa.data.model.HouseholdState(),
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nueva tarea", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Título") },
            )
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion ->
                        AssistChip(onClick = { onSuggestionSelected(suggestion) }, label = { Text(suggestion.title) })
                    }
                }
            }
            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notas") },
            )
            Text("Fecha límite *", style = MaterialTheme.typography.titleSmall)
            TaskDateWheelPicker(
                value = draft.dueDate,
                onValueChange = onDueDateChange,
                allowPast = draft.taskId != null,
            )
            Text(
                "Selecciona día, mes y año. Fecha elegida: ${draft.dueDate}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Hora límite", style = MaterialTheme.typography.titleSmall)
            TaskTimeWheelPicker(value = draft.dueTime, onValueChange = onDueTimeChange)
            Text(
                "Selecciona hora y minutos. Hora elegida: ${draft.dueTime}",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = draft.reminderMinutesBefore,
                onValueChange = onReminderChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Aviso minutos antes") },
                supportingText = { Text("Por defecto: 60 minutos. Puedes borrarlo para no recibir aviso.") },
            )
            Text("Asignar a", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskAssignmentType.entries.forEach { assignment ->
                    FilterChip(
                        selected = draft.assignmentType == assignment,
                        onClick = { onAssignmentTypeChange(assignment) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        label = { Text(assignmentLabel(assignment)) },
                    )
                }
            }
            if (draft.assignmentType == TaskAssignmentType.PERSON || draft.assignmentType == TaskAssignmentType.MULTIPLE_PEOPLE) {
                household.people.forEach { person ->
                    FilterChip(
                        selected = draft.personIds.contains(person.id),
                        onClick = { onTogglePerson(person.id) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        label = { Text(person.name) },
                    )
                }
            }
            if (draft.assignmentType == TaskAssignmentType.GROUP) {
                household.groups.forEach { group ->
                    FilterChip(
                        selected = draft.groupId == group.id,
                        onClick = { onGroupSelect(group.id) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        label = { Text(group.name) },
                    )
                }
                if (household.groups.isEmpty()) Text("No hay grupos configurados")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, colors = ButtonDefaults.buttonColors()) {
                    Text("Guardar")
                }
                Button(onClick = onClear, colors = ButtonDefaults.buttonColors()) {
                    Text("Limpiar")
                }
            }
        }
    }
}

private fun assignmentLabel(type: TaskAssignmentType): String = when (type) {
    TaskAssignmentType.NONE -> "Sin dueño"
    TaskAssignmentType.PERSON -> "Una persona"
    TaskAssignmentType.MULTIPLE_PEOPLE -> "Varias personas"
    TaskAssignmentType.GROUP -> "Grupo"
    TaskAssignmentType.EVERYONE -> "Todos"
}

@Composable
private fun TaskDateWheelPicker(
    value: String,
    onValueChange: (String) -> Unit,
    allowPast: Boolean,
) {
    val today = LocalDate.now()
    val parsedDate = TaskInputFormatters.parseDate(value) ?: today
    val selectedDate = if (!allowPast && parsedDate.isBefore(today)) today else parsedDate
    val yearRange = if (allowPast) 2020..2100 else today.year..2100
    val monthRange = if (!allowPast && selectedDate.year == today.year) today.monthValue..12 else 1..12
    val dayRange = if (!allowPast && selectedDate.year == today.year && selectedDate.monthValue == today.monthValue) {
        today.dayOfMonth..selectedDate.lengthOfMonth()
    } else {
        1..selectedDate.lengthOfMonth()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateWheel(
            label = "Día",
            range = dayRange,
            selected = selectedDate.dayOfMonth.coerceIn(dayRange.first, dayRange.last),
            onSelected = { day ->
                onValueChange(TaskInputFormatters.formatDate(LocalDate.of(selectedDate.year, selectedDate.monthValue, day)))
            },
        )
        DateWheel(
            label = "Mes",
            range = monthRange,
            selected = selectedDate.monthValue.coerceIn(monthRange.first, monthRange.last),
            onSelected = { month ->
                val minimumDay = if (!allowPast && selectedDate.year == today.year && month == today.monthValue) today.dayOfMonth else 1
                val day = minOf(selectedDate.dayOfMonth.coerceAtLeast(minimumDay), LocalDate.of(selectedDate.year, month, 1).lengthOfMonth())
                onValueChange(TaskInputFormatters.formatDate(LocalDate.of(selectedDate.year, month, day)))
            },
        )
        DateWheel(
            label = "Año",
            range = yearRange,
            selected = selectedDate.year.coerceIn(yearRange.first, yearRange.last),
            onSelected = { year ->
                val month = if (!allowPast && year == today.year) selectedDate.monthValue.coerceAtLeast(today.monthValue) else selectedDate.monthValue
                val minimumDay = if (!allowPast && year == today.year && month == today.monthValue) today.dayOfMonth else 1
                val day = minOf(selectedDate.dayOfMonth.coerceAtLeast(minimumDay), LocalDate.of(year, month, 1).lengthOfMonth())
                onValueChange(TaskInputFormatters.formatDate(LocalDate.of(year, month, day)))
            },
        )
    }
}

@Composable
private fun DateWheel(
    label: String,
    range: IntRange,
    selected: Int,
    wrap: Boolean = false,
    paddedValues: Boolean = false,
    onSelected: (Int) -> Unit,
) {
    val latestOnSelected = rememberUpdatedState(onSelected)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        AndroidView(
            modifier = Modifier.height(120.dp),
            factory = { context ->
                android.widget.NumberPicker(context).apply {
                    minValue = range.first
                    maxValue = range.last
                    displayedValues = if (paddedValues) {
                        (range.first..range.last).map { "%02d".format(it) }.toTypedArray()
                    } else {
                        null
                    }
                    this.value = selected.coerceIn(range.first, range.last)
                    wrapSelectorWheel = wrap
                    setOnValueChangedListener { _, _, newValue -> latestOnSelected.value(newValue) }
                }
            },
            update = { picker ->
                picker.minValue = range.first
                picker.maxValue = range.last
                picker.displayedValues = if (paddedValues) {
                    (range.first..range.last).map { "%02d".format(it) }.toTypedArray()
                } else {
                    null
                }
                picker.wrapSelectorWheel = wrap
                val safeSelected = selected.coerceIn(range.first, range.last)
                if (picker.value != safeSelected) picker.value = safeSelected
            },
        )
    }
}

@Composable
private fun TaskTimeWheelPicker(value: String, onValueChange: (String) -> Unit) {
    val selectedTime = runCatching { LocalTime.parse(value) }.getOrElse { LocalTime.of(23, 59) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateWheel(
            label = "Hora",
            range = 0..23,
            selected = selectedTime.hour,
            wrap = true,
            paddedValues = true,
            onSelected = { hour -> onValueChange("%02d:%02d".format(hour, selectedTime.minute)) },
        )
        Text(":", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))
        DateWheel(
            label = "Minutos",
            range = 0..59,
            selected = selectedTime.minute,
            wrap = true,
            paddedValues = true,
            onSelected = { minute -> onValueChange("%02d:%02d".format(selectedTime.hour, minute)) },
        )
    }
}

@Composable
private fun TaskSection(
    title: String,
    tasks: List<TaskItem>,
    onToggleCompleted: (TaskItem, String?, String?) -> Unit,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
    household: com.majyd.casa.data.model.HouseholdState,
    restoreMode: Boolean,
    onRestore: ((TaskItem) -> Unit)? = null,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth(0.85f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(sectionSummary(title, tasks.size), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (expanded) "▲" else "▼")
                }
            }
            if (expanded && tasks.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                tasks.forEach { task ->
                    TaskRow(
                        task = task,
                        onToggleCompleted = onToggleCompleted,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        household = household,
                        restoreMode = restoreMode,
                        onRestore = onRestore,
                    )
                }
                }
            }
        }
    }
}

private fun sectionSummary(title: String, count: Int): String = when {
    count == 0 -> "Sin tareas"
    title == "Completadas" -> "$count tareas completadas"
    title == "Papelera" -> "$count tareas eliminadas"
    else -> "$count tareas pendientes"
}

@Composable
private fun TaskRow(
    task: TaskItem,
    onToggleCompleted: (TaskItem, String?, String?) -> Unit,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
    household: com.majyd.casa.data.model.HouseholdState,
    restoreMode: Boolean,
    onRestore: ((TaskItem) -> Unit)?,
) {
    var completionMenuExpanded by rememberSaveable(task.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(task.title, modifier = Modifier.fillMaxWidth(0.8f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (task.notes.isNotBlank()) {
                Text(task.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Text(assignmentSummary(task, household), style = MaterialTheme.typography.bodySmall)
            task.completedByPersonId?.let { personId ->
                household.people.firstOrNull { it.id == personId }?.let { person ->
                    Text("Completada por ${person.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
            task.completedByGroupId?.let { groupId ->
                household.groups.firstOrNull { it.id == groupId }?.let { group ->
                    Text("Completada por ${group.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Vence: ${formatDueDate(task)}", style = MaterialTheme.typography.bodySmall)
            if (!task.completed && isOverdue(task)) {
                Text("Vencida", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            task.reminderMinutesBefore?.let { Text("Aviso: $it min antes", style = MaterialTheme.typography.bodySmall) }
            if (restoreMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onRestore?.invoke(task) }) {
                        Icon(Icons.Filled.Restore, contentDescription = "Restaurar", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onDelete(task) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar definitivamente", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = task.completed,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                onToggleCompleted(task, null, null)
                            } else if (task.assignmentType == TaskAssignmentType.NONE) {
                                completionMenuExpanded = true
                            } else {
                                onToggleCompleted(task, null, null)
                            }
                        },
                    )
                    Text(if (task.completed) "Marcar como pendiente" else "Marcar como completada")
                }
                if (!task.completed && task.assignmentType == TaskAssignmentType.NONE) {
                    Box {
                        Button(onClick = { completionMenuExpanded = true }) {
                            Text("Indicar quién la ha completado")
                        }
                        DropdownMenu(
                            expanded = completionMenuExpanded,
                            onDismissRequest = { completionMenuExpanded = false },
                        ) {
                            household.people.forEach { person ->
                                DropdownMenuItem(
                                    text = { Text(person.name) },
                                    onClick = {
                                        completionMenuExpanded = false
                                        onToggleCompleted(task, person.id, null)
                                    },
                                )
                            }
                            household.groups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text("Grupo: ${group.name}") },
                                    onClick = {
                                        completionMenuExpanded = false
                                        onToggleCompleted(task, null, group.id)
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onEdit(task) }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onDelete(task) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun formatDueDate(task: TaskItem): String {
    if (task.dueDate <= 0L) return "Sin fecha (tarea antigua)"
    val date = TaskInputFormatters.formatDate(java.time.LocalDate.ofEpochDay(task.dueDate))
    val time = task.dueTimeMinutes?.let { " %02d:%02d".format(it / 60, it % 60) }.orEmpty()
    return date + time
}

private fun assignmentSummary(task: TaskItem, household: com.majyd.casa.data.model.HouseholdState): String {
    return when (task.assignmentType) {
        TaskAssignmentType.NONE -> "Sin dueño"
        TaskAssignmentType.EVERYONE -> "Asignada a todos"
        TaskAssignmentType.GROUP -> {
            val groupName = task.groupId
                ?.let { id -> household.groups.firstOrNull { it.id == id }?.name }
            "Grupo: ${groupName ?: "desconocido"}"
        }
        TaskAssignmentType.PERSON, TaskAssignmentType.MULTIPLE_PEOPLE -> {
            val names = task.personIds.mapNotNull { personId ->
                household.people.firstOrNull { it.id == personId }?.name
            }
            if (names.isNotEmpty()) "Asignada a: ${names.joinToString(", ")}" else task.assignee.label
        }
    }
}

private fun isOverdue(task: TaskItem): Boolean {
    if (task.dueDate <= 0L) return false
    val today = LocalDate.now()
    if (task.dueDate < today.toEpochDay()) return true
    if (task.dueDate > today.toEpochDay() || task.dueTimeMinutes == null) return false
    val due = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + task.dueTimeMinutes * 60_000L
    return due < System.currentTimeMillis()
}

