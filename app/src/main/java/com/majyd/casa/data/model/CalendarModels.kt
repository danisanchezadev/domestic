package com.majyd.casa.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class CalendarEventType(val label: String) {
    APPOINTMENT("Cita"),
    FAMILY("Familia"),
    BILL("Factura"),
    REMINDER("Recordatorio"),
    OTHER("Otro"),
}

data class CalendarEventItem(
    val id: String,
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
)
