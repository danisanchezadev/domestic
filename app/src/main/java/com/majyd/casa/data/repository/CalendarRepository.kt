package com.majyd.casa.data.repository

import com.majyd.casa.data.model.CalendarEventItem
import com.majyd.casa.data.model.CalendarPlace
import com.majyd.casa.data.model.CalendarEventException
import kotlinx.coroutines.flow.Flow

interface CalendarRepository {
    fun observeEvents(): Flow<List<CalendarEventItem>>
    fun observePlaces(): Flow<List<CalendarPlace>>
    fun observeExceptions(): Flow<List<CalendarEventException>>
    suspend fun ensureDefaults()
    suspend fun upsertEvent(
        eventId: String?,
        title: String,
        notes: String,
        location: String,
        startAt: Long,
        endAt: Long,
        allDay: Boolean,
        reminderDaysBefore: Int?,
        participantIds: Set<String> = emptySet(),
        recurrenceType: String = "NONE",
        recurrenceInterval: Int = 1,
        recurrenceWeekdays: Set<Int> = emptySet(),
        recurrenceUntil: Long? = null,
        recurrenceCustomUnit: String = "DAYS",
    ): CalendarEventItem
    suspend fun deleteEvent(eventId: String): CalendarEventItem?
    suspend fun deleteEventPermanently(eventId: String)
    suspend fun restoreEvent(eventId: String): CalendarEventItem?
    suspend fun archivePast()
    suspend fun saveException(exception: CalendarEventException): CalendarEventException
    suspend fun deleteException(seriesId: String, occurrenceDate: Long): CalendarEventException?
    suspend fun savePlace(placeId: String?, name: String): CalendarPlace?
    suspend fun deletePlace(placeId: String)
    suspend fun deletePlacePermanently(placeId: String)
}
