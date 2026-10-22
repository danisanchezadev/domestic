package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.WeeklyMenuItemEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyMenuSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        if (operationRepository.get(operation.operationId) != null) return true
        val payload = WeeklyMenuSyncPayloadCodec.decode(operation.payload) ?: return false
        if (operation.entityType != "weekly_menu_item" || payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        database.withTransaction {
            val dao = database.weeklyMenuDao()
            val local = dao.getById(payload.id)
            if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, operation.deviceId)) {
                dao.upsert(WeeklyMenuItemEntity(payload.id, payload.householdId, payload.dayOfWeek, payload.mealSlot, payload.title, payload.normalizedTitle, payload.notes, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice))
            }
        }
        operationRepository.record(operation.copy(applied = true))
        return true
    }
}
