package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WeeklyMenuItemSyncPayload(
    val id: String,
    val householdId: String,
    val dayOfWeek: Int,
    val mealSlot: String,
    val dishId: String? = null,
    val variantId: String? = null,
    val shoppingIngredientIds: List<String>? = null,
    val title: String,
    val normalizedTitle: String,
    val notes: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)

object WeeklyMenuSyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(payload: WeeklyMenuItemSyncPayload): String = json.encodeToString(payload)
    fun decode(value: String): WeeklyMenuItemSyncPayload? = runCatching { json.decodeFromString<WeeklyMenuItemSyncPayload>(value) }.getOrNull()
}
