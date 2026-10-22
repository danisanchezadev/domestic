package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.CalendarDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.CalendarEventEntity
import com.majyd.casa.data.local.entities.CalendarPlaceEntity
import com.majyd.casa.data.model.CalendarPlace
import com.majyd.casa.data.model.CalendarEventItem
import com.majyd.casa.sync.CalendarEventSyncPayload
import com.majyd.casa.sync.CalendarSyncPayloadCodec
import com.majyd.casa.sync.CalendarPlaceSyncPayload
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.util.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomCalendarRepository @Inject constructor(
    private val database: CasaDatabase,
    private val calendarDao: CalendarDao,
    private val placeDao: com.majyd.casa.data.local.dao.CalendarPlaceDao,
    private val deviceManager: DeviceManager,
    private val syncOperationRepository: SyncOperationRepository,
) : CalendarRepository {

    override fun observeEvents(): Flow<List<CalendarEventItem>> =
        calendarDao.observeEvents().map { events -> events.map { it.toModel() } }

    override fun observePlaces(): Flow<List<CalendarPlace>> =
        placeDao.observeActive().map { places -> places.map(CalendarPlaceEntity::toModel) }

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
        syncOperationRepository.enqueue(entity.householdId, device.deviceId, "calendar_event", entity.id, CalendarSyncPayloadCodec.encode(CalendarEventSyncPayload(entity.id, entity.householdId, entity.title, entity.normalizedTitle, entity.notes, entity.location, entity.startAt, entity.endAt, entity.allDay, entity.reminderDaysBefore, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private suspend fun enqueuePlace(entity: CalendarPlaceEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(entity.householdId, device.deviceId, "calendar_place", entity.id, CalendarSyncPayloadCodec.encode(CalendarPlaceSyncPayload(entity.id, entity.householdId, entity.name, entity.normalizedName, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)), entity.version, entity.updatedAt, entity.deleted, operationType)
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
    )

}
