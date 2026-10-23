package com.majyd.casa.data.model

data class MenuHistoryItem(
    val id: String,
    val weekStartEpochDay: Long,
    val dayOfWeek: Int,
    val mealSlot: MealSlot,
    val dishId: String?,
    val variantId: String?,
    val title: String,
    val notes: String,
    val completed: Boolean,
    val completedAt: Long?,
)
