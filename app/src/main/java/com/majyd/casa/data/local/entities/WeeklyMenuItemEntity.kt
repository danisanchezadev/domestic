package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "weekly_menu_items",
    indices = [
        Index(value = ["deleted"]),
        Index(value = ["dayOfWeek"]),
        Index(value = ["mealSlot"]),
        Index(value = ["normalizedTitle"]),
        Index(value = ["dishId"]),
        Index(value = ["variantId"]),
    ],
)
data class WeeklyMenuItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "") val householdId: String,
    val dayOfWeek: Int,
    val mealSlot: String,
    val dishId: String?,
    val variantId: String?,
    val title: String,
    val normalizedTitle: String,
    val notes: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)
