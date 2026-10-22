package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.ContactCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactCategoryDao {
    @Query("SELECT * FROM contact_categories WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<ContactCategoryEntity>>

    @Query("SELECT * FROM contact_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ContactCategoryEntity?

    @Query("SELECT * FROM contact_categories WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): ContactCategoryEntity?

    @Upsert
    suspend fun upsert(category: ContactCategoryEntity)
}
