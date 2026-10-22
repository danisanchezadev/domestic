package com.majyd.casa.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.majyd.casa.data.model.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.random.Random

class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val preferencesKey = stringPreferencesKey("app_preferences_json")
    private val pairingCodeKey = stringPreferencesKey("sync_pairing_code")

    override val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        prefs[preferencesKey]
            ?.let { runCatching { json.decodeFromString<AppPreferences>(it) }.getOrNull() }
            ?: AppPreferences()
    }

    override suspend fun update(block: (AppPreferences) -> AppPreferences) {
        dataStore.edit { mutablePreferences ->
            val current = mutablePreferences[preferencesKey]
                ?.let { runCatching { json.decodeFromString<AppPreferences>(it) }.getOrNull() }
                ?: AppPreferences()
            mutablePreferences[preferencesKey] = json.encodeToString(block(current))
        }
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        update { it.copy(darkMode = enabled) }
    }

    override suspend fun getPairingCode(): String {
        var result = ""
        dataStore.edit { prefs ->
            result = prefs[pairingCodeKey] ?: Random.nextInt(100000, 1000000).toString().also { prefs[pairingCodeKey] = it }
        }
        return result
    }

    override suspend fun setPairingCode(code: String) {
        require(code.length == 6 && code.all(Char::isDigit)) { "El código debe tener 6 dígitos" }
        dataStore.edit { it[pairingCodeKey] = code }
    }
}
