package com.majyd.casa.data.repository

import com.majyd.casa.data.model.Dish
import com.majyd.casa.data.model.DishIngredient
import com.majyd.casa.data.model.DishMealType
import com.majyd.casa.data.model.DishVariant
import kotlinx.coroutines.flow.Flow

interface DishRepository {
    fun observeDishes(): Flow<List<Dish>>
    fun observeIngredients(dishId: String): Flow<List<DishIngredient>>
    suspend fun getIngredients(dishId: String, variantId: String? = null): List<DishIngredient>
    fun observeVariants(dishId: String): Flow<List<DishVariant>>
    suspend fun getVariants(dishId: String): List<DishVariant>
    suspend fun saveVariant(variantId: String?, dishId: String, name: String, recipe: String, notes: String): DishVariant
    suspend fun saveDish(
        dishId: String?,
        name: String,
        mealType: DishMealType,
        imageBase64: String?,
        recipe: String,
        preparationMinutes: Int?,
        servings: Int?,
        notes: String,
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
