package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.MenuHistoryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuHistoryDao {
    @Query("SELECT * FROM menu_history_items WHERE householdId = :householdId ORDER BY weekStartEpochDay DESC, dayOfWeek ASC, mealSlot ASC")
    fun observeAll(householdId: String): Flow<List<MenuHistoryItemEntity>>

    @Query("SELECT * FROM menu_history_items WHERE weekStartEpochDay = :weekStartEpochDay AND dayOfWeek = :dayOfWeek AND mealSlot = :mealSlot LIMIT 1")
    suspend fun getForSlot(weekStartEpochDay: Long, dayOfWeek: Int, mealSlot: String): MenuHistoryItemEntity?

    @Query("SELECT * FROM menu_history_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MenuHistoryItemEntity?

    @Upsert
    suspend fun upsert(item: MenuHistoryItemEntity)

    @Query("DELETE FROM menu_history_items WHERE weekStartEpochDay < :minimumWeekStartEpochDay")
    suspend fun deleteOlderThan(minimumWeekStartEpochDay: Long)
}
