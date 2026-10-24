package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.DishDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.DishEntity
import com.majyd.casa.data.local.entities.DishIngredientEntity
import com.majyd.casa.data.local.entities.DishVariantEntity
import com.majyd.casa.data.model.Dish
import com.majyd.casa.data.model.DishIngredient
import com.majyd.casa.data.model.DishMealType
import com.majyd.casa.data.model.DishVariant
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.DishIngredientSyncPayload
import com.majyd.casa.sync.DishVariantSyncPayload
import com.majyd.casa.sync.DishSyncPayload
import com.majyd.casa.sync.DishSyncPayloadCodec
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.util.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomDishRepository @Inject constructor(
    private val database: CasaDatabase,
    private val dishDao: DishDao,
    private val deviceManager: DeviceManager,
    private val syncOperationRepository: SyncOperationRepository,
) : DishRepository {
    override fun observeDishes(): Flow<List<Dish>> = dishDao.observeDishes().map { items -> items.map { it.toModel() } }

    override fun observeIngredients(dishId: String): Flow<List<DishIngredient>> =
        dishDao.observeIngredients(dishId).map { items -> items.map { it.toModel() } }

    override suspend fun getIngredients(dishId: String, variantId: String?): List<DishIngredient> =
        dishDao.getIngredients(dishId, variantId).map { it.toModel() }

    override fun observeVariants(dishId: String): Flow<List<DishVariant>> =
        dishDao.observeVariants(dishId).map { items -> items.map { it.toModel() } }

    override suspend fun getVariants(dishId: String): List<DishVariant> =
        dishDao.getVariants(dishId).map { it.toModel() }

    override suspend fun saveVariant(variantId: String?, dishId: String, name: String, recipe: String, notes: String): DishVariant = database.withTransaction {
        val device = deviceManager.ensureDevice()
        check(dishDao.getById(dishId) != null) { "Plato no encontrado" }
        val current = variantId?.let { dishDao.getVariantById(it) }
        val now = System.currentTimeMillis()
        val entity = DishVariantEntity(
            id = current?.id ?: UUID.randomUUID().toString(),
            dishId = dishId,
            name = name.trim(),
            normalizedName = Normalizer.normalizeText(name),
            recipe = recipe.trim(),
            notes = notes.trim(),
            deleted = false,
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
            version = (current?.version ?: 0) + 1,
            updatedByDevice = device.deviceId,
        )
        dishDao.upsertVariant(entity)
        enqueueVariant(entity)
        entity.toModel()
    }

    override suspend fun saveDish(
        dishId: String?,
        name: String,
        category: String,
        mealType: DishMealType,
        imageBase64: String?,
        recipe: String,
        recipeSteps: String,
        preparationMinutes: Int?,
        servings: Int?,
        notes: String,
    ): Dish = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val current = dishId?.let { dishDao.getById(it) }
        val now = System.currentTimeMillis()
        val entity = DishEntity(
            id = current?.id ?: UUID.randomUUID().toString(),
            householdId = device.householdId,
            name = name.trim(),
            normalizedName = Normalizer.normalizeText(name),
            category = category.trim(),
            mealType = mealType.name,
            imageBase64 = imageBase64,
            recipe = recipe.trim(),
            recipeSteps = recipeSteps.trim(),
            preparationMinutes = preparationMinutes?.takeIf { it >= 0 },
            servings = servings?.takeIf { it > 0 },
            notes = notes.trim(),
            deleted = false,
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
            version = (current?.version ?: 0) + 1,
            updatedByDevice = device.deviceId,
        )
        dishDao.upsertDish(entity)
        enqueueDish(entity, if (current == null) "CREATE" else "UPDATE")
        entity.toModel()
    }

    override suspend fun saveIngredient(
        ingredientId: String?,
        dishId: String,
        name: String,
        quantity: Double?,
        unit: String,
        sortOrder: Int,
        variantId: String?,
    ): DishIngredient = database.withTransaction {
        val device = deviceManager.ensureDevice()
        check(dishDao.getById(dishId) != null) { "Plato no encontrado" }
        val current = ingredientId?.let { dishDao.getIngredientById(it) }
        val now = System.currentTimeMillis()
        val entity = DishIngredientEntity(
            id = current?.id ?: UUID.randomUUID().toString(),
            dishId = dishId,
            name = name.trim(),
            normalizedName = Normalizer.normalizeText(name),
            quantity = quantity,
            unit = unit.trim(),
            sortOrder = sortOrder.coerceAtLeast(0),
            deleted = false,
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
            version = (current?.version ?: 0) + 1,
            updatedByDevice = device.deviceId,
            variantId = variantId,
        )
        dishDao.upsertIngredient(entity)
        enqueueIngredient(entity)
        entity.toModel()
    }

    override suspend fun deleteDish(dishId: String): Dish? = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val current = dishDao.getById(dishId) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        val updated = current.copy(deleted = true, updatedAt = now, version = current.version + 1, updatedByDevice = device.deviceId)
        dishDao.upsertDish(updated)
        enqueueDish(updated, "DELETE")
        updated.toModel()
    }

    override suspend fun deleteIngredient(ingredientId: String): DishIngredient? = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val current = dishDao.getIngredientById(ingredientId) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        val updated = current.copy(deleted = true, updatedAt = now, version = current.version + 1, updatedByDevice = device.deviceId)
        dishDao.upsertIngredient(updated)
        enqueueIngredient(updated)
        updated.toModel()
    }

    private suspend fun enqueueDish(entity: DishEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(
            householdId = entity.householdId,
            deviceId = device.deviceId,
            entityType = "dish",
            entityId = entity.id,
            payload = DishSyncPayloadCodec.encodeDish(DishSyncPayload(entity.id, entity.householdId, entity.name, entity.normalizedName, entity.category, entity.mealType, entity.imageBase64, entity.recipe, entity.recipeSteps, entity.preparationMinutes, entity.servings, entity.notes, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = operationType,
        )
    }

    private suspend fun enqueueIngredient(entity: DishIngredientEntity) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(
            householdId = device.householdId,
            deviceId = device.deviceId,
            entityType = "dish_ingredient",
            entityId = entity.id,
            payload = DishSyncPayloadCodec.encodeIngredient(DishIngredientSyncPayload(entity.id, device.householdId, entity.dishId, entity.name, entity.normalizedName, entity.quantity, entity.unit, entity.sortOrder, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice, entity.variantId)),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = "UPDATE",
        )
    }

    private fun DishEntity.toModel() = Dish(id, name, category, runCatching { DishMealType.valueOf(mealType) }.getOrDefault(DishMealType.BOTH), imageBase64, recipe, recipeSteps, preparationMinutes, servings, notes, deleted, createdAt, updatedAt, version)
    private fun DishIngredientEntity.toModel() = DishIngredient(id, dishId, name, quantity, unit, sortOrder, deleted, variantId)
    private fun DishVariantEntity.toModel() = DishVariant(id, dishId, name, recipe, notes, deleted)

    private suspend fun enqueueVariant(entity: DishVariantEntity) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(
            householdId = device.householdId,
            deviceId = device.deviceId,
            entityType = "dish_variant",
            entityId = entity.id,
            payload = DishSyncPayloadCodec.encodeVariant(DishVariantSyncPayload(entity.id, device.householdId, entity.dishId, entity.name, entity.normalizedName, entity.recipe, entity.notes, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = "UPDATE",
        )
    }
}
