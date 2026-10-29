package com.majyd.casa.data.repository

import com.majyd.casa.data.model.Dish
import com.majyd.casa.data.model.DishIngredient
import com.majyd.casa.data.model.DishMealType
import com.majyd.casa.data.model.DishVariant
import com.majyd.casa.data.model.DishCollection
import com.majyd.casa.data.model.RecipeCategory
import kotlinx.coroutines.flow.Flow

interface DishRepository {
    fun observeDishes(collection: DishCollection = DishCollection.MENU): Flow<List<Dish>>
    suspend fun getDish(dishId: String): Dish?
    fun observeIngredients(dishId: String): Flow<List<DishIngredient>>
    suspend fun getIngredients(dishId: String, variantId: String? = null): List<DishIngredient>
    fun observeVariants(dishId: String): Flow<List<DishVariant>>
    fun observeRecipeCategories(): Flow<List<RecipeCategory>>
    suspend fun ensureRecipeCategories()
    suspend fun saveRecipeCategory(categoryId: String?, name: String): RecipeCategory?
    suspend fun deleteRecipeCategory(categoryId: String): Boolean
    suspend fun setRecipeCategories(dishId: String, categoryIds: Set<String>)
    suspend fun getRecipeCategoryIds(dishId: String): Set<String>
    suspend fun getVariants(dishId: String): List<DishVariant>
    suspend fun saveVariant(variantId: String?, dishId: String, name: String, recipe: String, notes: String): DishVariant
    suspend fun saveDish(
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
        collection: DishCollection = DishCollection.MENU,
        preparationRequired: Boolean = false,
    ): Dish
    suspend fun saveIngredient(
        ingredientId: String?,
        dishId: String,
        name: String,
        quantity: Double?,
        unit: String,
        sortOrder: Int,
        variantId: String? = null,
    ): DishIngredient
    suspend fun deleteDish(dishId: String): Dish?
    suspend fun deleteIngredient(ingredientId: String): DishIngredient?
}
