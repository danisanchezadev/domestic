package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "economy_categories",
    indices = [Index(value = ["householdId"]), Index(value = ["householdId", "normalizedName", "type"], unique = true)],
)
data class EconomyCategoryEntity(
    @androidx.room.PrimaryKey val id: String,
    val householdId: String,
    val type: String,
    val name: String,
    val normalizedName: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)

@Entity(
    tableName = "economy_movements",
    indices = [Index(value = ["householdId"]), Index(value = ["dateEpochDay"]), Index(value = ["categoryId"])],
)
data class EconomyMovementEntity(
    @androidx.room.PrimaryKey val id: String,
    val householdId: String,
    val type: String,
    val amountCents: Long,
    val categoryId: String,
    val dateEpochDay: Long,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)

@Entity(
    tableName = "economy_monthly_targets",
    primaryKeys = ["householdId", "monthKey"],
    indices = [Index(value = ["householdId"])],
)
data class EconomyMonthlyTargetEntity(
    val householdId: String,
    val monthKey: String,
    val targetCents: Long,
    val minimumCents: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)

@Entity(tableName = "economy_settings")
data class EconomySettingsEntity(
    @androidx.room.PrimaryKey val householdId: String,
    val fiscalStartMonth: Int,
    val currency: String,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)
