package com.majyd.casa.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MealSlot(val label: String) {
    BREAKFAST("Desayuno"),
    LUNCH("Comida"),
    DINNER("Cena"),
    SNACK("Merienda"),
}

data class WeeklyMenuItem(
    val id: String,
    val dayOfWeek: Int,
    val mealSlot: MealSlot,
    val dishId: String?,
    val variantId: String?,
    val title: String,
    val normalizedTitle: String,
    val notes: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
)
