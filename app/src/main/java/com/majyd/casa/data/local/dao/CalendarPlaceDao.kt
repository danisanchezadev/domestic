package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.CalendarPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarPlaceDao {
    @Query("SELECT * FROM calendar_places WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<CalendarPlaceEntity>>

    @Query("SELECT * FROM calendar_places WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CalendarPlaceEntity?

    @Query("SELECT * FROM calendar_places WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): CalendarPlaceEntity?

    @Query("DELETE FROM calendar_places WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Upsert
    suspend fun upsert(place: CalendarPlaceEntity)
}
