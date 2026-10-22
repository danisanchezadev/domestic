package com.majyd.casa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.model.TaskItem
import com.majyd.casa.notifications.NotificationScheduler
import com.majyd.casa.sync.DefaultDeviceManager
import com.majyd.casa.sync.RoomSyncOperationRepository
import com.majyd.casa.sync.SyncChangeNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHouseholdRepositoryTest {
    private lateinit var database: CasaDatabase
    private lateinit var repository: RoomHouseholdRepository
    private lateinit var deviceManager: DefaultDeviceManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CasaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deviceManager =
            DefaultDeviceManager(RoomDeviceRepository(database.deviceDao()))
        val operationRepository =
            RoomSyncOperationRepository(database.syncOperationDao(), SyncChangeNotifier())
        val taskRepository = RoomTaskRepository(
            database = database,
            taskDao = database.taskDao(),
            syncOperationRepository = operationRepository,
            deviceManager = deviceManager,
            taskHistoryDao = database.taskHistoryDao(),
        )
        repository = RoomHouseholdRepository(
            database = database,
            householdDao = database.householdDao(),
            deviceDao = database.deviceDao(),
            taskDao = database.taskDao(),
            syncOperationRepository = operationRepository,
            taskRepository = taskRepository,
            notificationScheduler = RecordingScheduler(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingPersonRemovesGroupMembershipAndSyncsGroupChange() = runTest {
        deviceManager.ensureDevice()
        val state = repository.ensureDefaults()
        val group = repository.addGroup("Adultos", setOf(state.people.first().id))!!

        val result = repository.deletePerson(state.people.first().id)
        val updated = repository.observeState().first { household ->
            household.groups.any { it.id == group.id }
        }
        val operations = RoomSyncOperationRepository(database.syncOperationDao(), SyncChangeNotifier())
            .observeAll(state.householdId).first()

        assertTrue(result.deleted)
        assertFalse(updated.people.any { it.id == state.people.first().id })
        assertTrue(updated.groups.first { it.id == group.id }.memberIds.isEmpty())
        assertTrue(operations.any { it.entityType == "group" && it.entityId == group.id && it.operationType == "UPDATE" })
    }

    @Test
    fun addingPeople_hasNoArtificialTwoPersonLimit() = runTest {
        deviceManager.ensureDevice()
        repository.ensureDefaults()

        repeat(3) { index -> repository.addPerson("Persona extra $index") }

        assertTrue(repository.observeState().first().people.size >= 5)
    }

    @Test
    fun deletingAssignedPerson_clearsDeviceAssociation() = runTest {
        val device = deviceManager.ensureDevice()
        val state = repository.ensureDefaults()
        deviceManager.assignPerson(state.people.first().id)

        assertTrue(repository.deletePerson(state.people.first().id).deleted)
        val updatedDevice = database.deviceDao().getDevice()
        assertTrue(updatedDevice?.assignedPersonId == null)
        assertTrue(updatedDevice?.deviceId == device.deviceId)
    }

    private class RecordingScheduler : NotificationScheduler {
        override fun schedule(task: TaskItem) = Unit
        override fun cancel(taskId: String) = Unit
    }
}
