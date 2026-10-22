package com.majyd.casa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.model.CalendarEventItem
import com.majyd.casa.sync.DefaultDeviceManager
import com.majyd.casa.sync.RoomSyncOperationRepository
import com.majyd.casa.sync.SyncChangeNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCalendarRepositoryTest {
    private lateinit var database: CasaDatabase
    private lateinit var repository: RoomCalendarRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CasaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCalendarRepository(
            database = database,
            calendarDao = database.calendarDao(),
            placeDao = database.calendarPlaceDao(),
            deviceManager = DefaultDeviceManager(RoomDeviceRepository(database.deviceDao())),
            syncOperationRepository = RoomSyncOperationRepository(database.syncOperationDao(), SyncChangeNotifier()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ensureDefaults_doesNotSeedDemoEvents() = runTest {
        repository.ensureDefaults()

        val events = repository.observeEvents().first()

        assertTrue(events.isEmpty())
    }

    @Test
    fun upsertEvent_canCreateDeleteAndRestore() = runTest {
        val now = System.currentTimeMillis()
        val created = repository.upsertEvent(
            eventId = null,
            title = "Cita medica",
            notes = "Centro de salud",
            location = "Madrid",
            startAt = now + 86_400_000L,
            endAt = now + 86_400_000L + 3_600_000L,
            allDay = false,
            reminderDaysBefore = 1,
        )

        val deleted = repository.deleteEvent(created.id)
        val restored = repository.restoreEvent(created.id)

        assertEquals("Cita medica", created.title)
        assertTrue(deleted?.deleted == true)
        assertTrue(restored?.deleted == false)
    }
}
