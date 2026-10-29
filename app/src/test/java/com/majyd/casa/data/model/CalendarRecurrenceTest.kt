package com.majyd.casa.data.model

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRecurrenceTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun dailyRecurrence_isBoundedToVisibleRange() {
        val event = event(LocalDate.of(2026, 1, 1), CalendarRecurrenceType.DAILY)
        val occurrences = CalendarRecurrenceExpander.expand(listOf(event), LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 5), zone)

        assertEquals(listOf(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 5)), occurrences.map { it.occurrenceDate })
    }

    @Test
    fun weeklyRecurrence_supportsSeveralWeekdays() {
        val event = event(LocalDate.of(2026, 1, 5), CalendarRecurrenceType.WEEKLY).copy(recurrenceWeekdays = setOf(1, 4))
        val occurrences = CalendarRecurrenceExpander.expand(listOf(event), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 18), zone)

        assertEquals(4, occurrences.size)
        assertTrue(occurrences.all { it.occurrenceDate.dayOfWeek.value == 1 || it.occurrenceDate.dayOfWeek.value == 4 })
    }

    @Test
    fun exception_canCancelOneOccurrence() {
        val event = event(LocalDate.of(2026, 1, 1), CalendarRecurrenceType.DAILY)
        val exception = CalendarEventException(event.id, LocalDate.of(2026, 1, 3), "", "", "", 0, 0, false, emptySet(), deleted = true)

        val occurrences = CalendarRecurrenceExpander.expand(listOf(event), listOf(exception), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4), zone)

        assertEquals(3, occurrences.size)
        assertTrue(occurrences.none { it.occurrenceDate == exception.occurrenceDate })
    }

    private fun event(date: LocalDate, recurrence: CalendarRecurrenceType): CalendarEventItem {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return CalendarEventItem("series", "Evento", "evento", "", "", start, start + 3_600_000, false, null, false, start, start, 1, recurrenceType = recurrence)
    }
}
