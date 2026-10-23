package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.DishEntity
import com.majyd.casa.data.local.entities.DishIngredientEntity
import com.majyd.casa.data.local.entities.DishVariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DishDao {
    @Query("SELECT * FROM dishes WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeDishes(): Flow<List<DishEntity>>

    @Query("SELECT * FROM dishes WHERE id = :dishId LIMIT 1")
    suspend fun getById(dishId: String): DishEntity?

    @Query("SELECT * FROM dish_ingredients WHERE dishId = :dishId AND variantId IS :variantId AND deleted = 0 ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeIngredients(dishId: String, variantId: String? = null): Flow<List<DishIngredientEntity>>

    @Query("SELECT * FROM dish_ingredients WHERE dishId = :dishId AND variantId IS :variantId AND deleted = 0 ORDER BY sortOrder ASC")
    suspend fun getIngredients(dishId: String, variantId: String? = null): List<DishIngredientEntity>

    @Query("SELECT * FROM dish_variants WHERE dishId = :dishId AND deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeVariants(dishId: String): Flow<List<DishVariantEntity>>

    @Query("SELECT * FROM dish_variants WHERE dishId = :dishId AND deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getVariants(dishId: String): List<DishVariantEntity>

    @Query("SELECT * FROM dish_variants WHERE id = :variantId LIMIT 1")
    suspend fun getVariantById(variantId: String): DishVariantEntity?

    @Query("SELECT * FROM dish_ingredients WHERE id = :ingredientId LIMIT 1")
    suspend fun getIngredientById(ingredientId: String): DishIngredientEntity?

    @Upsert
    suspend fun upsertDish(dish: DishEntity)

    @Upsert
    suspend fun upsertIngredient(ingredient: DishIngredientEntity)

    @Upsert
    suspend fun upsertVariant(variant: DishVariantEntity)

    @Query("UPDATE dishes SET deleted = 1, updatedAt = :updatedAt, version = version + 1, updatedByDevice = :deviceId WHERE id = :dishId")
    suspend fun softDeleteDish(dishId: String, updatedAt: Long, deviceId: String)

    @Query("UPDATE dish_ingredients SET deleted = 1, updatedAt = :updatedAt, version = version + 1, updatedByDevice = :deviceId WHERE dishId = :dishId AND deleted = 0")
    suspend fun softDeleteIngredients(dishId: String, updatedAt: Long, deviceId: String)

    @Query("UPDATE dish_ingredients SET deleted = 1, updatedAt = :updatedAt, version = version + 1, updatedByDevice = :deviceId WHERE id = :ingredientId")
    suspend fun softDeleteIngredient(ingredientId: String, updatedAt: Long, deviceId: String)
}
