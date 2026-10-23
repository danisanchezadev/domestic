package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "menu_template_items",
    primaryKeys = ["templateId", "dayOfWeek", "mealSlot"],
    indices = [Index("templateId"), Index("dishId"), Index("variantId")],
)
data class MenuTemplateItemEntity(
    val templateId: String,
    val dayOfWeek: Int,
    val mealSlot: String,
    val dishId: String?,
    val variantId: String?,
    val title: String,
    val notes: String,
    val deleted: Boolean,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)
