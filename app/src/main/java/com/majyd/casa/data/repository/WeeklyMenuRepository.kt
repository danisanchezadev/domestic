package com.majyd.casa.data.repository

import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.WeeklyMenuItem
import kotlinx.coroutines.flow.Flow

interface WeeklyMenuRepository {
    fun observeItems(): Flow<List<WeeklyMenuItem>>
    suspend fun ensureDefaults()
    suspend fun upsertItem(
        itemId: String?,
        dayOfWeek: Int,
        mealSlot: MealSlot,
        title: String,
        notes: String,
    ): WeeklyMenuItem
    suspend fun deleteItem(itemId: String): WeeklyMenuItem?
    suspend fun restoreItem(itemId: String): WeeklyMenuItem?
    suspend fun archiveAll()
}
