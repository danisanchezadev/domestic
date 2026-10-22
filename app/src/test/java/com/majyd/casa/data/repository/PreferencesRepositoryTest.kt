package com.majyd.casa.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class PreferencesRepositoryTest {
    @Test
    fun update_persistsPreferences() = runTest {
        val tempFile = File.createTempFile("preferences", ".preferences_pb").apply {
            delete()
            deleteOnExit()
        }
        val store = PreferenceDataStoreFactory.create(
            produceFile = { tempFile }
        )
        val repository = PreferencesRepository(store)

        repository.update { it.copy(homeName = "Mi casa", domesticWifiSsid = "Casa-5G", lastSyncAt = 1234L) }

        assertEquals("Mi casa", repository.preferences.first().homeName)
        assertEquals("Casa-5G", repository.preferences.first().domesticWifiSsid)
        assertEquals(1234L, repository.preferences.first().lastSyncAt)
    }
}
