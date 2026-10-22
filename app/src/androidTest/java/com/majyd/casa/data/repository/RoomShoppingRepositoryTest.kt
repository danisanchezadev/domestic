package com.majyd.casa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.sync.DefaultDeviceManager
import com.majyd.casa.sync.RoomSyncOperationRepository
import com.majyd.casa.sync.SyncChangeNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
}
