package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.GroupMemberEntity
import com.majyd.casa.data.local.entities.HouseholdGroupEntity
import com.majyd.casa.data.local.entities.PersonEntity
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.data.repository.TaskRepository
import com.majyd.casa.notifications.NotificationScheduler
import com.majyd.casa.notifications.TaskNotificationPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class HouseholdSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
    private val householdRepository: HouseholdRepository,
    private val taskRepository: TaskRepository,
    private val notificationScheduler: NotificationScheduler,
) {
    private val resolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        // Idempotent replays must still be acknowledged by the transport.
        if (operationRepository.get(operation.operationId) != null) return true
        val accepted = database.withTransaction {
            when (operation.entityType) {
                "person" -> applyPerson(operation)
                "group" -> applyGroup(operation)
                else -> false
            }.also { value ->
                if (value) operationRepository.record(operation.copy(applied = true))
            }
        }
        if (accepted && operation.entityType == "group") refreshGroupNotifications(operation.entityId)
        return accepted
    }

    private suspend fun refreshGroupNotifications(groupId: String) {
        val state = householdRepository.observeState().first()
        taskRepository.observeTasks().first()
            .filter { it.assignmentType == com.majyd.casa.data.model.TaskAssignmentType.GROUP && it.groupId == groupId }
            .forEach { task ->
                if (task.deleted || task.completed || !TaskNotificationPolicy.isRelevant(task, state)) {
                    notificationScheduler.cancel(task.id)
                } else {
                    notificationScheduler.schedule(task)
                }
            }
    }

    private suspend fun applyPerson(operation: SyncOperation): Boolean {
        val payload = HouseholdSyncPayloadCodec.decodePerson(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.householdDao()
        val local = dao.getPerson(payload.id)
        if (local == null || resolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertPerson(PersonEntity(payload.id, payload.householdId, payload.name, com.majyd.casa.util.Normalizer.normalizeText(payload.name), payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice.ifBlank { operation.deviceId }))
        }
        return true
    }

    private suspend fun applyGroup(operation: SyncOperation): Boolean {
        val payload = HouseholdSyncPayloadCodec.decodeGroup(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.householdDao()
        val local = dao.getGroup(payload.id)
        if (local == null || resolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertGroup(HouseholdGroupEntity(payload.id, payload.householdId, payload.name, com.majyd.casa.util.Normalizer.normalizeText(payload.name), payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice.ifBlank { operation.deviceId }))
            dao.clearMembers(payload.id)
            payload.memberIds.forEach { dao.upsertMember(GroupMemberEntity(payload.id, it, payload.updatedAt)) }
        }
        return true
    }
}
