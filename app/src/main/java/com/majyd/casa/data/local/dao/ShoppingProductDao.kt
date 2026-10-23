package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.ShoppingProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingProductDao {
    @Query("SELECT * FROM shopping_products WHERE listId = :listId AND deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeProducts(listId: String): Flow<List<ShoppingProductEntity>>

    @Query("SELECT * FROM shopping_products WHERE listId = :listId AND deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getProducts(listId: String): List<ShoppingProductEntity>

    @Query("SELECT * FROM shopping_products WHERE id = :productId LIMIT 1")
    suspend fun getById(productId: String): ShoppingProductEntity?

    @Query("SELECT * FROM shopping_products WHERE listId = :listId AND normalizedName = :normalizedName AND deleted = 0 LIMIT 1")
    suspend fun getActiveByName(listId: String, normalizedName: String): ShoppingProductEntity?

    @Query("SELECT * FROM shopping_products WHERE listId = :listId AND deleted = 1 ORDER BY updatedAt DESC")
    fun observeDeletedProducts(listId: String): Flow<List<ShoppingProductEntity>>

    @Upsert
    suspend fun upsert(product: ShoppingProductEntity)

    @Query("UPDATE shopping_products SET purchased = :purchased, purchasedAt = :purchasedAt, updatedAt = :updatedAt, version = version + 1 WHERE id = :productId")
    suspend fun updatePurchased(productId: String, purchased: Boolean, purchasedAt: Long?, updatedAt: Long)

    @Query("UPDATE shopping_products SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE id = :productId")
    suspend fun softDelete(productId: String, updatedAt: Long)

    @Query("UPDATE shopping_products SET deleted = 0, updatedAt = :updatedAt, version = version + 1 WHERE id = :productId")
    suspend fun restore(productId: String, updatedAt: Long)
}
