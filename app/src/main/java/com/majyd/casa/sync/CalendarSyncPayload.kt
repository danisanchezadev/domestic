package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CalendarEventSyncPayload(
    val id: String,
    val householdId: String,
    val title: String,
    val normalizedTitle: String,
    val notes: String,
    val location: String,
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean,
    val reminderDaysBefore: Int?,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
    val participantIdsJson: String = "[]",
    val recurrenceType: String = "NONE",
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdays: String = "",
    val recurrenceUntil: Long? = null,
    val recurrenceCustomUnit: String = "DAYS",
)

@Serializable
data class CalendarPlaceSyncPayload(
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
data class CalendarExceptionSyncPayload(
    val seriesId: String,
    val occurrenceDate: Long,
    val householdId: String,
    val title: String,
    val notes: String,
    val location: String,
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean,
    val participantIdsJson: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)

object CalendarSyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(payload: CalendarEventSyncPayload): String = json.encodeToString(payload)
    fun encode(payload: CalendarPlaceSyncPayload): String = json.encodeToString(payload)
    fun encode(payload: CalendarExceptionSyncPayload): String = json.encodeToString(payload)
    fun decodeEvent(value: String): CalendarEventSyncPayload? = runCatching { json.decodeFromString<CalendarEventSyncPayload>(value) }.getOrNull()
    fun decodePlace(value: String): CalendarPlaceSyncPayload? = runCatching { json.decodeFromString<CalendarPlaceSyncPayload>(value) }.getOrNull()
    fun decodeException(value: String): CalendarExceptionSyncPayload? = runCatching { json.decodeFromString<CalendarExceptionSyncPayload>(value) }.getOrNull()
}
