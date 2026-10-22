package com.majyd.casa.sync

import com.majyd.casa.data.model.HouseholdGroup
import com.majyd.casa.data.model.HouseholdState
import com.majyd.casa.data.model.Person
import com.majyd.casa.data.model.TaskAssignmentType
import com.majyd.casa.data.model.TaskAssignee
import com.majyd.casa.data.model.TaskItem
import com.majyd.casa.data.model.TaskRecurrence
import com.majyd.casa.data.model.normalizeTaskAssignment
import com.majyd.casa.data.local.entities.AuthorizedDeviceEntity
import com.majyd.casa.data.model.nextOccurrence
import com.majyd.casa.data.model.occurrenceId
import com.majyd.casa.notifications.TaskNotificationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class SyncModelsTest {
    @Test
    fun syncAttempt_distinguishesChangesFromNoChanges() {
        val noChanges = SyncAttemptResult(SyncResult(0, 0, 0, 0), "phone-b", 100L, 150L)
        val withChanges = SyncAttemptResult(SyncResult(2, 2, 1, 1), "phone-b", 100L, 250L)

        assertEquals(SyncPhase.NO_CHANGES, noChanges.phase)
        assertEquals(SyncPhase.SUCCESS, withChanges.phase)
        assertEquals(50L, noChanges.durationMs)
        assertEquals(150L, withChanges.durationMs)
    }

    @Test
    fun shoppingPayloads_roundTripWithoutLosingPurchaseState() {
        val list = ShoppingListSyncPayload("list-1", "home-1", "Compra", 0, false, 2L, 10L, 20L)
        val product = ShoppingProductSyncPayload("product-1", "home-1", "list-1", "Leche", "leche", null, 2.0, "l", "", true, 30L, 10L, 20L, "device-1", "device-1", false, 2L)

        assertEquals(list, ShoppingSyncPayloadCodec.decodeList(ShoppingSyncPayloadCodec.encodeList(list)))
        assertEquals(product, ShoppingSyncPayloadCodec.decodeProduct(ShoppingSyncPayloadCodec.encodeProduct(product)))
    }

    @Test
    fun syncOperation_preservesCreatedAtForWireCompatibility() {
        val operation = SyncOperation(
            operationId = "op-1",
            householdId = "home-1",
            entityType = "task",
            entityId = "task-1",
            payload = "{}",
            version = 1L,
            createdAt = 10L,
            updatedAt = 20L,
            deviceId = "device-1",
            deleted = false,
            applied = false,
        )

        val decoded = Json.decodeFromString<SyncOperation>(Json.encodeToString(operation))
        assertEquals(10L, decoded.createdAt)
    }

    @Test
    fun payload_roundTripsAllTaskFields() {
        val payload = SyncTaskPayload(
            id = "task-1",
            title = "Limpiar cocina",
            normalizedTitle = "limpiar cocina",
            notes = "Notas",
            assignmentType = "GROUP",
            personIds = listOf("person-1"),
            groupId = "group-1",
            dueDate = 20_000L,
            dueTimeMinutes = 1_140,
            reminderMinutesBefore = 60,
            completed = false,
            completedAt = null,
            completedByPersonId = null,
            createdAt = 10L,
            updatedAt = 20L,
            version = 2L,
            deleted = false,
        )

        assertTrue(SyncTaskPayloadCodec.decode(SyncTaskPayloadCodec.encode(payload)) == payload)
    }

    @Test
    fun conflictResolver_prefersVersionThenDeviceId() {
        val resolver = DeterministicConflictResolver()
        assertTrue(resolver.incomingWins(1, 100, "a", 2, 1, "b"))
        assertFalse(resolver.incomingWins(2, 100, "b", 2, 100, "a"))
        assertTrue(resolver.incomingWins(2, 100, "a", 2, 100, "b"))
    }

    @Test
    fun notificationPolicy_includesGroupMemberAndExcludesUnassigned() {
        val person = Person("person-1", "home-1", "Daniel", 1L)
        val group = HouseholdGroup("group-1", "home-1", "Adultos", setOf(person.id), 1L)
        val household = HouseholdState("home-1", person.id, listOf(person), listOf(group))
        val base = TaskItem("task", "Tarea", "tarea", "", TaskAssignee.UNASSIGNED, false, false, 1L, 1L, 1L)

        assertTrue(TaskNotificationPolicy.isRelevant(base.copy(assignmentType = TaskAssignmentType.GROUP, groupId = group.id), household))
        assertFalse(TaskNotificationPolicy.isRelevant(base.copy(assignmentType = TaskAssignmentType.NONE), household))
        assertTrue(TaskNotificationPolicy.isRelevant(base.copy(assignmentType = TaskAssignmentType.EVERYONE), household))
    }

    @Test
    fun notificationPolicy_filtersPeopleAndForeignGroups() {
        val daniel = Person("person-1", "home-1", "Daniel", 1L)
        val laura = Person("person-2", "home-1", "Laura", 1L)
        val ownGroup = HouseholdGroup("group-1", "home-1", "Adultos", setOf(daniel.id), 1L)
        val foreignGroup = HouseholdGroup("group-2", "home-1", "Laura", setOf(laura.id), 1L)
        val household = HouseholdState("home-1", daniel.id, listOf(daniel, laura), listOf(ownGroup, foreignGroup))
        val base = TaskItem("task", "Tarea", "tarea", "", TaskAssignee.UNASSIGNED, false, false, 1L, 1L, 1L)

        assertTrue(TaskNotificationPolicy.isRelevant(base.copy(assignmentType = TaskAssignmentType.PERSON, personIds = setOf(daniel.id)), household))
        assertTrue(TaskNotificationPolicy.isRelevant(base.copy(assignmentType = TaskAssignmentType.MULTIPLE_PEOPLE, personIds = setOf(laura.id, daniel.id)), household))
        assertFalse(TaskNotificationPolicy.isRelevant(base.copy(assignmentType = TaskAssignmentType.PERSON, personIds = setOf(laura.id)), household))
        assertFalse(TaskNotificationPolicy.isRelevant(base.copy(assignmentType = TaskAssignmentType.GROUP, groupId = foreignGroup.id), household))
    }

    @Test
    fun notificationPolicy_includesEveryoneForEitherPerson() {
        val daniel = Person("person-1", "home-1", "Daniel", 1L)
        val laura = Person("person-2", "home-1", "Laura", 1L)
        val base = TaskItem("task", "Tarea", "tarea", "", TaskAssignee.UNASSIGNED, false, false, 1L, 1L, 1L)

        assertTrue(TaskNotificationPolicy.isRelevant(base.copy(assignmentType = TaskAssignmentType.EVERYONE), HouseholdState("home-1", daniel.id, listOf(daniel, laura), emptyList())))
        assertTrue(TaskNotificationPolicy.isRelevant(base.copy(assignmentType = TaskAssignmentType.EVERYONE), HouseholdState("home-1", laura.id, listOf(daniel, laura), emptyList())))
    }

    @Test
    fun recurrence_calculatesNextOccurrence() {
        val monday = java.time.LocalDate.of(2026, 9, 7).toEpochDay()

        assertEquals(java.time.LocalDate.of(2026, 9, 8).toEpochDay(), nextOccurrence(monday, TaskRecurrence.DAILY))
        assertEquals(java.time.LocalDate.of(2026, 9, 14).toEpochDay(), nextOccurrence(monday, TaskRecurrence.WEEKLY))
        assertEquals(java.time.LocalDate.of(2026, 10, 7).toEpochDay(), nextOccurrence(monday, TaskRecurrence.MONTHLY))
        assertEquals(java.time.LocalDate.of(2027, 9, 7).toEpochDay(), nextOccurrence(monday, TaskRecurrence.YEARLY))
        assertEquals("series-1:20707", occurrenceId("series-1", 20_707L))
    }

    @Test
    fun taskAssignment_normalizesIdsToItsDeclaredShape() {
        assertEquals(
            com.majyd.casa.data.model.NormalizedTaskAssignment(TaskAssignmentType.PERSON, setOf("person-a"), null),
            normalizeTaskAssignment(TaskAssignmentType.PERSON, setOf("person-b", "person-a"), "group-a"),
        )
        assertEquals(
            com.majyd.casa.data.model.NormalizedTaskAssignment(TaskAssignmentType.NONE, emptySet(), null),
            normalizeTaskAssignment(TaskAssignmentType.GROUP, emptySet(), ""),
        )
        assertEquals(
            com.majyd.casa.data.model.NormalizedTaskAssignment(TaskAssignmentType.EVERYONE, emptySet(), null),
            normalizeTaskAssignment(TaskAssignmentType.EVERYONE, setOf("person-a"), "group-a"),
        )
    }

    @Test
    fun authorizedDevicePolicy_blocksRevokedAndThirdDevices() {
        val active = AuthorizedDeviceEntity("device-2", "home-1", "Phone", 1L, revoked = false)
        val revoked = active.copy(revoked = true)

        assertTrue(AuthorizedDevicePolicy.canAccept(active, "home-1", activeDeviceCount = 2))
        assertFalse(AuthorizedDevicePolicy.canAccept(revoked, "home-1", activeDeviceCount = 1))
        assertTrue(AuthorizedDevicePolicy.canAccept(null, "home-1", activeDeviceCount = 1))
        assertFalse(AuthorizedDevicePolicy.canAccept(null, "home-1", activeDeviceCount = 2))
    }
}
