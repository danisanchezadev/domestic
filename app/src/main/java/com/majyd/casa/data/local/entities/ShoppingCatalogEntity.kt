package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_catalog",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class ShoppingCatalogEntity(
    @PrimaryKey val id: String,
    val normalizedName: String,
    val displayName: String,
    val categoryId: String?,
    val preferredUnit: String,
    val preferredQuantity: Double?,
    val usageCount: Int,
    val lastUsedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val updatedByDevice: String = "",
)

