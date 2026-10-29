package com.majyd.casa.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.DishEntity
import com.majyd.casa.data.local.entities.RecipeCategoryEntity
import com.majyd.casa.data.local.entities.RecipeCategoryLinkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DishCollectionIsolationTest {
    private lateinit var database: CasaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CasaDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun menuAndRecipeBookAreIndependentCollections() = runTest {
        database.dishDao().upsertDish(dish("menu", "MENU"))
        database.dishDao().upsertDish(dish("recipe", "RECIPE_BOOK"))

        assertEquals(listOf("menu"), database.dishDao().observeDishes("MENU").first().map { it.id })
        assertEquals(listOf("recipe"), database.dishDao().observeDishes("RECIPE_BOOK").first().map { it.id })
    }

    @Test
    fun recipeCategoriesKeepStableLinksAndSortActiveCategories() = runTest {
        database.recipeCategoryDao().upsertCategory(category("z", "Zetas"))
        database.recipeCategoryDao().upsertCategory(category("a", "Arroces"))
        database.recipeCategoryDao().upsertLink(RecipeCategoryLinkEntity("recipe", "z", false, 1, 1, "device"))

        assertEquals(listOf("Arroces", "Zetas"), database.recipeCategoryDao().observeActive().first().map { it.name })
        assertEquals(listOf("z"), database.recipeCategoryDao().getLinks("recipe").map { it.categoryId })
    }

    private fun dish(id: String, collection: String) = DishEntity(
        id = id,
        householdId = "household",
        name = id,
        normalizedName = id,
        category = "",
        mealType = "BOTH",
        imageBase64 = null,
        recipe = "",
        recipeSteps = "",
        preparationMinutes = null,
        servings = null,
        notes = "",
        deleted = false,
        createdAt = 1,
        updatedAt = 1,
        version = 1,
        updatedByDevice = "device",
        collection = collection,
    )

    private fun category(id: String, name: String) = RecipeCategoryEntity(
        id = id,
        householdId = "household",
        name = name,
        normalizedName = name.lowercase(),
        deleted = false,
        createdAt = 1,
        updatedAt = 1,
        version = 1,
        updatedByDevice = "device",
    )
}
