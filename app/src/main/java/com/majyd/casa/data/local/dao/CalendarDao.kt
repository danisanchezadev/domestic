package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_events ORDER BY deleted ASC, startAt ASC, updatedAt DESC")
    fun observeEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :eventId LIMIT 1")
    suspend fun getById(eventId: String): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE deleted = 0")
    suspend fun getActive(): List<CalendarEventEntity>

    @Upsert
    suspend fun upsert(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :eventId")
    suspend fun hardDelete(eventId: String)

    @Query("UPDATE calendar_events SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE id = :eventId")
    suspend fun softDelete(eventId: String, updatedAt: Long)

    @Query("UPDATE calendar_events SET deleted = 0, updatedAt = :updatedAt, version = version + 1 WHERE id = :eventId")
    suspend fun restore(eventId: String, updatedAt: Long)

    @Query("UPDATE calendar_events SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE deleted = 0 AND endAt < :olderThan")
    suspend fun archivePast(olderThan: Long, updatedAt: Long)
}
