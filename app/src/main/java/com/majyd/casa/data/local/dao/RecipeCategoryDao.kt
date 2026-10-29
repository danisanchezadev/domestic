package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.RecipeCategoryEntity
import com.majyd.casa.data.local.entities.RecipeCategoryLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeCategoryDao {
    @Query("SELECT * FROM recipe_categories WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<RecipeCategoryEntity>>

    @Query("SELECT * FROM recipe_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecipeCategoryEntity?

    @Query("SELECT * FROM recipe_categories WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): RecipeCategoryEntity?

    @Query("SELECT * FROM recipe_category_links WHERE dishId = :dishId AND deleted = 0")
    suspend fun getLinks(dishId: String): List<RecipeCategoryLinkEntity>

    @Query("SELECT * FROM recipe_category_links WHERE deleted = 0")
    fun observeActiveLinks(): Flow<List<RecipeCategoryLinkEntity>>

    @Upsert suspend fun upsertCategory(category: RecipeCategoryEntity)
    @Upsert suspend fun upsertLink(link: RecipeCategoryLinkEntity)
}
