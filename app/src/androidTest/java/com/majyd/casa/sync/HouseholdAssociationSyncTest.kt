package com.majyd.casa.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.AuthorizedDeviceEntity
import com.majyd.casa.data.local.entities.DeviceIdentityEntity
import com.majyd.casa.data.model.TaskItem
import com.majyd.casa.data.repository.RoomDeviceRepository
import com.majyd.casa.data.repository.RoomHouseholdRepository
import com.majyd.casa.data.repository.RoomTaskRepository
import com.majyd.casa.notifications.NotificationScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HouseholdAssociationSyncTest {
    private lateinit var source: CasaDatabase
    private lateinit var target: CasaDatabase
    private lateinit var applier: HouseholdSyncApplier

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = newDatabase(context)
        target = newDatabase(context)

        val targetOperations = RoomSyncOperationRepository(target.syncOperationDao(), SyncChangeNotifier())
        val targetDeviceManager = DefaultDeviceManager(RoomDeviceRepository(target.deviceDao()))
        val targetTasks = RoomTaskRepository(
            database = target,
            taskDao = target.taskDao(),
            syncOperationRepository = targetOperations,
            deviceManager = targetDeviceManager,
            taskHistoryDao = target.taskHistoryDao(),
        )
        val targetHousehold = RoomHouseholdRepository(
            database = target,
            householdDao = target.householdDao(),
            deviceDao = target.deviceDao(),
            authorizedDeviceDao = target.authorizedDeviceDao(),
            taskDao = target.taskDao(),
            syncOperationRepository = targetOperations,
            taskRepository = targetTasks,
            notificationScheduler = NoOpScheduler,
        )
        applier = HouseholdSyncApplier(target, targetOperations, targetHousehold, targetTasks, NoOpScheduler, target.authorizedDeviceDao())
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun associationAndPeopleAreAppliedAcrossIndependentDatabasesWithoutChangingLocalIdentity() = runTest {
        val now = 100L
        target.deviceDao().upsert(DeviceIdentityEntity("device-b", HOUSEHOLD_ID, null, "Movil B", now, now))
        target.authorizedDeviceDao().upsert(AuthorizedDeviceEntity("device-a", HOUSEHOLD_ID, "Movil A", now))

        val person = PersonSyncPayload("person-a", HOUSEHOLD_ID, "Ana", false, now, now, 1L, "device-a")
        val personOperation = operation("person", person.id, HouseholdSyncPayloadCodec.encode(person), "device-a")
        assertTrue(applier.apply(personOperation))
        assertTrue(applier.apply(personOperation))

        val association = DevicePersonSyncPayload("device-a", HOUSEHOLD_ID, person.id, now + 1)
        val associationOperation = operation("device_person", association.deviceId, HouseholdSyncPayloadCodec.encode(association), "device-a", now + 1)
        assertTrue(applier.apply(associationOperation))
        assertTrue(applier.apply(associationOperation))

        assertEquals("Ana", target.householdDao().getPerson(person.id)?.name)
        assertEquals(person.id, target.authorizedDeviceDao().get("device-a")?.assignedPersonId)
        assertEquals(null, target.deviceDao().getDevice()?.assignedPersonId)
        assertEquals(1, target.householdDao().observePeople(HOUSEHOLD_ID).first().count { it.id == person.id })
    }

    private fun operation(
        entityType: String,
        entityId: String,
        payload: String,
        deviceId: String,
        updatedAt: Long = 100L,
    ) = SyncOperation("op-$entityType-$entityId-$updatedAt", HOUSEHOLD_ID, entityType, entityId, payload = payload, version = 1L, updatedAt = updatedAt, deviceId = deviceId, deleted = false, applied = false)

    private fun newDatabase(context: Context) = Room.inMemoryDatabaseBuilder(context, CasaDatabase::class.java).allowMainThreadQueries().build()

    private object NoOpScheduler : NotificationScheduler {
        override fun schedule(task: TaskItem) = Unit
        override fun cancel(taskId: String) = Unit
        override fun schedulePreparation(menuItemId: String, dishName: String, date: LocalDate, mealSlot: com.majyd.casa.data.model.MealSlot) = Unit
        override fun cancelPreparation(menuItemId: String) = Unit
    }

    companion object {
        private const val HOUSEHOLD_ID = "household-1"
    }
}
