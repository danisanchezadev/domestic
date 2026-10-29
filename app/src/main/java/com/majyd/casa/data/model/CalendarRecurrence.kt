package com.majyd.casa.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

data class CalendarOccurrence(
    val source: CalendarEventItem,
    val occurrenceDate: LocalDate,
    val startAt: Long,
    val endAt: Long,
)

data class CalendarEventException(
    val seriesId: String,
    val occurrenceDate: LocalDate,
    val title: String,
    val notes: String,
    val location: String,
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean,
    val participantIds: Set<String>,
    val deleted: Boolean,
)

object CalendarRecurrenceExpander {
    fun expand(events: List<CalendarEventItem>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalendarOccurrence> =
        expand(events, emptyList(), from, to, zone)

    fun expand(events: List<CalendarEventItem>, exceptions: List<CalendarEventException>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalendarOccurrence> =
        events.asSequence().flatMap { event -> expandEvent(event, from, to, zone).asSequence() }
            .mapNotNull { occurrence ->
                val exception = exceptions.firstOrNull { it.seriesId == occurrence.source.id && it.occurrenceDate == occurrence.occurrenceDate }
                when {
                    exception == null -> occurrence
                    exception.deleted -> null
                    else -> occurrence.copy(startAt = exception.startAt, endAt = exception.endAt, source = occurrence.source.copy(title = exception.title, notes = exception.notes, location = exception.location, allDay = exception.allDay, participantIds = exception.participantIds))
                }
            }
            .sortedWith(compareBy<CalendarOccurrence> { it.occurrenceDate }.thenBy { it.startAt }.thenBy { it.source.title })
            .toList()

    private fun expandEvent(event: CalendarEventItem, from: LocalDate, to: LocalDate, zone: ZoneId): List<CalendarOccurrence> {
        val start = java.time.Instant.ofEpochMilli(event.startAt).atZone(zone)
        val baseDate = start.toLocalDate()
        val baseTime = start.toLocalTime()
        val duration = (event.endAt - event.startAt).coerceAtLeast(0L)
        val last = event.recurrenceUntil?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val result = mutableListOf<CalendarOccurrence>()
        var date = if (event.recurrenceType == CalendarRecurrenceType.NONE) baseDate else from
        while (!date.isAfter(to)) {
            if (date.isBefore(baseDate) || (last != null && date.isAfter(last))) {
                date = date.plusDays(1)
                continue
            }
            if (matches(event, baseDate, date)) {
                val occurrenceStart = date.atTime(baseTime).atZone(zone).toInstant().toEpochMilli()
                result += CalendarOccurrence(event, date, occurrenceStart, occurrenceStart + duration)
            }
            if (event.recurrenceType == CalendarRecurrenceType.NONE) break
            date = date.plusDays(1)
        }
        return result
    }

    private fun matches(event: CalendarEventItem, base: LocalDate, date: LocalDate): Boolean = when (event.recurrenceType) {
        CalendarRecurrenceType.NONE -> date == base
        CalendarRecurrenceType.DAILY -> java.time.temporal.ChronoUnit.DAYS.between(base, date) % event.recurrenceInterval == 0L
        CalendarRecurrenceType.WEEKLY -> {
            val weeks = java.time.temporal.ChronoUnit.WEEKS.between(base, date)
            weeks >= 0 && weeks % event.recurrenceInterval == 0L && (event.recurrenceWeekdays.ifEmpty { setOf(base.dayOfWeek.value) }).contains(date.dayOfWeek.value)
        }
        CalendarRecurrenceType.MONTHLY -> {
            val months = java.time.temporal.ChronoUnit.MONTHS.between(base.withDayOfMonth(1), date.withDayOfMonth(1))
            months >= 0 && months % event.recurrenceInterval == 0L && date.dayOfMonth == base.dayOfMonth
        }
        CalendarRecurrenceType.YEARLY -> {
            val years = java.time.temporal.ChronoUnit.YEARS.between(base.withDayOfYear(1), date.withDayOfYear(1))
            years >= 0 && years % event.recurrenceInterval == 0L && date.month == base.month && date.dayOfMonth == base.dayOfMonth
        }
        CalendarRecurrenceType.CUSTOM -> when (event.recurrenceCustomUnit) {
            "WEEKS" -> {
                val weeks = java.time.temporal.ChronoUnit.WEEKS.between(base, date)
                weeks >= 0 && weeks % event.recurrenceInterval == 0L
            }
            "MONTHS" -> {
                val months = java.time.temporal.ChronoUnit.MONTHS.between(base.withDayOfMonth(1), date.withDayOfMonth(1))
                months >= 0 && months % event.recurrenceInterval == 0L && date.dayOfMonth == base.dayOfMonth
            }
            "YEARS" -> {
                val years = java.time.temporal.ChronoUnit.YEARS.between(base.withDayOfYear(1), date.withDayOfYear(1))
                years >= 0 && years % event.recurrenceInterval == 0L && date.month == base.month && date.dayOfMonth == base.dayOfMonth
            }
            else -> java.time.temporal.ChronoUnit.DAYS.between(base, date) % event.recurrenceInterval == 0L
        }
    }
}
