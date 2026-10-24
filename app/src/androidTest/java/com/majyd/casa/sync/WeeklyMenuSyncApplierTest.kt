package com.majyd.casa.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.DishEntity
import com.majyd.casa.data.local.entities.DishIngredientEntity
import com.majyd.casa.data.local.entities.WeeklyMenuItemEntity
import com.majyd.casa.data.repository.RoomDeviceRepository
import com.majyd.casa.data.repository.RoomShoppingRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeeklyMenuSyncApplierTest {
    private lateinit var database: CasaDatabase
    private lateinit var applier: WeeklyMenuSyncApplier

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CasaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val operationRepository = RoomSyncOperationRepository(database.syncOperationDao(), SyncChangeNotifier())
        val deviceManager = DefaultDeviceManager(RoomDeviceRepository(database.deviceDao()))
        val shoppingRepository = RoomShoppingRepository(
            database = database,
            listDao = database.shoppingListDao(),
            categoryDao = database.shoppingCategoryDao(),
            productDao = database.shoppingProductDao(),
            catalogDao = database.shoppingCatalogDao(),
            syncOperationRepository = operationRepository,
            deviceManager = deviceManager,
        )
        applier = WeeklyMenuSyncApplier(database, operationRepository, shoppingRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun applyingSameAssignmentTwiceDoesNotDuplicateShoppingIngredient() = runTest {
        seedDish()
        val operation = operation(version = 1L, operationId = "menu-create")

        assertTrue(applier.apply(operation))
        assertTrue(applier.apply(operation))

        val products = database.shoppingProductDao().getProducts(RoomShoppingRepository.DEFAULT_LIST_ID)
        assertEquals(1, products.count { it.normalizedName == "arroz" && !it.deleted })
    }

    @Test
    fun losingAssignmentDoesNotAddShoppingIngredient() = runTest {
        seedDish()
        database.weeklyMenuDao().upsert(
            WeeklyMenuItemEntity(
                id = "menu-existing",
                householdId = HOUSEHOLD_ID,
                dayOfWeek = 1,
                mealSlot = "LUNCH",
                dishId = DISH_ID,
                variantId = null,
                title = "Plato local",
                normalizedTitle = "plato local",
                notes = "",
                deleted = false,
                createdAt = 1L,
                updatedAt = 200L,
                version = 2L,
                updatedByDevice = "local-device",
            ),
        )

        assertTrue(applier.apply(operation(version = 1L, operationId = "menu-loser")))

        assertTrue(database.shoppingProductDao().getProducts(RoomShoppingRepository.DEFAULT_LIST_ID).isEmpty())
        assertEquals(2L, database.weeklyMenuDao().getById("menu-existing")?.version)
    }

    private suspend fun seedDish() {
        database.dishDao().upsertDish(
            DishEntity(
                id = DISH_ID,
                householdId = HOUSEHOLD_ID,
                name = "Arroz",
                normalizedName = "arroz",
                category = "Arroces",
                mealType = "BOTH",
                imageBase64 = null,
                recipe = "",
                recipeSteps = "",
                preparationMinutes = 20,
                servings = 2,
                notes = "",
                deleted = false,
                createdAt = 1L,
                updatedAt = 1L,
                version = 1L,
                updatedByDevice = "remote-device",
            ),
        )
        database.dishDao().upsertIngredient(
            DishIngredientEntity(
                id = INGREDIENT_ID,
                dishId = DISH_ID,
                name = "Arroz",
                normalizedName = "arroz",
                quantity = 1.0,
                unit = "kg",
                sortOrder = 0,
                deleted = false,
                createdAt = 1L,
                updatedAt = 1L,
                version = 1L,
                updatedByDevice = "remote-device",
            ),
        )
        RoomShoppingRepository(
            database = database,
            listDao = database.shoppingListDao(),
            categoryDao = database.shoppingCategoryDao(),
            productDao = database.shoppingProductDao(),
            catalogDao = database.shoppingCatalogDao(),
            syncOperationRepository = RoomSyncOperationRepository(database.syncOperationDao(), SyncChangeNotifier()),
            deviceManager = DefaultDeviceManager(RoomDeviceRepository(database.deviceDao())),
        ).ensureDefaults()
    }

    private fun operation(version: Long, operationId: String) = SyncOperation(
        operationId = operationId,
        householdId = HOUSEHOLD_ID,
        entityType = "weekly_menu_item",
        entityId = "menu-existing",
        operationType = "CREATE",
        payload = WeeklyMenuSyncPayloadCodec.encode(
            WeeklyMenuItemSyncPayload(
                id = "menu-existing",
                householdId = HOUSEHOLD_ID,
                dayOfWeek = 1,
                mealSlot = "LUNCH",
                dishId = DISH_ID,
                variantId = null,
                shoppingIngredientIds = listOf(INGREDIENT_ID),
                title = "Arroz",
                normalizedTitle = "arroz",
                notes = "",
                deleted = false,
                createdAt = 1L,
                updatedAt = version,
                version = version,
                updatedByDevice = "remote-device",
            ),
        ),
        version = version,
        createdAt = version,
        updatedAt = version,
        deviceId = "remote-device",
        deleted = false,
        applied = false,
    )

    companion object {
        private const val HOUSEHOLD_ID = "household-default"
        private const val DISH_ID = "dish-rice"
        private const val INGREDIENT_ID = "ingredient-rice"
    }
}
