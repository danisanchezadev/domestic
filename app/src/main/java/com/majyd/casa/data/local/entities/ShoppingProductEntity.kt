package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_products",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ShoppingCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("listId"),
        Index("normalizedName"),
        Index("categoryId"),
    ],
)
data class ShoppingProductEntity(
    @PrimaryKey val id: String,
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
