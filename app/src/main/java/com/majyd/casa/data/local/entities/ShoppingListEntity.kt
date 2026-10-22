package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val deleted: Boolean,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val updatedByDevice: String = "",
)

