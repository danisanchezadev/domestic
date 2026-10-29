package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dishes",
    indices = [
        Index(value = ["householdId"]),
        Index(value = ["normalizedName"]),
        Index(value = ["collection"]),
    ],
)
data class DishEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val normalizedName: String,
    val category: String,
    val mealType: String,
    val imageBase64: String?,
    val recipe: String,
    val recipeSteps: String,
    val preparationMinutes: Int?,
    val servings: Int?,
    val notes: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
    val collection: String = "MENU",
    val preparationRequired: Boolean = false,
)
