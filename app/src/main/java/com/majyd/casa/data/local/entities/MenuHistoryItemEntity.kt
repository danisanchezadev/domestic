package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "menu_history_items",
    indices = [Index("householdId"), Index("weekStartEpochDay"), Index("dishId")],
)
data class MenuHistoryItemEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val weekStartEpochDay: Long,
    val dayOfWeek: Int,
    val mealSlot: String,
    val dishId: String?,
    val variantId: String?,
    val title: String,
    val notes: String,
    val completed: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)
