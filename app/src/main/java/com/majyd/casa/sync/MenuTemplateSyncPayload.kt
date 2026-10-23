package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MenuTemplateSyncPayload(
    val id: String,
    val householdId: String,
    val name: String,
    val normalizedName: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)

@Serializable
data class MenuTemplateItemSyncPayload(
    val id: String,
    val templateId: String,
    val householdId: String,
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

object MenuTemplateSyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeTemplate(value: MenuTemplateSyncPayload): String = json.encodeToString(value)
    fun decodeTemplate(value: String): MenuTemplateSyncPayload? = runCatching {
        json.decodeFromString<MenuTemplateSyncPayload>(value)
    }.getOrNull()

    fun encodeItem(value: MenuTemplateItemSyncPayload): String = json.encodeToString(value)
    fun decodeItem(value: String): MenuTemplateItemSyncPayload? = runCatching {
        json.decodeFromString<MenuTemplateItemSyncPayload>(value)
    }.getOrNull()
}
