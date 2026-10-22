package com.majyd.casa.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.DeviceIdentityEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceDaoTest {
    private lateinit var database: CasaDatabase
    private lateinit var dao: DeviceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CasaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.deviceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsert_and_getDevice_returnsStoredDevice() = runTest {
        val entity = DeviceIdentityEntity(
            deviceId = "device-1",
            displayName = "Móvil principal",
            createdAt = 1L,
            updatedAt = 1L,
        )
        dao.upsert(entity)

        val stored = dao.getDevice()

        assertEquals(entity, stored)
    }
}

