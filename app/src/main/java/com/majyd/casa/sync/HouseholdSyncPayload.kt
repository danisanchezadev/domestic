package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PersonSyncPayload(
    val id: String,
    val householdId: String,
    val name: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String = "",
)

@Serializable
data class GroupSyncPayload(
    val id: String,
    val householdId: String,
    val name: String,
    val memberIds: List<String>,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String = "",
)

object HouseholdSyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(payload: PersonSyncPayload): String = json.encodeToString(payload)
    fun encode(payload: GroupSyncPayload): String = json.encodeToString(payload)
    fun decodePerson(payload: String): PersonSyncPayload? = runCatching { json.decodeFromString<PersonSyncPayload>(payload) }.getOrNull()
    fun decodeGroup(payload: String): GroupSyncPayload? = runCatching { json.decodeFromString<GroupSyncPayload>(payload) }.getOrNull()
}
