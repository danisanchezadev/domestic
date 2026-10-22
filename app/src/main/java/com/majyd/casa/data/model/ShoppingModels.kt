package com.majyd.casa.data.model

data class ShoppingList(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val deleted: Boolean,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ShoppingCategory(
    val id: String,
    val name: String,
    val normalizedName: String,
    val icon: String,
    val color: String?,
    val sortOrder: Int,
    val hidden: Boolean,
    val deleted: Boolean,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ShoppingProduct(
    val id: String,
    val listId: String,
    val name: String,
    val normalizedName: String,
    val categoryId: String?,
    val quantity: Double,
    val unit: String,
    val notes: String,
    val purchased: Boolean,
    val purchasedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val createdByDevice: String,
    val updatedByDevice: String,
    val deleted: Boolean,
    val version: Long,
    val imageBase64: String? = null,
)

data class ShoppingSuggestion(
    val normalizedName: String,
    val displayName: String,
    val categoryId: String?,
    val preferredUnit: String,
    val preferredQuantity: Double?,
    val usageCount: Int,
    val lastUsedAt: Long?,
)
