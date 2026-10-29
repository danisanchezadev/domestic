package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.DishEntity
import com.majyd.casa.data.local.entities.DishIngredientEntity
import com.majyd.casa.data.local.entities.DishVariantEntity
import com.majyd.casa.data.local.entities.RecipeCategoryEntity
import com.majyd.casa.data.local.entities.RecipeCategoryLinkEntity
import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.notifications.NotificationScheduler
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DishSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
    private val notificationScheduler: NotificationScheduler,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        if (operationRepository.get(operation.operationId) != null) return true
        return database.withTransaction {
            val accepted = when (operation.entityType) {
                "dish" -> applyDish(operation)
                "dish_ingredient" -> applyIngredient(operation)
                "dish_variant" -> applyVariant(operation)
                "recipe_category" -> applyCategory(operation)
                "recipe_category_link" -> applyCategoryLink(operation)
                else -> false
            }
            if (accepted) operationRepository.record(operation.copy(applied = true))
            accepted
        }
    }

    private suspend fun applyDish(operation: SyncOperation): Boolean {
        val payload = DishSyncPayloadCodec.decodeDish(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.dishDao()
        val local = dao.getById(payload.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            val entity = DishEntity(payload.id, payload.householdId, payload.name, payload.normalizedName, payload.category, payload.mealType, payload.imageBase64, payload.recipe, payload.recipeSteps, payload.preparationMinutes, payload.servings, payload.notes, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice.ifBlank { operation.deviceId }, payload.collection, payload.preparationRequired)
            dao.upsertDish(entity)
            val weekStart = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()).toEpochDay()
            database.weeklyMenuDao().getActiveByDish(entity.id).forEach { item ->
                if (entity.deleted || !entity.preparationRequired) {
                    notificationScheduler.cancelPreparation(item.id)
                } else {
                    notificationScheduler.schedulePreparation(
                        item.id,
                        entity.name,
                        LocalDate.ofEpochDay(weekStart).plusDays((item.dayOfWeek - 1).toLong()),
                        runCatching { MealSlot.valueOf(item.mealSlot) }.getOrDefault(MealSlot.LUNCH),
                    )
                }
            }
        }
        return true
    }

    private suspend fun applyIngredient(operation: SyncOperation): Boolean {
        val payload = DishSyncPayloadCodec.decodeIngredient(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.dishDao()
        if (dao.getById(payload.dishId) == null) return false
        val local = dao.getIngredients(payload.dishId).firstOrNull { it.id == payload.id }
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertIngredient(DishIngredientEntity(payload.id, payload.dishId, payload.name, payload.normalizedName, payload.quantity, payload.unit, payload.sortOrder, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice.ifBlank { operation.deviceId }, payload.variantId))
        }
        return true
    }

    private suspend fun applyVariant(operation: SyncOperation): Boolean {
        val payload = DishSyncPayloadCodec.decodeVariant(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.dishDao()
        if (dao.getById(payload.dishId) == null) return false
        val local = dao.getVariantById(payload.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertVariant(DishVariantEntity(payload.id, payload.dishId, payload.name, payload.normalizedName, payload.recipe, payload.notes, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice.ifBlank { operation.deviceId }))
        }
        return true
    }

    private suspend fun applyCategory(operation: SyncOperation): Boolean {
        val payload = DishSyncPayloadCodec.decodeCategory(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.recipeCategoryDao()
        val local = dao.getById(payload.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertCategory(RecipeCategoryEntity(payload.id, payload.householdId, payload.name, payload.normalizedName, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice.ifBlank { operation.deviceId }))
        }
        return true
    }

    private suspend fun applyCategoryLink(operation: SyncOperation): Boolean {
        val payload = DishSyncPayloadCodec.decodeCategoryLink(operation.payload) ?: return false
        if ("${payload.dishId}:${payload.categoryId}" != operation.entityId) return false
        val dao = database.recipeCategoryDao()
        val local = dao.getLinks(payload.dishId).firstOrNull { it.categoryId == payload.categoryId }
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, payload.updatedByDevice.ifBlank { operation.deviceId })) {
            dao.upsertLink(RecipeCategoryLinkEntity(payload.dishId, payload.categoryId, payload.deleted, payload.updatedAt, payload.version, payload.updatedByDevice.ifBlank { operation.deviceId }))
        }
        return true
    }
}
