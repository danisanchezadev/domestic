package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MenuHistorySyncPayload(
    val id: String,
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

object MenuHistorySyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(value: MenuHistorySyncPayload): String = json.encodeToString(value)
    fun decode(value: String): MenuHistorySyncPayload? = runCatching {
        json.decodeFromString<MenuHistorySyncPayload>(value)
    }.getOrNull()
}
