package com.majyd.casa.data.repository

import com.majyd.casa.data.model.HomeProfile
import kotlinx.coroutines.flow.Flow

interface HomeProfileRepository {
    fun observeProfile(): Flow<HomeProfile?>
    suspend fun ensureProfile(): HomeProfile
    suspend fun updateNames(homeName: String, personOneName: String, personTwoName: String): HomeProfile
    suspend fun updateLastSync(timestamp: Long?): HomeProfile
}

