package com.majyd.casa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.sync.SyncOperation
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.sync.ShoppingProductSyncPayload
import com.majyd.casa.sync.ShoppingSyncApplier
import com.majyd.casa.sync.ShoppingSyncPayloadCodec
import com.majyd.casa.sync.DefaultDeviceManager
import com.majyd.casa.sync.RoomSyncOperationRepository
import com.majyd.casa.sync.SyncChangeNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomShoppingRepositoryTest {
    private lateinit var database: CasaDatabase
    private lateinit var repository: RoomShoppingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CasaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomShoppingRepository(
            database = database,
            listDao = database.shoppingListDao(),
            categoryDao = database.shoppingCategoryDao(),
            productDao = database.shoppingProductDao(),
            catalogDao = database.shoppingCatalogDao(),
            syncOperationRepository = RoomSyncOperationRepository(database.syncOperationDao(), SyncChangeNotifier()),
            deviceManager = DefaultDeviceManager(RoomDeviceRepository(database.deviceDao())),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ensureDefaults_createsMainListAndCategories() = runTest {
        repository.ensureDefaults()

        val lists = repository.observeLists().first()
        val categories = repository.observeCategories().first()

        assertTrue(lists.any { it.id == RoomShoppingRepository.DEFAULT_LIST_ID })
        assertTrue(categories.isNotEmpty())
    }

    @Test
    fun upsertProduct_updatesHistoricalCatalogSuggestions() = runTest {
        repository.ensureDefaults()
        repository.upsertProduct(
            listId = RoomShoppingRepository.DEFAULT_LIST_ID,
            productId = null,
            name = "Leche",
            categoryId = null,
            quantity = 2.0,
            unit = "l",
            notes = "",
            purchased = false,
        )

        val suggestions = repository.observeSuggestions("le").first()

        assertEquals("Leche", suggestions.first().displayName)
        assertEquals("leche", suggestions.first().normalizedName)
    }

    @Test
    fun catalogWrite_rollsBackWhenSyncOperationCannotBeEnqueued() = runTest {
        val failingRepository = RoomShoppingRepository(
            database = database,
            listDao = database.shoppingListDao(),
            categoryDao = database.shoppingCategoryDao(),
            productDao = database.shoppingProductDao(),
            catalogDao = database.shoppingCatalogDao(),
            syncOperationRepository = FailingSyncOperationRepository(),
            deviceManager = DefaultDeviceManager(RoomDeviceRepository(database.deviceDao())),
        )

        try {
            failingRepository.addCatalogItem("Leche")
            fail("Se esperaba un fallo de sincronización")
        } catch (_: IllegalStateException) {
            assertTrue(database.shoppingCatalogDao().getByNameIncludingDeleted("leche") == null)
        }
    }

    @Test
    fun listWrite_rollsBackWhenSyncOperationCannotBeEnqueued() = runTest {
        val failingRepository = repositoryWithFailingSync()

        try {
            failingRepository.createList("Compra semanal")
            fail("Se esperaba un fallo de sincronización")
        } catch (_: IllegalStateException) {
            assertTrue(database.shoppingListDao().getLists().none { it.name == "Compra semanal" })
        }
    }

    @Test
    fun productWrite_rollsBackWhenSyncOperationCannotBeEnqueued() = runTest {
        repository.ensureDefaults()
        val failingRepository = repositoryWithFailingSync()

        try {
            failingRepository.upsertProduct(
                listId = RoomShoppingRepository.DEFAULT_LIST_ID,
                productId = null,
                name = "Pan",
                categoryId = null,
                quantity = 1.0,
                unit = "ud",
                notes = "",
                purchased = false,
            )
            fail("Se esperaba un fallo de sincronización")
        } catch (_: IllegalStateException) {
            assertTrue(database.shoppingProductDao().getProducts(RoomShoppingRepository.DEFAULT_LIST_ID).none { it.name == "Pan" })
        }
    }

    @Test
    fun incomingProduct_rollsBackWhenOperationRecordFails() = runTest {
        repository.ensureDefaults()
        val operation = SyncOperation(
            operationId = "incoming-product-operation",
            householdId = "household-default",
            entityType = "shopping_product",
            entityId = "remote-product",
            operationType = "CREATE",
            payload = ShoppingSyncPayloadCodec.encodeProduct(
                ShoppingProductSyncPayload(
                    id = "remote-product",
                    householdId = "household-default",
                    listId = RoomShoppingRepository.DEFAULT_LIST_ID,
                    name = "Huevos",
                    normalizedName = "huevos",
                    categoryId = null,
                    quantity = 1.0,
                    unit = "docena",
                    notes = "",
                    purchased = false,
                    purchasedAt = null,
                    createdAt = 1L,
                    updatedAt = 2L,
                    createdByDevice = "remote-device",
                    updatedByDevice = "remote-device",
                    deleted = false,
                    version = 1L,
                )
            ),
            version = 1L,
            createdAt = 1L,
            updatedAt = 2L,
            deviceId = "remote-device",
            deleted = false,
            applied = false,
        )

        try {
            ShoppingSyncApplier(database, FailingSyncOperationRepository()).apply(operation)
            fail("Se esperaba un fallo al registrar la operación entrante")
        } catch (_: IllegalStateException) {
            assertTrue(database.shoppingProductDao().getById("remote-product") == null)
            assertTrue(database.shoppingCatalogDao().getByNameIncludingDeleted("huevos") == null)
        }
    }

    private fun repositoryWithFailingSync() = RoomShoppingRepository(
        database = database,
        listDao = database.shoppingListDao(),
        categoryDao = database.shoppingCategoryDao(),
        productDao = database.shoppingProductDao(),
        catalogDao = database.shoppingCatalogDao(),
        syncOperationRepository = FailingSyncOperationRepository(),
        deviceManager = DefaultDeviceManager(RoomDeviceRepository(database.deviceDao())),
    )

    private class FailingSyncOperationRepository : SyncOperationRepository {
        override suspend fun enqueue(
            householdId: String,
            deviceId: String,
            entityType: String,
            entityId: String,
            payload: String,
            version: Long,
            updatedAt: Long,
            deleted: Boolean,
            operationType: String,
            createdAt: Long,
        ) = error("Fallo de persistencia de operación")

        override fun observePending(householdId: String): Flow<List<SyncOperation>> = emptyFlow()
        override suspend fun markApplied(operationId: String) = Unit
        override suspend fun get(operationId: String): SyncOperation? = null
        override suspend fun record(operation: SyncOperation) = error("Fallo de persistencia de operación")
    }
}
