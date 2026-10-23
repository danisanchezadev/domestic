package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dish_variants",
    indices = [Index("dishId"), Index("normalizedName")],
)
data class DishVariantEntity(
    @PrimaryKey val id: String,
    val dishId: String,
    val name: String,
    val normalizedName: String,
    val recipe: String,
    val notes: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)
