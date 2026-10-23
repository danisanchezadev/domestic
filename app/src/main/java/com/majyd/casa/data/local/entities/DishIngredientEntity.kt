package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dish_ingredients",
    indices = [Index(value = ["dishId"]), Index(value = ["normalizedName"])],
)
data class DishIngredientEntity(
    @PrimaryKey val id: String,
    val dishId: String,
    val name: String,
    val normalizedName: String,
    val quantity: Double?,
    val unit: String,
    val sortOrder: Int,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
    val variantId: String? = null,
)
