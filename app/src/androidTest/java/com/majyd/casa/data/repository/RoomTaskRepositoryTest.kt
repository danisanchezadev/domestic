package com.majyd.casa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.model.TaskAssignee
import com.majyd.casa.data.model.TaskAssignmentType
import com.majyd.casa.sync.DefaultDeviceManager
import com.majyd.casa.sync.RoomSyncOperationRepository
import com.majyd.casa.sync.SyncChangeNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTaskRepositoryTest {
    private lateinit var database: CasaDatabase
    private lateinit var repository: RoomTaskRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CasaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomTaskRepository(
            database = database,
            taskDao = database.taskDao(),
            syncOperationRepository = RoomSyncOperationRepository(database.syncOperationDao(), SyncChangeNotifier()),
            deviceManager = DefaultDeviceManager(RoomDeviceRepository(database.deviceDao())),
            taskHistoryDao = database.taskHistoryDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ensureDefaults_doesNotSeedDemoTasks() = runTest {
        repository.ensureDefaults()

        val tasks = repository.observeTasks().first()

        assertTrue(tasks.isEmpty())
    }

    @Test
    fun upsertTask_canCreateCompleteAndDeleteTask() = runTest {
        val created = repository.upsertTask(
            taskId = null,
            title = "Sacar basura",
            notes = "Antes de cenar",
            assignee = TaskAssignee.PERSON_ONE,
            completed = false,
            dueDate = 20_000L,
        )

        val completed = repository.toggleCompleted(created.id, true)
        val deleted = repository.deleteTask(created.id)

        assertEquals("Sacar basura", created.title)
        assertTrue(completed?.completed == true)
        assertTrue(deleted?.deleted == true)
    }

    @Test
    fun upsertTask_persistsNormalizedAssignment() = runTest {
        val created = repository.upsertTask(
            taskId = null,
            title = "Revisar tareas",
            notes = "",
            assignee = TaskAssignee.UNASSIGNED,
            completed = false,
            assignmentType = TaskAssignmentType.PERSON,
            personIds = setOf("person-b", "person-a"),
            groupId = "group-ignored",
            dueDate = 20_000L,
        )

        assertEquals(setOf("person-a"), created.personIds)
        assertEquals(null, created.groupId)
        val stored = repository.observeTasks().first().first { it.id == created.id }

        assertEquals(TaskAssignmentType.PERSON, stored.assignmentType)
        assertEquals(setOf("person-a"), stored.personIds)
        assertEquals(null, stored.groupId)
    }

    @Test
    fun upsertTask_rejectsMissingDueDate() = runTest {
        try {
            repository.upsertTask(
                taskId = null,
                title = "Tarea sin fecha",
                notes = "",
                assignee = TaskAssignee.UNASSIGNED,
                completed = false,
                dueDate = 0L,
            )
            fail("Se esperaba una fecha límite obligatoria")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("fecha", ignoreCase = true))
        }
    }

    @Test
    fun postponeTask_preservesNormalizedAssignments() = runTest {
        val created = repository.upsertTask(
            taskId = null,
            title = "Tarea asignada",
            notes = "",
            assignee = TaskAssignee.PERSON_ONE,
            completed = false,
            dueDate = 20_000L,
            assignmentType = TaskAssignmentType.PERSON,
            personIds = setOf("person-a"),
        )

        val postponed = repository.postponeTask(created.id, 1L)

        assertEquals(setOf("person-a"), postponed?.personIds)
        assertEquals(TaskAssignmentType.PERSON, postponed?.assignmentType)
    }

    @Test
    fun upsertTask_supportsAllNormalizedAssignmentTypes() = runTest {
        val multiple = repository.upsertTask(
            taskId = null,
            title = "Tarea compartida",
            notes = "",
            assignee = TaskAssignee.UNASSIGNED,
            completed = false,
            dueDate = 20_000L,
            assignmentType = TaskAssignmentType.MULTIPLE_PEOPLE,
            personIds = setOf("person-b", "person-a"),
        )
        val group = repository.upsertTask(
            taskId = null,
            title = "Tarea del grupo",
            notes = "",
            assignee = TaskAssignee.UNASSIGNED,
            completed = false,
            dueDate = 20_000L,
            assignmentType = TaskAssignmentType.GROUP,
            groupId = "group-adults",
        )
        val everyone = repository.upsertTask(
            taskId = null,
            title = "Tarea de todos",
            notes = "",
            assignee = TaskAssignee.UNASSIGNED,
            completed = false,
            dueDate = 20_000L,
            assignmentType = TaskAssignmentType.EVERYONE,
        )
        val unassigned = repository.upsertTask(
            taskId = null,
            title = "Tarea sin asignar",
            notes = "",
            assignee = TaskAssignee.UNASSIGNED,
            completed = false,
            dueDate = 20_000L,
            assignmentType = TaskAssignmentType.NONE,
            personIds = setOf("ignored"),
            groupId = "ignored",
        )

        assertEquals(setOf("person-a", "person-b"), multiple.personIds)
        assertEquals(TaskAssignmentType.GROUP, group.assignmentType)
        assertEquals("group-adults", group.groupId)
        assertEquals(TaskAssignmentType.EVERYONE, everyone.assignmentType)
        assertEquals(TaskAssignmentType.NONE, unassigned.assignmentType)
        assertTrue(unassigned.personIds.isEmpty())
        assertEquals(null, unassigned.groupId)
    }
}
