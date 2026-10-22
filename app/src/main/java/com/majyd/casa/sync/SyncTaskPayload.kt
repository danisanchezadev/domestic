package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SyncTaskPayload(
    val id: String,
    val title: String,
    val normalizedTitle: String,
    val notes: String,
    val assignmentType: String,
    val personIds: List<String>,
    val groupId: String?,
    val dueDate: Long,
    val dueTimeMinutes: Int?,
    val reminderMinutesBefore: Int?,
    val completed: Boolean,
    val completedAt: Long?,
    val completedByPersonId: String?,
    val completedByGroupId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val deleted: Boolean,
    val recurrence: String = "NONE",
    val recurrenceSeriesId: String? = null,
)

object SyncTaskPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(payload: SyncTaskPayload): String = json.encodeToString(payload)
    fun decode(value: String): SyncTaskPayload? = runCatching {
        json.decodeFromString<SyncTaskPayload>(value)
    }.getOrNull()
}
