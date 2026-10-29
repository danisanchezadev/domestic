package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.CalendarEventExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventExceptionDao {
    @Query("SELECT * FROM calendar_event_exceptions")
    fun observeAll(): Flow<List<CalendarEventExceptionEntity>>

    @Query("SELECT * FROM calendar_event_exceptions WHERE seriesId = :seriesId AND occurrenceDate = :occurrenceDate LIMIT 1")
    suspend fun get(seriesId: String, occurrenceDate: Long): CalendarEventExceptionEntity?

    @Upsert
    suspend fun upsert(exception: CalendarEventExceptionEntity)
}
