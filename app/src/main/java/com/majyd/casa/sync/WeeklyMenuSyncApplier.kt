package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.WeeklyMenuItemEntity
import com.majyd.casa.data.repository.ShoppingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyMenuSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
    private val shoppingRepository: ShoppingRepository,
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
                dao.upsert(WeeklyMenuItemEntity(payload.id, payload.householdId, payload.dayOfWeek, payload.mealSlot, payload.dishId, payload.variantId, payload.title, payload.normalizedTitle, payload.notes, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice))
            }
            operationRepository.record(operation.copy(applied = true))
        }
        if (!payload.deleted && payload.dishId != null) {
            // The menu assignment is also the trigger for the shopping ingredients on the peer device.
            val dish = database.dishDao().getById(payload.dishId)
            dish?.let {
                database.dishDao().getIngredients(it.id, payload.variantId)
                    .filterNot { ingredient -> ingredient.deleted }
                    .forEach { ingredient ->
                        if (payload.shoppingIngredientIds == null || ingredient.id in payload.shoppingIngredientIds) {
                            shoppingRepository.addIngredientToDefaultList(ingredient.name, ingredient.quantity ?: 1.0, ingredient.unit)
                        }
                    }
            }
        }
        return true
    }
}
