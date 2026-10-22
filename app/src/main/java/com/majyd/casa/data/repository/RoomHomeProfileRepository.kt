package com.majyd.casa.data.repository

import com.majyd.casa.data.local.dao.HomeProfileDao
import com.majyd.casa.data.local.entities.HomeProfileEntity
import com.majyd.casa.data.model.HomeProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomHomeProfileRepository @Inject constructor(
    private val homeProfileDao: HomeProfileDao,
) : HomeProfileRepository {

    override fun observeProfile(): Flow<HomeProfile?> {
        return homeProfileDao.observeProfile(HomeProfileEntity.DEFAULT_PROFILE_ID).map { entity -> entity?.toModel() }
    }

    override suspend fun ensureProfile(): HomeProfile {
        val existing = homeProfileDao.getProfile(HomeProfileEntity.DEFAULT_PROFILE_ID)
        if (existing != null) return existing.toModel()

        val now = System.currentTimeMillis()
        val profile = HomeProfileEntity(
            homeName = "Casa",
            personOneName = "Persona 1",
            personTwoName = "Persona 2",
            lastSyncAt = null,
            createdAt = now,
            updatedAt = now,
        )
        homeProfileDao.upsert(profile)
        return profile.toModel()
    }

    override suspend fun updateNames(
        homeName: String,
        personOneName: String,
        personTwoName: String,
    ): HomeProfile {
        val current = homeProfileDao.getProfile(HomeProfileEntity.DEFAULT_PROFILE_ID)
        val now = System.currentTimeMillis()
        homeProfileDao.updateNames(
            profileId = HomeProfileEntity.DEFAULT_PROFILE_ID,
            homeName = homeName,
            personOneName = personOneName,
            personTwoName = personTwoName,
            updatedAt = now,
        )
        return if (current != null) {
            current.toModel().copy(
                homeName = homeName,
                personOneName = personOneName,
                personTwoName = personTwoName,
                updatedAt = now,
            )
        } else {
            HomeProfile(
                profileId = HomeProfileEntity.DEFAULT_PROFILE_ID,
                homeName = homeName,
                personOneName = personOneName,
                personTwoName = personTwoName,
                lastSyncAt = null,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    override suspend fun updateLastSync(timestamp: Long?): HomeProfile {
        val current = homeProfileDao.getProfile(HomeProfileEntity.DEFAULT_PROFILE_ID)
        val now = System.currentTimeMillis()
        homeProfileDao.updateLastSync(
            profileId = HomeProfileEntity.DEFAULT_PROFILE_ID,
            lastSyncAt = timestamp,
            updatedAt = now,
        )
        return if (current != null) {
            current.toModel().copy(lastSyncAt = timestamp, updatedAt = now)
        } else {
            HomeProfile(
                profileId = HomeProfileEntity.DEFAULT_PROFILE_ID,
                homeName = "Casa",
                personOneName = "Persona 1",
                personTwoName = "Persona 2",
                lastSyncAt = timestamp,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    private fun HomeProfileEntity.toModel(): HomeProfile = HomeProfile(
        profileId = profileId,
        homeName = homeName,
        personOneName = personOneName,
        personTwoName = personTwoName,
        lastSyncAt = lastSyncAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
