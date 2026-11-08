package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.EconomyCategoryEntity
import com.majyd.casa.data.local.entities.EconomyMovementEntity
import com.majyd.casa.data.local.entities.EconomyMonthlyTargetEntity
import com.majyd.casa.data.local.entities.EconomySettingsEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EconomySyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        if (operationRepository.get(operation.operationId) != null) return true
        val accepted = database.withTransaction {
            when (operation.entityType) {
                "economy_category" -> applyCategory(operation)
                "economy_movement" -> applyMovement(operation)
                "economy_target" -> applyTarget(operation)
                "economy_settings" -> applySettings(operation)
                else -> false
            }
        }
        if (accepted) operationRepository.record(operation.copy(applied = true))
        return accepted
    }

    private suspend fun applyCategory(operation: SyncOperation): Boolean {
        val p = EconomySyncPayloadCodec.decodeCategory(operation.payload) ?: return false
        if (p.id != operation.entityId || p.householdId != operation.householdId) return false
        val dao = database.economyDao()
        val local = dao.getCategory(p.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, p.version, p.updatedAt, p.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertCategory(EconomyCategoryEntity(p.id, p.householdId, p.type, p.name, p.normalizedName, p.deleted, p.createdAt, p.updatedAt, p.version, p.updatedByDevice.ifBlank { operation.deviceId }))
        }
        return true
    }

    private suspend fun applyMovement(operation: SyncOperation): Boolean {
        val p = EconomySyncPayloadCodec.decodeMovement(operation.payload) ?: return false
        if (p.id != operation.entityId || p.householdId != operation.householdId) return false
        val dao = database.economyDao()
        val local = dao.getMovement(p.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, p.version, p.updatedAt, p.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertMovement(EconomyMovementEntity(p.id, p.householdId, p.type, p.amountCents, p.categoryId, p.dateEpochDay, p.deleted, p.createdAt, p.updatedAt, p.version, p.updatedByDevice.ifBlank { operation.deviceId }))
        }
        return true
    }

    private suspend fun applyTarget(operation: SyncOperation): Boolean {
        val p = EconomySyncPayloadCodec.decodeTarget(operation.payload) ?: return false
        if ("${p.householdId}:${p.monthKey}" != operation.entityId || p.householdId != operation.householdId) return false
        val dao = database.economyDao()
        val local = dao.getTarget(p.householdId, p.monthKey)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, p.version, p.updatedAt, p.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertTarget(EconomyMonthlyTargetEntity(p.householdId, p.monthKey, p.targetCents, p.minimumCents, p.createdAt, p.updatedAt, p.version, p.updatedByDevice.ifBlank { operation.deviceId }))
        }
        return true
    }

    private suspend fun applySettings(operation: SyncOperation): Boolean {
        val p = EconomySyncPayloadCodec.decodeSettings(operation.payload) ?: return false
        if (p.householdId != operation.householdId || operation.entityId != p.householdId) return false
        val dao = database.economyDao()
        val local = dao.getSettings(p.householdId)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, p.version, p.updatedAt, p.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertSettings(EconomySettingsEntity(p.householdId, p.fiscalStartMonth.coerceIn(1, 12), p.currency, p.updatedAt, p.version, p.updatedByDevice.ifBlank { operation.deviceId }))
        }
        return true
    }
}
