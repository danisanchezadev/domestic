package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.ShoppingCatalogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingCatalogDao {
    @Query("SELECT * FROM shopping_catalog")
    suspend fun getAllIncludingDeleted(): List<ShoppingCatalogEntity>

    @Query(
        """
        SELECT * FROM shopping_catalog
        WHERE deleted = 0 AND normalizedName LIKE :prefix || '%'
        ORDER BY displayName COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun observeSuggestions(prefix: String, limit: Int = 50): Flow<List<ShoppingCatalogEntity>>

    @Query("SELECT * FROM shopping_catalog WHERE deleted = 0 AND normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): ShoppingCatalogEntity?

    @Query("SELECT * FROM shopping_catalog WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNameIncludingDeleted(normalizedName: String): ShoppingCatalogEntity?

    @Upsert
    suspend fun upsert(entry: ShoppingCatalogEntity)
}
