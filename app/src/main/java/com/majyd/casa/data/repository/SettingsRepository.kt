package com.majyd.casa.data.repository

import com.majyd.casa.data.model.AppPreferences
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val preferences: Flow<AppPreferences>
    suspend fun update(block: (AppPreferences) -> AppPreferences)
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun getPairingCode(): String
    suspend fun setPairingCode(code: String)
}
