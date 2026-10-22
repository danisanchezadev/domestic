package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.ShoppingCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingCategoryDao {
    @Query("SELECT * FROM shopping_categories WHERE deleted = 0 AND hidden = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(): Flow<List<ShoppingCategoryEntity>>

    @Query("SELECT * FROM shopping_categories WHERE deleted = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun getCategories(): List<ShoppingCategoryEntity>

    @Upsert
    suspend fun upsert(category: ShoppingCategoryEntity)
}

