package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "calendar_event_exceptions",
    primaryKeys = ["seriesId", "occurrenceDate"],
    indices = [Index("seriesId"), Index("householdId")],
)
data class CalendarEventExceptionEntity(
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
