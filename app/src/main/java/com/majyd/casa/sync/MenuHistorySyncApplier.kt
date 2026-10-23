package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.MenuHistoryItemEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuHistorySyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        if (operationRepository.get(operation.operationId) != null) return true
        val payload = MenuHistorySyncPayloadCodec.decode(operation.payload) ?: return false
        if (operation.entityType != "menu_history_item" || payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        database.withTransaction {
            val dao = database.menuHistoryDao()
            val local = dao.getById(payload.id)
            if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
                dao.upsert(MenuHistoryItemEntity(payload.id, payload.householdId, payload.weekStartEpochDay, payload.dayOfWeek, payload.mealSlot, payload.dishId, payload.variantId, payload.title, payload.notes, payload.completed, payload.completedAt, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice.ifBlank { operation.deviceId }))
            }
            operationRepository.record(operation.copy(applied = true))
        }
        return true
    }
}
