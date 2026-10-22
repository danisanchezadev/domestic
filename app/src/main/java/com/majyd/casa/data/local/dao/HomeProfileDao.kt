package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.HomeProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeProfileDao {
    @Query("SELECT * FROM home_profile WHERE profileId = :profileId LIMIT 1")
    fun observeProfile(profileId: String): Flow<HomeProfileEntity?>

    @Query("SELECT * FROM home_profile WHERE profileId = :profileId LIMIT 1")
    suspend fun getProfile(profileId: String): HomeProfileEntity?

    @Upsert
    suspend fun upsert(profile: HomeProfileEntity)

    @Query(
        """
        UPDATE home_profile
        SET homeName = :homeName,
            personOneName = :personOneName,
            personTwoName = :personTwoName,
            updatedAt = :updatedAt
        WHERE profileId = :profileId
        """
    )
    suspend fun updateNames(
        profileId: String = HomeProfileEntity.DEFAULT_PROFILE_ID,
        homeName: String,
        personOneName: String,
        personTwoName: String,
        updatedAt: Long,
    )

    @Query("UPDATE home_profile SET lastSyncAt = :lastSyncAt, updatedAt = :updatedAt WHERE profileId = :profileId")
    suspend fun updateLastSync(
        profileId: String,
        lastSyncAt: Long?,
        updatedAt: Long,
    )
}
