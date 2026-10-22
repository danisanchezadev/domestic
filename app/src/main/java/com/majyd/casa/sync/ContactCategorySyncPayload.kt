package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ContactCategorySyncPayload(
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

object ContactCategorySyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(payload: ContactCategorySyncPayload): String = json.encodeToString(payload)
    fun decode(value: String): ContactCategorySyncPayload? = runCatching { json.decodeFromString<ContactCategorySyncPayload>(value) }.getOrNull()
}
