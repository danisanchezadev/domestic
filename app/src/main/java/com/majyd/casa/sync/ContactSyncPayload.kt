package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ContactSyncPayload(
    val id: String,
    val householdId: String,
    val category: String,
    val name: String,
    val normalizedName: String,
    val phone: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)

object ContactSyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(payload: ContactSyncPayload): String = json.encodeToString(payload)
    fun decode(value: String): ContactSyncPayload? = runCatching { json.decodeFromString<ContactSyncPayload>(value) }.getOrNull()
}
