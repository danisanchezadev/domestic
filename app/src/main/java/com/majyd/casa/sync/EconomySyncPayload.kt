package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EconomyCategorySyncPayload(val id: String, val householdId: String, val type: String, val name: String, val normalizedName: String, val deleted: Boolean, val createdAt: Long, val updatedAt: Long, val version: Long, val updatedByDevice: String)
@Serializable
data class EconomyMovementSyncPayload(val id: String, val householdId: String, val type: String, val amountCents: Long, val categoryId: String, val dateEpochDay: Long, val deleted: Boolean, val createdAt: Long, val updatedAt: Long, val version: Long, val updatedByDevice: String)
@Serializable
data class EconomyTargetSyncPayload(val householdId: String, val monthKey: String, val targetCents: Long, val minimumCents: Long, val createdAt: Long, val updatedAt: Long, val version: Long, val updatedByDevice: String)
@Serializable
data class EconomySettingsSyncPayload(val householdId: String, val fiscalStartMonth: Int, val currency: String, val targetCents: Long = 0L, val minimumCents: Long = 0L, val updatedAt: Long, val version: Long, val updatedByDevice: String)

object EconomySyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(value: EconomyCategorySyncPayload) = json.encodeToString(value)
    fun decodeCategory(value: String) = runCatching { json.decodeFromString<EconomyCategorySyncPayload>(value) }.getOrNull()
    fun encode(value: EconomyMovementSyncPayload) = json.encodeToString(value)
    fun decodeMovement(value: String) = runCatching { json.decodeFromString<EconomyMovementSyncPayload>(value) }.getOrNull()
    fun encode(value: EconomyTargetSyncPayload) = json.encodeToString(value)
    fun decodeTarget(value: String) = runCatching { json.decodeFromString<EconomyTargetSyncPayload>(value) }.getOrNull()
    fun encode(value: EconomySettingsSyncPayload) = json.encodeToString(value)
    fun decodeSettings(value: String) = runCatching { json.decodeFromString<EconomySettingsSyncPayload>(value) }.getOrNull()
}
