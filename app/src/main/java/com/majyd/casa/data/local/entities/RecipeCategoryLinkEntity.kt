package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "recipe_category_links",
    primaryKeys = ["dishId", "categoryId"],
    indices = [Index(value = ["categoryId"])],
)
data class RecipeCategoryLinkEntity(
    val dishId: String,
    val categoryId: String,
    val deleted: Boolean,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)
