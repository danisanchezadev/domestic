package com.majyd.casa.data.model

data class Dish(
    val id: String,
    val name: String,
    val category: String,
    val mealType: DishMealType,
    val imageBase64: String?,
    val recipe: String,
    val recipeSteps: String,
    val preparationMinutes: Int?,
    val servings: Int?,
    val notes: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
)

data class DishIngredient(
    val id: String,
    val dishId: String,
    val name: String,
    val quantity: Double?,
    val unit: String,
    val sortOrder: Int,
    val deleted: Boolean,
    val variantId: String? = null,
)

data class DishVariant(
    val id: String,
    val dishId: String,
    val name: String,
    val recipe: String,
    val notes: String,
    val deleted: Boolean,
)

data class DishUsageStats(
    val assignmentCount: Int = 0,
    val lastAssignedAt: Long? = null,
)

enum class DishMealType {
    LUNCH,
    DINNER,
    BOTH,
}
