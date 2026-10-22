package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.TaskEntity
import com.majyd.casa.data.local.entities.TaskGroupAssignmentEntity
import com.majyd.casa.data.local.entities.TaskPersonAssignmentEntity
import com.majyd.casa.data.model.TaskAssignee
import com.majyd.casa.data.model.TaskAssignmentType
import com.majyd.casa.data.model.TaskItem
import com.majyd.casa.data.model.TaskRecurrence
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.data.model.normalizeTaskAssignment
import com.majyd.casa.notifications.NotificationScheduler
import com.majyd.casa.notifications.TaskNotificationPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class TaskSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
    private val householdRepository: HouseholdRepository,
    private val notificationScheduler: NotificationScheduler,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        // Idempotent replays must still be acknowledged by the transport.
        if (operationRepository.get(operation.operationId) != null) return true
        val payload = SyncTaskPayloadCodec.decode(operation.payload) ?: return false
        if (operation.entityType != "task" || payload.id != operation.entityId) return false
        if (operation.operationType == "PURGE") {
            database.withTransaction {
                database.taskDao().clearPersonAssignments(payload.id)
                database.taskDao().clearGroupAssignments(payload.id)
                database.taskDao().permanentlyDelete(payload.id)
            }
            operationRepository.record(operation.copy(applied = true))
            notificationScheduler.cancel(payload.id)
            return true
        }
        val normalizedAssignment = normalizeTaskAssignment(
            runCatching { TaskAssignmentType.valueOf(payload.assignmentType) }.getOrDefault(TaskAssignmentType.NONE),
            payload.personIds.toSet(),
            payload.groupId,
        )
        val applied = database.withTransaction {
            val dao = database.taskDao()
            val local = dao.getById(payload.id)
            if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDeviceId.orEmpty(), payload.version, payload.updatedAt, operation.deviceId)) {
                dao.upsert(
                    TaskEntity(
                    id = payload.id,
                    title = payload.title,
                    normalizedTitle = payload.normalizedTitle,
                    notes = payload.notes,
                    assignee = legacyAssignee(payload.assignmentType),
                    completed = payload.completed,
                    deleted = payload.deleted,
                    createdAt = payload.createdAt,
                    updatedAt = payload.updatedAt,
                    version = payload.version,
                    dueDate = payload.dueDate,
                    dueTimeMinutes = payload.dueTimeMinutes,
                    reminderMinutesBefore = payload.reminderMinutesBefore,
                    completedAt = payload.completedAt,
                    completedByPersonId = payload.completedByPersonId,
                    completedByGroupId = payload.completedByGroupId,
                    updatedByDeviceId = operation.deviceId,
                    assignmentType = normalizedAssignment.type.name,
                    recurrence = payload.recurrence,
                    recurrenceSeriesId = payload.recurrenceSeriesId,
                    )
                )
                dao.clearPersonAssignments(payload.id)
                dao.clearGroupAssignments(payload.id)
                normalizedAssignment.personIds.forEach { dao.upsertPersonAssignment(TaskPersonAssignmentEntity(payload.id, it, payload.updatedAt)) }
                normalizedAssignment.groupId?.let { dao.upsertGroupAssignment(TaskGroupAssignmentEntity(payload.id, it, payload.updatedAt)) }
                true
            } else false
        }
        operationRepository.record(operation.copy(applied = true))
        if (applied) {
            val task = payload.toModel(normalizedAssignment)
            val household = householdRepository.observeState().first()
            if (task.deleted || task.completed || !TaskNotificationPolicy.isRelevant(task, household)) {
                notificationScheduler.cancel(task.id)
            } else {
                notificationScheduler.schedule(task)
            }
        }
        return true
    }

    private fun SyncTaskPayload.toModel(normalizedAssignment: com.majyd.casa.data.model.NormalizedTaskAssignment) = TaskItem(
        id = id,
        title = title,
        normalizedTitle = normalizedTitle,
        notes = notes,
        assignee = legacyAssignee(assignmentType).let(TaskAssignee::valueOf),
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
        assignmentType = normalizedAssignment.type,
        personIds = normalizedAssignment.personIds,
        groupId = normalizedAssignment.groupId,
        recurrence = runCatching { TaskRecurrence.valueOf(recurrence) }.getOrDefault(TaskRecurrence.NONE),
        recurrenceSeriesId = recurrenceSeriesId,
    )

    private fun legacyAssignee(type: String): String = when (type) {
        "EVERYONE", "MULTIPLE_PEOPLE" -> TaskAssignee.BOTH.name
        "PERSON" -> TaskAssignee.PERSON_ONE.name
        else -> TaskAssignee.UNASSIGNED.name
    }
}
