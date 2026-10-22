package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.ShoppingListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_lists WHERE deleted = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE deleted = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun getLists(): List<ShoppingListEntity>

    @Query("SELECT * FROM shopping_lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShoppingListEntity?

    @Upsert
    suspend fun upsert(list: ShoppingListEntity)

    @Query("SELECT COUNT(*) FROM shopping_lists")
    suspend fun count(): Int
}
