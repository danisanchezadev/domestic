package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.TaskDao
import com.majyd.casa.data.local.dao.TaskHistoryDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.TaskEntity
import com.majyd.casa.data.model.TaskAssignee
import com.majyd.casa.data.model.TaskItem
import com.majyd.casa.data.model.TaskAssignmentType
import com.majyd.casa.data.model.TaskRecurrence
import com.majyd.casa.data.model.TaskSuggestion
import com.majyd.casa.data.model.normalizeTaskAssignment
import com.majyd.casa.data.local.entities.TaskPersonAssignmentEntity
import com.majyd.casa.data.local.entities.TaskGroupAssignmentEntity
import com.majyd.casa.util.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.SyncTaskPayload
import com.majyd.casa.sync.SyncTaskPayloadCodec
import com.majyd.casa.data.local.entities.TaskHistoryEntity
import kotlinx.serialization.json.Json

@Singleton
class RoomTaskRepository @Inject constructor(
    private val database: CasaDatabase,
    private val taskDao: TaskDao,
    private val syncOperationRepository: SyncOperationRepository,
    private val deviceManager: DeviceManager,
    private val taskHistoryDao: TaskHistoryDao,
) : TaskRepository {

    override fun observeTasks(): Flow<List<TaskItem>> = combine(
        taskDao.observeTasks(),
        taskDao.observePersonAssignments(),
        taskDao.observeGroupAssignments(),
    ) { tasks, people, groups ->
        tasks.map { task ->
            task.toModel(
                personIds = people.filter { it.taskId == task.id }.map { it.personId }.toSet(),
                groupId = groups.firstOrNull { it.taskId == task.id }?.groupId,
            )
        }
    }

    override fun observeSuggestions(): Flow<List<TaskSuggestion>> = taskHistoryDao.observeAll().map { entries ->
        entries.mapNotNull { entry ->
            TaskSuggestion(
                title = entry.title,
                notes = entry.notes,
                assignmentType = runCatching { TaskAssignmentType.valueOf(entry.assignmentType) }.getOrDefault(TaskAssignmentType.NONE),
                personIds = runCatching { Json.decodeFromString<List<String>>(entry.personIdsJson).toSet() }.getOrDefault(emptySet()),
                groupId = entry.groupId,
                dueTimeMinutes = entry.dueTimeMinutes,
                reminderMinutesBefore = entry.reminderMinutesBefore,
                recurrence = runCatching { TaskRecurrence.valueOf(entry.recurrence) }.getOrDefault(TaskRecurrence.NONE),
                usageCount = entry.usageCount,
                lastUsedAt = entry.lastUsedAt,
            )
        }
    }

    override suspend fun ensureDefaults() {
        // Tasks are user data; do not seed demo records in production.
    }

    override suspend fun upsertTask(
        taskId: String?,
        title: String,
        notes: String,
        assignee: TaskAssignee,
        completed: Boolean,
        dueDate: Long,
        dueTimeMinutes: Int?,
        reminderMinutesBefore: Int?,
        assignmentType: TaskAssignmentType,
        personIds: Set<String>,
        groupId: String?,
        recurrence: TaskRecurrence,
        recurrenceSeriesId: String?,
    ): TaskItem {
        require(dueDate > 0L) { "La fecha límite es obligatoria" }
        return database.withTransaction {
            val current = taskId?.let { taskDao.getById(it) }
            val now = System.currentTimeMillis()
            val device = deviceManager.ensureDevice()
            val normalizedAssignment = normalizeTaskAssignment(assignmentType, personIds, groupId)
            val entity = TaskEntity(
                id = current?.id ?: UUID.randomUUID().toString(),
                title = title.trim(),
                normalizedTitle = Normalizer.normalizeText(title),
                notes = notes.trim(),
                assignee = assignee.name,
                completed = completed,
                deleted = false,
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                version = (current?.version ?: 0) + 1,
                dueDate = dueDate,
                dueTimeMinutes = dueTimeMinutes,
                reminderMinutesBefore = reminderMinutesBefore,
                completedAt = if (completed) current?.completedAt ?: now else null,
                completedByPersonId = if (completed) current?.completedByPersonId ?: device.assignedPersonId else null,
                completedByGroupId = if (completed) current?.completedByGroupId else null,
                createdByDeviceId = current?.createdByDeviceId ?: device.deviceId,
                updatedByDeviceId = device.deviceId,
                assignmentType = normalizedAssignment.type.name,
                recurrence = recurrence.name,
                recurrenceSeriesId = recurrenceSeriesId,
            )
            taskDao.upsert(entity)
            taskDao.clearPersonAssignments(entity.id)
            taskDao.clearGroupAssignments(entity.id)
            normalizedAssignment.personIds.forEach { personId -> taskDao.upsertPersonAssignment(TaskPersonAssignmentEntity(entity.id, personId, now)) }
            normalizedAssignment.groupId?.let { taskDao.upsertGroupAssignment(TaskGroupAssignmentEntity(entity.id, it, now)) }
            enqueueTask(entity, normalizedAssignment.personIds, normalizedAssignment.groupId, if (current == null) "CREATE" else "UPDATE")
            rememberTask(entity, normalizedAssignment.personIds, normalizedAssignment.groupId)
            entity.toModel(normalizedAssignment.personIds, normalizedAssignment.groupId)
        }
    }

    private suspend fun rememberTask(entity: TaskEntity, personIds: Set<String>, groupId: String?) {
        val existing = taskHistoryDao.getByNormalizedTitle(entity.normalizedTitle)
        taskHistoryDao.upsert(
            TaskHistoryEntity(
                normalizedTitle = entity.normalizedTitle,
                title = entity.title,
                notes = entity.notes,
                assignmentType = entity.assignmentType,
                personIdsJson = Json.encodeToString(personIds.toList()),
                groupId = groupId,
                dueTimeMinutes = entity.dueTimeMinutes,
                reminderMinutesBefore = entity.reminderMinutesBefore,
                recurrence = entity.recurrence,
                usageCount = (existing?.usageCount ?: 0) + 1,
                lastUsedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun toggleCompleted(
        taskId: String,
        completed: Boolean,
        completedByPersonId: String?,
        completedByGroupId: String?,
    ): TaskItem? {
        val current = taskDao.getById(taskId) ?: return null
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val device = deviceManager.ensureDevice()
            val updated = current.copy(
                completed = completed,
                completedAt = if (completed) now else null,
                completedByPersonId = if (completed) completedByPersonId ?: device.assignedPersonId else null,
                completedByGroupId = if (completed) completedByGroupId else null,
                updatedAt = now,
                version = current.version + 1,
            )
            taskDao.upsert(updated)
            enqueueTask(updated, taskDao.getPersonAssignments(taskId).map { it.personId }.toSet(), taskDao.getGroupAssignments(taskId).firstOrNull()?.groupId, if (completed) "COMPLETE" else "UPDATE")
            updated.toModel(
                personIds = taskDao.getPersonAssignments(taskId).map { it.personId }.toSet(),
                groupId = taskDao.getGroupAssignments(taskId).firstOrNull()?.groupId,
            )
        }
    }

    override suspend fun deleteTask(taskId: String): TaskItem? {
        val current = taskDao.getById(taskId) ?: return null
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val updated = current.copy(deleted = true, updatedAt = now, version = current.version + 1)
            taskDao.upsert(updated)
            enqueueTask(updated, taskDao.getPersonAssignments(taskId).map { it.personId }.toSet(), taskDao.getGroupAssignments(taskId).firstOrNull()?.groupId, "DELETE")
            updated.toModel(
                personIds = taskDao.getPersonAssignments(taskId).map { it.personId }.toSet(),
                groupId = taskDao.getGroupAssignments(taskId).firstOrNull()?.groupId,
            )
        }
    }

    override suspend fun permanentlyDeleteTask(taskId: String): TaskItem? {
        val current = taskDao.getById(taskId) ?: return null
        return database.withTransaction {
            val personIds = taskDao.getPersonAssignments(taskId).map { it.personId }.toSet()
            val groupId = taskDao.getGroupAssignments(taskId).firstOrNull()?.groupId
            enqueueTask(current, personIds, groupId, "PURGE")
            taskDao.clearPersonAssignments(taskId)
            taskDao.clearGroupAssignments(taskId)
            taskDao.permanentlyDelete(taskId)
            current.toModel(personIds, groupId)
        }
    }

    override suspend fun restoreTask(taskId: String): TaskItem? {
        val current = taskDao.getById(taskId) ?: return null
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val updated = current.copy(deleted = false, updatedAt = now, version = current.version + 1)
            taskDao.upsert(updated)
            enqueueTask(updated, taskDao.getPersonAssignments(taskId).map { it.personId }.toSet(), taskDao.getGroupAssignments(taskId).firstOrNull()?.groupId, "RESTORE")
            updated.toModel(
                personIds = taskDao.getPersonAssignments(taskId).map { it.personId }.toSet(),
                groupId = taskDao.getGroupAssignments(taskId).firstOrNull()?.groupId,
            )
        }
    }

    override suspend fun postponeTask(taskId: String, days: Long): TaskItem? {
        if (days <= 0L) return null
        val current = taskDao.getById(taskId) ?: return null
        return database.withTransaction {
            val personIds = taskDao.getPersonAssignments(taskId).map { it.personId }.toSet()
            val groupId = taskDao.getGroupAssignments(taskId).firstOrNull()?.groupId
            val updated = current.copy(
                dueDate = (if (current.dueDate > 0L) current.dueDate else LocalDate.now().toEpochDay()) + days,
                updatedAt = System.currentTimeMillis(),
                version = current.version + 1,
            )
            taskDao.upsert(updated)
            enqueueTask(updated, personIds, groupId, "UPDATE")
            updated.toModel(personIds, groupId)
        }
    }

    override suspend fun archiveCompleted() {
        database.withTransaction {
            val now = System.currentTimeMillis()
            taskDao.observeTasks().first().filter { it.completed && !it.deleted }.forEach { task ->
                val updated = task.copy(deleted = true, updatedAt = now, version = task.version + 1)
                taskDao.upsert(updated)
                enqueueTask(
                    updated,
                    taskDao.getPersonAssignments(task.id).map { it.personId }.toSet(),
                    taskDao.getGroupAssignments(task.id).firstOrNull()?.groupId,
                    "ARCHIVE",
                )
            }
        }
    }

    private fun TaskEntity.toModel(personIds: Set<String> = emptySet(), groupId: String? = null): TaskItem {
        val parsedAssignee = runCatching { TaskAssignee.valueOf(assignee ?: TaskAssignee.UNASSIGNED.name) }
            .getOrDefault(TaskAssignee.UNASSIGNED)
        val storedAssignment = runCatching { TaskAssignmentType.valueOf(assignmentType) }
            .getOrDefault(TaskAssignmentType.NONE)
        val resolvedAssignment = if (storedAssignment != TaskAssignmentType.NONE || parsedAssignee == TaskAssignee.UNASSIGNED) {
            storedAssignment
        } else {
            when (parsedAssignee) {
                TaskAssignee.BOTH -> TaskAssignmentType.MULTIPLE_PEOPLE
                else -> TaskAssignmentType.PERSON
            }
        }
        return TaskItem(
        id = id,
        title = title,
        normalizedTitle = normalizedTitle,
        notes = notes,
        assignee = parsedAssignee,
        completed = completed,
        deleted = deleted,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        dueDate = dueDate,
        dueTimeMinutes = dueTimeMinutes,
        reminderMinutesBefore = reminderMinutesBefore,
        completedAt = completedAt,
        completedByPersonId = completedByPersonId,
        completedByGroupId = completedByGroupId,
            assignmentType = resolvedAssignment,
            recurrence = runCatching { TaskRecurrence.valueOf(recurrence) }.getOrDefault(TaskRecurrence.NONE),
            recurrenceSeriesId = recurrenceSeriesId,
        personIds = personIds,
        groupId = groupId,
        )
    }

    private suspend fun enqueueTask(entity: TaskEntity, personIds: Set<String>, groupId: String?, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(
            householdId = device.householdId,
            deviceId = device.deviceId,
            entityType = "task",
            entityId = entity.id,
            payload = SyncTaskPayloadCodec.encode(
                SyncTaskPayload(
                    id = entity.id,
                    title = entity.title,
                    normalizedTitle = entity.normalizedTitle,
                    notes = entity.notes,
                    assignmentType = entity.assignmentType,
                    personIds = personIds.toList(),
                    groupId = groupId,
                    dueDate = entity.dueDate,
                    dueTimeMinutes = entity.dueTimeMinutes,
                    reminderMinutesBefore = entity.reminderMinutesBefore,
                    completed = entity.completed,
                    completedAt = entity.completedAt,
                    completedByPersonId = entity.completedByPersonId,
                    completedByGroupId = entity.completedByGroupId,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    version = entity.version,
                    deleted = entity.deleted,
                    recurrence = entity.recurrence,
                    recurrenceSeriesId = entity.recurrenceSeriesId,
                ),
            ),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = operationType,
        )
    }

    companion object {
    }
}
