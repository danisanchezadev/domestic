package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.WeeklyMenuItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyMenuDao {
    @Query("SELECT * FROM weekly_menu_items ORDER BY deleted ASC, dayOfWeek ASC, mealSlot ASC, updatedAt DESC")
    fun observeItems(): Flow<List<WeeklyMenuItemEntity>>

    @Query("SELECT * FROM weekly_menu_items WHERE id = :itemId LIMIT 1")
    suspend fun getById(itemId: String): WeeklyMenuItemEntity?

    @Query("SELECT * FROM weekly_menu_items WHERE dayOfWeek = :dayOfWeek AND mealSlot = :mealSlot AND deleted = 0 LIMIT 1")
    suspend fun getActiveBySlot(dayOfWeek: Int, mealSlot: String): WeeklyMenuItemEntity?

    @Query("SELECT COUNT(*) FROM weekly_menu_items WHERE dishId = :dishId AND deleted = 0")
    suspend fun countActiveAssignments(dishId: String): Int

    @Query("SELECT MAX(updatedAt) FROM weekly_menu_items WHERE dishId = :dishId AND deleted = 0")
    suspend fun lastAssignmentUpdate(dishId: String): Long?

    @Query("SELECT * FROM weekly_menu_items WHERE deleted = 0")
    suspend fun getActive(): List<WeeklyMenuItemEntity>

    @Upsert
    suspend fun upsert(item: WeeklyMenuItemEntity)

    @Query("UPDATE weekly_menu_items SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE id = :itemId")
    suspend fun softDelete(itemId: String, updatedAt: Long)

    @Query("UPDATE weekly_menu_items SET deleted = 0, updatedAt = :updatedAt, version = version + 1 WHERE id = :itemId")
    suspend fun restore(itemId: String, updatedAt: Long)

    @Query("UPDATE weekly_menu_items SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE deleted = 0")
    suspend fun archiveAll(updatedAt: Long)
}
