package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.CalendarDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.CalendarEventEntity
import com.majyd.casa.data.local.entities.CalendarPlaceEntity
import com.majyd.casa.data.local.entities.CalendarEventExceptionEntity
import com.majyd.casa.data.model.CalendarPlace
import com.majyd.casa.data.model.CalendarEventItem
import com.majyd.casa.data.model.CalendarEventException
import com.majyd.casa.sync.CalendarEventSyncPayload
import com.majyd.casa.sync.CalendarSyncPayloadCodec
import com.majyd.casa.sync.CalendarPlaceSyncPayload
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.util.Normalizer
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomCalendarRepository @Inject constructor(
    private val database: CasaDatabase,
    private val calendarDao: CalendarDao,
    private val placeDao: com.majyd.casa.data.local.dao.CalendarPlaceDao,
    private val exceptionDao: com.majyd.casa.data.local.dao.CalendarEventExceptionDao,
    private val deviceManager: DeviceManager,
    private val syncOperationRepository: SyncOperationRepository,
) : CalendarRepository {

    override fun observeEvents(): Flow<List<CalendarEventItem>> =
        calendarDao.observeEvents().map { events -> events.map { it.toModel() } }

    override fun observePlaces(): Flow<List<CalendarPlace>> =
        placeDao.observeActive().map { places -> places.map(CalendarPlaceEntity::toModel) }

    override fun observeExceptions(): Flow<List<CalendarEventException>> = exceptionDao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun ensureDefaults() {
        // Events are user data; do not seed demo records in production.
    }

    override suspend fun upsertEvent(
        eventId: String?,
        title: String,
        notes: String,
        location: String,
        startAt: Long,
        endAt: Long,
        allDay: Boolean,
        reminderDaysBefore: Int?,
        participantIds: Set<String>,
        recurrenceType: String,
        recurrenceInterval: Int,
        recurrenceWeekdays: Set<Int>,
        recurrenceUntil: Long?,
        recurrenceCustomUnit: String,
    ): CalendarEventItem {
        return database.withTransaction {
            val device = deviceManager.ensureDevice()
            val current = eventId?.let { calendarDao.getById(it) }
            val now = System.currentTimeMillis()
            val entity = CalendarEventEntity(
                id = current?.id ?: UUID.randomUUID().toString(),
                householdId = device.householdId,
                title = title.trim(),
                normalizedTitle = Normalizer.normalizeText(title),
                notes = notes.trim(),
                location = location.trim(),
                startAt = startAt,
                endAt = endAt,
                allDay = allDay,
                reminderDaysBefore = reminderDaysBefore,
                deleted = false,
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                version = (current?.version ?: 0) + 1,
                updatedByDevice = device.deviceId,
                participantIdsJson = encodeIds(participantIds),
                recurrenceType = recurrenceType,
                recurrenceInterval = recurrenceInterval.coerceAtLeast(1),
                recurrenceWeekdays = recurrenceWeekdays.sorted().joinToString(","),
                recurrenceUntil = recurrenceUntil,
                recurrenceCustomUnit = recurrenceCustomUnit,
            )
            calendarDao.upsert(entity)
            enqueueEvent(entity, if (current == null) "CREATE" else "UPDATE")
            entity.toModel()
        }
    }

    override suspend fun deleteEvent(eventId: String): CalendarEventItem? {
        val current = calendarDao.getById(eventId) ?: return null
        return database.withTransaction {
            val device = deviceManager.ensureDevice()
            val now = System.currentTimeMillis()
            val updated = current.copy(deleted = true, updatedAt = now, version = current.version + 1, updatedByDevice = device.deviceId)
            calendarDao.upsert(updated)
            enqueueEvent(updated, "DELETE")
            updated.toModel()
        }
    }

    override suspend fun deleteEventPermanently(eventId: String) {
        database.withTransaction {
            val current = calendarDao.getById(eventId) ?: return@withTransaction
            val device = deviceManager.ensureDevice()
            val tombstone = current.copy(deleted = true, updatedAt = System.currentTimeMillis(), version = current.version + 1, updatedByDevice = device.deviceId)
            enqueueEvent(tombstone, "DELETE_PERMANENT")
            calendarDao.hardDelete(eventId)
        }
    }

    override suspend fun restoreEvent(eventId: String): CalendarEventItem? {
        val current = calendarDao.getById(eventId) ?: return null
        return database.withTransaction {
            val device = deviceManager.ensureDevice()
            val now = System.currentTimeMillis()
            val updated = current.copy(deleted = false, updatedAt = now, version = current.version + 1, updatedByDevice = device.deviceId)
            calendarDao.upsert(updated)
            enqueueEvent(updated, "RESTORE")
            updated.toModel()
        }
    }

    override suspend fun archivePast() {
        database.withTransaction {
            val device = deviceManager.ensureDevice()
            val now = System.currentTimeMillis()
            calendarDao.getActive().filter { it.endAt < now }.forEach { current ->
                val updated = current.copy(deleted = true, updatedAt = now, version = current.version + 1, updatedByDevice = device.deviceId)
                calendarDao.upsert(updated)
                enqueueEvent(updated, "ARCHIVE")
            }
        }
    }

    override suspend fun saveException(exception: CalendarEventException): CalendarEventException = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val current = exceptionDao.get(exception.seriesId, exception.occurrenceDate.toEpochDay())
        val now = System.currentTimeMillis()
        val entity = CalendarEventExceptionEntity(exception.seriesId, exception.occurrenceDate.toEpochDay(), device.householdId, exception.title, exception.notes, exception.location, exception.startAt, exception.endAt, exception.allDay, encodeIds(exception.participantIds), exception.deleted, current?.createdAt ?: now, now, (current?.version ?: 0) + 1, device.deviceId)
        exceptionDao.upsert(entity)
        enqueueException(entity, if (exception.deleted) "DELETE" else "UPDATE")
        exception
    }

    override suspend fun deleteException(seriesId: String, occurrenceDate: Long): CalendarEventException? = database.withTransaction {
        val current = exceptionDao.get(seriesId, java.time.Instant.ofEpochMilli(occurrenceDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()) ?: return@withTransaction null
        val device = deviceManager.ensureDevice()
        val updated = current.copy(deleted = true, updatedAt = System.currentTimeMillis(), version = current.version + 1, updatedByDevice = device.deviceId)
        exceptionDao.upsert(updated)
        enqueueException(updated, "DELETE")
        updated.toModel()
    }

    override suspend fun savePlace(placeId: String?, name: String): CalendarPlace? = database.withTransaction {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withTransaction null
        val device = deviceManager.ensureDevice()
        val current = if (placeId != null) placeDao.getById(placeId) else null
        val duplicate = placeDao.getByNormalizedName(Normalizer.normalizeText(cleanName))
        if (duplicate != null && duplicate.id != placeId) {
            if (!duplicate.deleted) return@withTransaction duplicate.toModel()
            val restored = duplicate.copy(name = cleanName, normalizedName = Normalizer.normalizeText(cleanName), deleted = false, updatedAt = System.currentTimeMillis(), version = duplicate.version + 1, updatedByDevice = device.deviceId)
            placeDao.upsert(restored)
            enqueuePlace(restored, "RESTORE")
            return@withTransaction restored.toModel()
        }
        val now = System.currentTimeMillis()
        val entity = CalendarPlaceEntity(current?.id ?: UUID.randomUUID().toString(), device.householdId, cleanName, Normalizer.normalizeText(cleanName), false, current?.createdAt ?: now, now, (current?.version ?: 0) + 1, device.deviceId)
        placeDao.upsert(entity)
        enqueuePlace(entity, if (current == null) "CREATE" else "UPDATE")
        entity.toModel()
    }

    override suspend fun deletePlace(placeId: String) = database.withTransaction {
        val current = placeDao.getById(placeId) ?: return@withTransaction
        val device = deviceManager.ensureDevice()
        val updated = current.copy(deleted = true, updatedAt = System.currentTimeMillis(), version = current.version + 1, updatedByDevice = device.deviceId)
        placeDao.upsert(updated)
        enqueuePlace(updated, "DELETE")
    }

    override suspend fun deletePlacePermanently(placeId: String) = database.withTransaction {
        val current = placeDao.getById(placeId) ?: return@withTransaction
        val device = deviceManager.ensureDevice()
        val tombstone = current.copy(deleted = true, updatedAt = System.currentTimeMillis(), version = current.version + 1, updatedByDevice = device.deviceId)
        enqueuePlace(tombstone, "DELETE_PERMANENT")
        placeDao.hardDelete(placeId)
    }

    private suspend fun enqueueEvent(entity: CalendarEventEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(entity.householdId, device.deviceId, "calendar_event", entity.id, CalendarSyncPayloadCodec.encode(CalendarEventSyncPayload(entity.id, entity.householdId, entity.title, entity.normalizedTitle, entity.notes, entity.location, entity.startAt, entity.endAt, entity.allDay, entity.reminderDaysBefore, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice, entity.participantIdsJson, entity.recurrenceType, entity.recurrenceInterval, entity.recurrenceWeekdays, entity.recurrenceUntil, entity.recurrenceCustomUnit)), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private suspend fun enqueuePlace(entity: CalendarPlaceEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(entity.householdId, device.deviceId, "calendar_place", entity.id, CalendarSyncPayloadCodec.encode(CalendarPlaceSyncPayload(entity.id, entity.householdId, entity.name, entity.normalizedName, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private suspend fun enqueueException(entity: CalendarEventExceptionEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(entity.householdId, device.deviceId, "calendar_exception", "${entity.seriesId}:${entity.occurrenceDate}", CalendarSyncPayloadCodec.encode(entity.toPayload()), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private fun CalendarEventEntity.toModel(): CalendarEventItem = CalendarEventItem(
        id = id,
        title = title,
        normalizedTitle = normalizedTitle,
        notes = notes,
        location = location,
        startAt = startAt,
        endAt = endAt,
        allDay = allDay,
        reminderDaysBefore = reminderDaysBefore,
        deleted = deleted,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        participantIds = decodeIds(participantIdsJson),
        recurrenceType = runCatching { com.majyd.casa.data.model.CalendarRecurrenceType.valueOf(recurrenceType) }.getOrDefault(com.majyd.casa.data.model.CalendarRecurrenceType.NONE),
        recurrenceInterval = recurrenceInterval,
        recurrenceWeekdays = recurrenceWeekdays.split(',').mapNotNull { it.toIntOrNull() }.toSet(),
        recurrenceUntil = recurrenceUntil,
        recurrenceCustomUnit = recurrenceCustomUnit,
    )

    private fun CalendarEventExceptionEntity.toModel() = CalendarEventException(seriesId, java.time.LocalDate.ofEpochDay(occurrenceDate), title, notes, location, startAt, endAt, allDay, decodeIds(participantIdsJson), deleted)
    private fun CalendarEventExceptionEntity.toPayload() = com.majyd.casa.sync.CalendarExceptionSyncPayload(seriesId, occurrenceDate, householdId, title, notes, location, startAt, endAt, allDay, participantIdsJson, deleted, createdAt, updatedAt, version, updatedByDevice)

    private fun encodeIds(ids: Set<String>): String = Json.encodeToString(ListSerializer(String.serializer()), ids.toList().sorted())
    private fun decodeIds(value: String): Set<String> = runCatching { Json.decodeFromString(ListSerializer(String.serializer()), value).toSet() }.getOrDefault(emptySet())

}
