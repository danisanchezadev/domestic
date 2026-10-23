package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.MenuTemplateEntity
import com.majyd.casa.data.local.entities.MenuTemplateItemEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuTemplateSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        if (operationRepository.get(operation.operationId) != null) return true
        return database.withTransaction {
            val accepted = when (operation.entityType) {
                "menu_template" -> applyTemplate(operation)
                "menu_template_item" -> applyItem(operation)
                else -> false
            }
            if (accepted) operationRepository.record(operation.copy(applied = true))
            accepted
        }
    }

    private suspend fun applyTemplate(operation: SyncOperation): Boolean {
        val payload = MenuTemplateSyncPayloadCodec.decodeTemplate(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.menuTemplateDao()
        val local = dao.getById(payload.id)
        if (local == null || conflictResolver.incomingWins(
                local.version,
                local.updatedAt,
                local.updatedByDevice,
                payload.version,
                payload.updatedAt,
                payload.updatedByDevice.ifBlank { operation.deviceId },
            )
        ) {
            dao.upsertTemplate(
                MenuTemplateEntity(
                    id = payload.id,
                    householdId = payload.householdId,
                    name = payload.name,
                    normalizedName = payload.normalizedName,
                    deleted = payload.deleted,
                    createdAt = payload.createdAt,
                    updatedAt = payload.updatedAt,
                    version = payload.version,
                    updatedByDevice = payload.updatedByDevice.ifBlank { operation.deviceId },
                ),
            )
        }
        return true
    }

    private suspend fun applyItem(operation: SyncOperation): Boolean {
        val payload = MenuTemplateSyncPayloadCodec.decodeItem(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.menuTemplateDao()
        val local = dao.getItem(payload.templateId, payload.dayOfWeek, payload.mealSlot)
        if (local == null || conflictResolver.incomingWins(
                local.version,
                local.updatedAt,
                local.updatedByDevice,
                payload.version,
                payload.updatedAt,
                payload.updatedByDevice.ifBlank { operation.deviceId },
            )
        ) {
            dao.upsertItem(
                MenuTemplateItemEntity(
                    templateId = payload.templateId,
                    dayOfWeek = payload.dayOfWeek,
                    mealSlot = payload.mealSlot,
                    dishId = payload.dishId,
                    variantId = payload.variantId,
                    title = payload.title,
                    notes = payload.notes,
                    deleted = payload.deleted,
                    updatedAt = payload.updatedAt,
                    version = payload.version,
                    updatedByDevice = payload.updatedByDevice.ifBlank { operation.deviceId },
                ),
            )
        }
        return true
    }
}
