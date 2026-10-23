package com.majyd.casa.data.repository

import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.WeeklyMenuItem
import com.majyd.casa.data.model.DishUsageStats
import com.majyd.casa.data.model.MenuHistoryItem
import kotlinx.coroutines.flow.Flow

interface WeeklyMenuRepository {
    fun observeItems(): Flow<List<WeeklyMenuItem>>
    fun observeHistory(): Flow<List<MenuHistoryItem>>
    suspend fun getUsageStats(dishId: String): DishUsageStats
    suspend fun ensureDefaults()
    suspend fun upsertItem(
        itemId: String?,
        dayOfWeek: Int,
        mealSlot: MealSlot,
        dishId: String?,
        variantId: String?,
        title: String,
        notes: String,
        shoppingIngredientIds: Set<String>? = null,
    ): WeeklyMenuItem
    suspend fun deleteItem(itemId: String): WeeklyMenuItem?
    suspend fun restoreItem(itemId: String): WeeklyMenuItem?
    suspend fun archiveAll()
    suspend fun setHistoryCompleted(historyId: String, completed: Boolean)
}
