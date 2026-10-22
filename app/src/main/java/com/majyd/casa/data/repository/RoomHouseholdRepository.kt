package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.DeviceDao
import com.majyd.casa.data.local.dao.HouseholdDao
import com.majyd.casa.data.local.dao.TaskDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.HouseholdGroupEntity
import com.majyd.casa.data.local.entities.PersonEntity
import com.majyd.casa.data.model.HouseholdState
import com.majyd.casa.data.model.Person
import com.majyd.casa.data.model.HouseholdGroup
import com.majyd.casa.data.model.PersonDeletionResult
import com.majyd.casa.util.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.majyd.casa.notifications.NotificationScheduler
import com.majyd.casa.notifications.TaskNotificationPolicy
import com.majyd.casa.data.local.entities.GroupMemberEntity
import com.majyd.casa.sync.HouseholdSyncPayloadCodec
import com.majyd.casa.sync.GroupSyncPayload
import com.majyd.casa.sync.PersonSyncPayload
import com.majyd.casa.sync.SyncOperationRepository

@Singleton
class RoomHouseholdRepository @Inject constructor(
    private val database: CasaDatabase,
    private val householdDao: HouseholdDao,
    private val deviceDao: DeviceDao,
    private val taskDao: TaskDao,
    private val syncOperationRepository: SyncOperationRepository,
    private val taskRepository: TaskRepository,
    private val notificationScheduler: NotificationScheduler,
) : HouseholdRepository {
    override fun observeState(): Flow<HouseholdState> = combine(
        deviceDao.observeDevice(),
        householdDao.observeAllPeople(),
        householdDao.observeAllGroups(),
        householdDao.observeMembers(),
    ) { device, people, groups, members ->
        val householdId = device?.householdId ?: DEFAULT_HOUSEHOLD_ID
        HouseholdState(
            householdId = householdId,
            assignedPersonId = device?.assignedPersonId,
            people = people.filter { it.householdId == householdId }.map { it.toModel() },
            groups = groups.filter { it.householdId == householdId }.map { group ->
                HouseholdGroup(group.id, group.householdId, group.name, members.filter { it.groupId == group.id }.map { it.personId }.toSet(), group.updatedAt)
            },
        )
    }

    override suspend fun ensureDefaults(): HouseholdState {
        val device = deviceDao.getDevice() ?: return HouseholdState()
        val existing = householdDao.observePeople(device.householdId).first()
        if (existing.isEmpty()) {
            val now = System.currentTimeMillis()
            listOf(
                DEFAULT_PERSON_ONE_ID to "Persona 1",
                DEFAULT_PERSON_TWO_ID to "Persona 2",
            ).forEach { (id, name) ->
                val person = PersonEntity(id, device.householdId, name, Normalizer.normalizeText(name), false, now, now, 1)
                householdDao.upsertPerson(person)
                // Stable bootstrap records let a newly paired device receive the initial people.
                enqueuePerson(person, "CREATE")
            }
            return observeState().first { state ->
                state.householdId == device.householdId && state.people.isNotEmpty()
            }
        }
        return observeState().first()
    }

    override suspend fun addPerson(name: String): Person? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        val device = deviceDao.getDevice() ?: return null
        val now = System.currentTimeMillis()
        val entity = PersonEntity(UUID.randomUUID().toString(), device.householdId, clean, Normalizer.normalizeText(clean), false, now, now, 1)
        householdDao.upsertPerson(entity)
        enqueuePerson(entity, "CREATE")
        return entity.toModel()
    }

    override suspend fun renamePerson(personId: String, name: String): Person? {
        val current = householdDao.getPerson(personId) ?: return null
        val clean = name.trim()
        if (clean.isBlank()) return current.toModel()
        val now = System.currentTimeMillis()
        database.withTransaction { householdDao.renamePerson(personId, clean, Normalizer.normalizeText(clean), now) }
        val updated = current.copy(name = clean, normalizedName = Normalizer.normalizeText(clean), updatedAt = now, version = current.version + 1)
        enqueuePerson(updated, "UPDATE")
        return updated.toModel()
    }

    override suspend fun deletePerson(personId: String): PersonDeletionResult {
        val activeTasks = taskDao.countActiveTasksForPerson(personId)
        if (activeTasks > 0) return PersonDeletionResult(deleted = false, activeTaskCount = activeTasks)
        val device = deviceDao.getDevice() ?: return PersonDeletionResult(false, 0)
        val current = householdDao.getPerson(personId) ?: return PersonDeletionResult(false, 0)
        val now = System.currentTimeMillis()
        val affectedGroups = householdDao.getGroupsForMember(personId)
        val updatedGroups = affectedGroups.map { it.copy(updatedAt = now, version = it.version + 1) }
        database.withTransaction {
            householdDao.deletePerson(personId, now)
            householdDao.clearPersonMemberships(personId)
            if (device.assignedPersonId == personId) {
                deviceDao.assignPerson(device.deviceId, null, now)
            }
            updatedGroups.forEach { householdDao.upsertGroup(it) }
        }
        enqueuePerson(current.copy(deleted = true, updatedAt = now, version = current.version + 1), "DELETE")
        updatedGroups.forEach { group ->
            enqueueGroup(group, householdDao.getMembers(group.id).map { it.personId }, "UPDATE")
            refreshGroupNotifications(group.id)
        }
        return PersonDeletionResult(deleted = true, activeTaskCount = 0)
    }

    override suspend fun resolvePersonTasks(personId: String, replacementPersonId: String?): PersonDeletionResult {
        val activeTasks = taskDao.getActiveTasksForPerson(personId)
        val now = System.currentTimeMillis()
        database.withTransaction {
            activeTasks.forEach { task ->
                val remaining = taskDao.getPersonAssignments(task.id).map { it.personId }.filterNot { it == personId }.toMutableSet()
                replacementPersonId?.let { remaining += it }
                taskDao.clearPersonAssignments(task.id)
                remaining.forEach { taskDao.upsertPersonAssignment(com.majyd.casa.data.local.entities.TaskPersonAssignmentEntity(task.id, it, now)) }
                val assignmentType = when (remaining.size) {
                    0 -> "NONE"
                    1 -> "PERSON"
                    else -> "MULTIPLE_PEOPLE"
                }
                val legacyAssignee = when (remaining.size) {
                    0 -> com.majyd.casa.data.model.TaskAssignee.UNASSIGNED.name
                    1 -> com.majyd.casa.data.model.TaskAssignee.PERSON_ONE.name
                    else -> com.majyd.casa.data.model.TaskAssignee.BOTH.name
                }
                taskDao.upsert(
                    task.copy(
                        assignee = legacyAssignee,
                        assignmentType = assignmentType,
                        updatedAt = now,
                        version = task.version + 1,
                    )
                )
                val updated = task.copy(
                    assignee = legacyAssignee,
                    assignmentType = assignmentType,
                    updatedAt = now,
                    version = task.version + 1,
                )
                syncOperationRepository.enqueue(
                    householdId = deviceDao.getDevice()?.householdId ?: DEFAULT_HOUSEHOLD_ID,
                    deviceId = deviceDao.getDevice()?.deviceId ?: return@forEach,
                    entityType = "task",
                    entityId = updated.id,
                    payload = com.majyd.casa.sync.SyncTaskPayloadCodec.encode(
                        com.majyd.casa.sync.SyncTaskPayload(
                            id = updated.id,
                            title = updated.title,
                            normalizedTitle = updated.normalizedTitle,
                            notes = updated.notes,
                            assignmentType = updated.assignmentType,
                            personIds = remaining.toList(),
                            groupId = taskDao.getGroupAssignments(updated.id).firstOrNull()?.groupId,
                            dueDate = updated.dueDate,
                            dueTimeMinutes = updated.dueTimeMinutes,
                            reminderMinutesBefore = updated.reminderMinutesBefore,
                            completed = updated.completed,
                            completedAt = updated.completedAt,
                            completedByPersonId = updated.completedByPersonId,
                            createdAt = updated.createdAt,
                            updatedAt = updated.updatedAt,
                            version = updated.version,
                            deleted = updated.deleted,
                        ),
                    ),
                    version = updated.version,
                    updatedAt = updated.updatedAt,
                    deleted = updated.deleted,
                    operationType = "UPDATE",
                )
            }
        }
        return deletePerson(personId)
    }

    override suspend fun addGroup(name: String, memberIds: Set<String>): HouseholdGroup? {
        val clean = name.trim()
        val device = deviceDao.getDevice() ?: return null
        if (clean.isBlank()) return null
        val now = System.currentTimeMillis()
        val entity = HouseholdGroupEntity(UUID.randomUUID().toString(), device.householdId, clean, Normalizer.normalizeText(clean), false, now, now, 1)
        database.withTransaction {
            householdDao.upsertGroup(entity)
            replaceMembers(entity.id, memberIds, now)
        }
        enqueueGroup(entity, memberIds, "CREATE")
        return HouseholdGroup(entity.id, entity.householdId, entity.name, memberIds, now)
    }

    override suspend fun renameGroup(groupId: String, name: String): HouseholdGroup? {
        val current = householdDao.getGroup(groupId) ?: return null
        val clean = name.trim()
        if (clean.isBlank()) return null
        val now = System.currentTimeMillis()
        householdDao.renameGroup(groupId, clean, Normalizer.normalizeText(clean), now)
        val updated = current.copy(name = clean, normalizedName = Normalizer.normalizeText(clean), updatedAt = now, version = current.version + 1)
        householdDao.upsertGroup(updated)
        enqueueGroup(updated, householdDao.getMembers(groupId).map { it.personId }, "UPDATE")
        return HouseholdGroup(current.id, current.householdId, clean, householdDao.getMembers(groupId).map { it.personId }.toSet(), now)
    }

    override suspend fun setGroupMembers(groupId: String, memberIds: Set<String>): HouseholdGroup? {
        val current = householdDao.getGroup(groupId) ?: return null
        val now = System.currentTimeMillis()
        val updated = current.copy(updatedAt = now, version = current.version + 1)
        database.withTransaction {
            householdDao.upsertGroup(updated)
            replaceMembers(groupId, memberIds, now)
        }
        enqueueGroup(updated, memberIds, "UPDATE")
        refreshGroupNotifications(groupId)
        return HouseholdGroup(current.id, current.householdId, current.name, memberIds, now)
    }

    override suspend fun deleteGroup(groupId: String): HouseholdGroup? {
        val current = householdDao.getGroup(groupId) ?: return null
        val now = System.currentTimeMillis()
        val memberIds = householdDao.getMembers(groupId).map { it.personId }.toSet()
        householdDao.deleteGroup(groupId, now)
        enqueueGroup(current.copy(deleted = true, updatedAt = now, version = current.version + 1), memberIds, "DELETE")
        refreshGroupNotifications(groupId)
        return HouseholdGroup(current.id, current.householdId, current.name, memberIds, now)
    }

    private suspend fun refreshGroupNotifications(groupId: String) {
        val state = observeState().first()
        taskRepository.observeTasks().first()
            .filter { it.assignmentType == com.majyd.casa.data.model.TaskAssignmentType.GROUP && it.groupId == groupId }
            .forEach { task ->
                if (task.deleted || task.completed || !TaskNotificationPolicy.isRelevant(task, state)) {
                    notificationScheduler.cancel(task.id)
                } else {
                    notificationScheduler.schedule(task)
                }
            }
    }

    private suspend fun replaceMembers(groupId: String, memberIds: Set<String>, now: Long) {
        householdDao.clearMembers(groupId)
        memberIds.forEach { householdDao.upsertMember(GroupMemberEntity(groupId, it, now)) }
    }

    private suspend fun enqueuePerson(entity: PersonEntity, operationType: String) {
        val device = deviceDao.getDevice() ?: return
        syncOperationRepository.enqueue(device.householdId, device.deviceId, "person", entity.id, HouseholdSyncPayloadCodec.encode(PersonSyncPayload(entity.id, entity.householdId, entity.name, entity.deleted, entity.createdAt, entity.updatedAt, entity.version)), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private suspend fun enqueueGroup(entity: HouseholdGroupEntity, memberIds: Collection<String>, operationType: String) {
        val device = deviceDao.getDevice() ?: return
        syncOperationRepository.enqueue(device.householdId, device.deviceId, "group", entity.id, HouseholdSyncPayloadCodec.encode(GroupSyncPayload(entity.id, entity.householdId, entity.name, memberIds.toList(), entity.deleted, entity.createdAt, entity.updatedAt, entity.version)), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private fun PersonEntity.toModel() = Person(id, householdId, name, updatedAt)
    companion object {
        const val DEFAULT_HOUSEHOLD_ID = "household-default"
        private const val DEFAULT_PERSON_ONE_ID = "person-default-1"
        private const val DEFAULT_PERSON_TWO_ID = "person-default-2"
    }
}
