package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "household_groups",
    indices = [Index(value = ["householdId"]), Index(value = ["normalizedName"])],
)
data class HouseholdGroupEntity(
    @androidx.room.PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val normalizedName: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String = "",
)
