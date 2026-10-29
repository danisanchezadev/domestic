package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["deleted"]),
        Index(value = ["startAt"]),
        Index(value = ["normalizedTitle"]),
    ],
)
data class CalendarEventEntity(
    @PrimaryKey val id: String,
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
