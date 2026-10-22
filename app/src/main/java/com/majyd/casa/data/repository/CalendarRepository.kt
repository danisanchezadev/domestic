package com.majyd.casa.data.repository

import com.majyd.casa.data.model.CalendarEventItem
import com.majyd.casa.data.model.CalendarPlace
import kotlinx.coroutines.flow.Flow

interface CalendarRepository {
    fun observeEvents(): Flow<List<CalendarEventItem>>
    fun observePlaces(): Flow<List<CalendarPlace>>
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
    ): CalendarEventItem
    suspend fun deleteEvent(eventId: String): CalendarEventItem?
    suspend fun deleteEventPermanently(eventId: String)
    suspend fun restoreEvent(eventId: String): CalendarEventItem?
    suspend fun archivePast()
    suspend fun savePlace(placeId: String?, name: String): CalendarPlace?
    suspend fun deletePlace(placeId: String)
    suspend fun deletePlacePermanently(placeId: String)
}
