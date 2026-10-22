package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_categories",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class ShoppingCategoryEntity(
    @PrimaryKey val id: String,
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

