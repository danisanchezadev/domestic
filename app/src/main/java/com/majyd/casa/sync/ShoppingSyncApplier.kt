package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.ShoppingListEntity
import com.majyd.casa.data.local.entities.ShoppingProductEntity
import com.majyd.casa.data.local.entities.ShoppingCatalogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        // A previously recorded operation is already applied; acknowledge it to stop retries.
        if (operationRepository.get(operation.operationId) != null) return true
        return database.withTransaction {
            val accepted = when (operation.entityType) {
                "shopping_list" -> applyList(operation)
                "shopping_product" -> applyProduct(operation)
                "shopping_catalog" -> applyCatalog(operation)
                else -> false
            }
            if (accepted) operationRepository.record(operation.copy(applied = true))
            accepted
        }
    }

    private suspend fun applyList(operation: SyncOperation): Boolean {
        val payload = ShoppingSyncPayloadCodec.decodeList(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.shoppingListDao()
        val local = dao.getById(payload.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsert(ShoppingListEntity(payload.id, payload.name, payload.sortOrder, payload.deleted, payload.version, payload.createdAt, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId }))
        }
        return true
    }

    private suspend fun applyProduct(operation: SyncOperation): Boolean {
        val payload = ShoppingSyncPayloadCodec.decodeProduct(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.shoppingProductDao()
        if (database.shoppingListDao().getById(payload.listId) == null) return false
        val local = dao.getById(payload.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsert(ShoppingProductEntity(payload.id, payload.listId, payload.name, payload.normalizedName, payload.categoryId, payload.quantity, payload.unit, payload.notes, payload.purchased, payload.purchasedAt, payload.createdAt, payload.updatedAt, payload.createdByDevice, payload.updatedByDevice.ifBlank { operation.deviceId }, payload.deleted, payload.version, payload.imageBase64 ?: local?.imageBase64))
        }
        val catalogDao = database.shoppingCatalogDao()
        if (catalogDao.getByNameIncludingDeleted(payload.normalizedName) == null) {
            catalogDao.upsert(
                ShoppingCatalogEntity(
                    id = payload.normalizedName,
                    normalizedName = payload.normalizedName,
                    displayName = payload.name,
                    categoryId = payload.categoryId,
                    preferredUnit = payload.unit,
                    preferredQuantity = payload.quantity,
                    usageCount = 1,
                    lastUsedAt = payload.updatedAt,
                    createdAt = payload.createdAt,
                    updatedAt = payload.updatedAt,
                    deleted = false,
                    updatedByDevice = payload.updatedByDevice.ifBlank { operation.deviceId },
                )
            )
        }
        return true
    }

    private suspend fun applyCatalog(operation: SyncOperation): Boolean {
        val payload = ShoppingSyncPayloadCodec.decodeCatalog(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.shoppingCatalogDao()
        val local = dao.getByNameIncludingDeleted(payload.normalizedName)
        if (local == null || conflictResolver.incomingWins(local.updatedAt, local.updatedAt, local.updatedByDevice, payload.updatedAt, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsert(
                ShoppingCatalogEntity(
                    id = local?.id ?: payload.id,
                    normalizedName = payload.normalizedName,
                    displayName = payload.displayName,
                    categoryId = payload.categoryId,
                    preferredUnit = payload.preferredUnit,
                    preferredQuantity = payload.preferredQuantity,
                    usageCount = payload.usageCount,
                    lastUsedAt = payload.lastUsedAt,
                    createdAt = payload.createdAt,
                    updatedAt = payload.updatedAt,
                    deleted = payload.deleted,
                    updatedByDevice = payload.updatedByDevice.ifBlank { operation.deviceId },
                )
            )
        }
        return true
    }
}
