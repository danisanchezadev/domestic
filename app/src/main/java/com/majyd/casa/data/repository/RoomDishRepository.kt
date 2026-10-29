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
import com.majyd.casa.data.model.DishCollection
import com.majyd.casa.data.model.RecipeCategory
import com.majyd.casa.data.local.dao.RecipeCategoryDao
import com.majyd.casa.data.local.dao.WeeklyMenuDao
import com.majyd.casa.data.local.entities.RecipeCategoryEntity
import com.majyd.casa.data.local.entities.RecipeCategoryLinkEntity
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.DishIngredientSyncPayload
import com.majyd.casa.sync.DishVariantSyncPayload
import com.majyd.casa.sync.DishSyncPayload
import com.majyd.casa.sync.DishSyncPayloadCodec
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.util.Normalizer
import com.majyd.casa.notifications.NotificationScheduler
import com.majyd.casa.data.model.MealSlot
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class RoomDishRepository @Inject constructor(
    private val database: CasaDatabase,
    private val dishDao: DishDao,
    private val deviceManager: DeviceManager,
    private val syncOperationRepository: SyncOperationRepository,
    private val recipeCategoryDao: RecipeCategoryDao,
    private val weeklyMenuDao: WeeklyMenuDao,
    private val notificationScheduler: NotificationScheduler,
) : DishRepository {
    override fun observeDishes(collection: DishCollection): Flow<List<Dish>> {
        val dishes = dishDao.observeDishes(collection.name)
        if (collection != DishCollection.RECIPE_BOOK) {
            return dishes.map { items -> items.map { it.toModel() } }
        }
        return combine(dishes, recipeCategoryDao.observeActive(), recipeCategoryDao.observeActiveLinks()) { items, categories, links ->
            val categoriesById = categories.associateBy { it.id }
            val linksByDish = links.groupBy { it.dishId }
            items.map { entity ->
                val categoryIds = linksByDish[entity.id].orEmpty().map { it.categoryId }.toSet()
                val categoryNames = categoryIds.mapNotNull { categoriesById[it]?.name }
                entity.toModel(categoryNames, categoryIds)
            }
        }
    }

    override fun observeIngredients(dishId: String): Flow<List<DishIngredient>> =
        dishDao.observeIngredients(dishId).map { items -> items.map { it.toModel() } }

    override suspend fun getDish(dishId: String): Dish? =
        dishDao.getById(dishId)?.takeUnless { it.deleted }?.toModel()

    override suspend fun getIngredients(dishId: String, variantId: String?): List<DishIngredient> =
        dishDao.getIngredients(dishId, variantId).map { it.toModel() }

    override fun observeVariants(dishId: String): Flow<List<DishVariant>> =
        dishDao.observeVariants(dishId).map { items -> items.map { it.toModel() } }

    override fun observeRecipeCategories(): Flow<List<RecipeCategory>> = recipeCategoryDao.observeActive().map { it.map { category -> category.toModel() } }

    override suspend fun ensureRecipeCategories() {
        val device = deviceManager.ensureDevice()
        val defaults = listOf("Comidas", "Cenas", "Desayunos", "Meriendas", "Postres", "Salsas", "Bebidas y batidos", "Aperitivos", "Guarniciones", "Otras recetas")
        database.withTransaction {
            defaults.forEach { name ->
                if (recipeCategoryDao.getByNormalizedName(Normalizer.normalizeText(name)) == null) {
                    val now = System.currentTimeMillis()
                    val entity = RecipeCategoryEntity(UUID.randomUUID().toString(), device.householdId, name, Normalizer.normalizeText(name), false, now, now, 1, device.deviceId)
                    recipeCategoryDao.upsertCategory(entity)
                    enqueueCategory(entity, "CREATE")
                }
            }
        }
    }

    override suspend fun saveRecipeCategory(categoryId: String?, name: String): RecipeCategory? = database.withTransaction {
        val clean = name.trim()
        if (clean.isBlank()) return@withTransaction null
        val device = deviceManager.ensureDevice()
        val existing = categoryId?.let { recipeCategoryDao.getById(it) } ?: recipeCategoryDao.getByNormalizedName(Normalizer.normalizeText(clean))
        val now = System.currentTimeMillis()
        val entity = RecipeCategoryEntity(existing?.id ?: UUID.randomUUID().toString(), device.householdId, clean, Normalizer.normalizeText(clean), false, existing?.createdAt ?: now, now, (existing?.version ?: 0) + 1, device.deviceId)
        recipeCategoryDao.upsertCategory(entity)
        enqueueCategory(entity, if (existing == null) "CREATE" else "UPDATE")
        entity.toModel()
    }

    override suspend fun deleteRecipeCategory(categoryId: String): Boolean = database.withTransaction {
        val current = recipeCategoryDao.getById(categoryId) ?: return@withTransaction false
        val device = deviceManager.ensureDevice()
        val updated = current.copy(deleted = true, updatedAt = System.currentTimeMillis(), version = current.version + 1, updatedByDevice = device.deviceId)
        recipeCategoryDao.upsertCategory(updated)
        enqueueCategory(updated, "DELETE")
        true
    }

    override suspend fun setRecipeCategories(dishId: String, categoryIds: Set<String>) = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val current = recipeCategoryDao.getLinks(dishId).associateBy { it.categoryId }
        val allIds = current.keys + categoryIds
        allIds.forEach { categoryId ->
            val previous = current[categoryId]
            val now = System.currentTimeMillis()
            val link = RecipeCategoryLinkEntity(dishId, categoryId, categoryId !in categoryIds, now, (previous?.version ?: 0) + 1, device.deviceId)
            recipeCategoryDao.upsertLink(link)
            enqueueCategoryLink(link, if (link.deleted) "DELETE" else "UPDATE")
        }
    }

    override suspend fun getRecipeCategoryIds(dishId: String): Set<String> = recipeCategoryDao.getLinks(dishId).filterNot { it.deleted }.map { it.categoryId }.toSet()

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
        collection: DishCollection,
        preparationRequired: Boolean,
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
            collection = collection.name,
            preparationRequired = preparationRequired,
        )
        dishDao.upsertDish(entity)
        reschedulePreparation(entity)
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
        reschedulePreparation(updated)
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
            payload = DishSyncPayloadCodec.encodeDish(DishSyncPayload(entity.id, entity.householdId, entity.name, entity.normalizedName, entity.category, entity.mealType, entity.imageBase64, entity.recipe, entity.recipeSteps, entity.preparationMinutes, entity.servings, entity.notes, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice, entity.collection, entity.preparationRequired)),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = operationType,
        )
    }

    private suspend fun reschedulePreparation(dish: DishEntity) {
        val weekStart = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()).toEpochDay()
        weeklyMenuDao.getActiveByDish(dish.id).forEach { item ->
            if (dish.deleted || !dish.preparationRequired) {
                notificationScheduler.cancelPreparation(item.id)
            } else {
                val date = LocalDate.ofEpochDay(weekStart).plusDays((item.dayOfWeek - 1).toLong())
                notificationScheduler.schedulePreparation(
                    item.id,
                    dish.name,
                    date,
                    runCatching { MealSlot.valueOf(item.mealSlot) }.getOrDefault(MealSlot.LUNCH),
                )
            }
        }
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

    private fun DishEntity.toModel(categoryNames: List<String> = emptyList(), categoryIds: Set<String> = emptySet()) = Dish(
        id = id,
        name = name,
        category = categoryNames.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: category,
        mealType = runCatching { DishMealType.valueOf(mealType) }.getOrDefault(DishMealType.BOTH),
        imageBase64 = imageBase64,
        recipe = recipe,
        recipeSteps = recipeSteps,
        preparationMinutes = preparationMinutes,
        servings = servings,
        notes = notes,
        deleted = deleted,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        preparationRequired = preparationRequired,
        categoryIds = categoryIds,
    )
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

    private suspend fun enqueueCategory(entity: RecipeCategoryEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(entity.householdId, device.deviceId, "recipe_category", entity.id, com.majyd.casa.sync.DishSyncPayloadCodec.encodeCategory(com.majyd.casa.sync.RecipeCategorySyncPayload(entity.id, entity.householdId, entity.name, entity.normalizedName, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private suspend fun enqueueCategoryLink(entity: RecipeCategoryLinkEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(device.householdId, device.deviceId, "recipe_category_link", "${entity.dishId}:${entity.categoryId}", com.majyd.casa.sync.DishSyncPayloadCodec.encodeCategoryLink(com.majyd.casa.sync.RecipeCategoryLinkSyncPayload(entity.dishId, entity.categoryId, entity.deleted, entity.updatedAt, entity.version, entity.updatedByDevice)), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private fun RecipeCategoryEntity.toModel() = RecipeCategory(id, name, deleted)
}
