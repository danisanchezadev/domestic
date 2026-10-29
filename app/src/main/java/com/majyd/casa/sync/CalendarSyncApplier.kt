package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.CalendarEventEntity
import com.majyd.casa.data.local.entities.CalendarPlaceEntity
import com.majyd.casa.data.local.entities.CalendarEventExceptionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        if (operationRepository.get(operation.operationId) != null) return true
        return database.withTransaction {
            val accepted = when (operation.entityType) {
                "calendar_event" -> applyEvent(operation)
                "calendar_place" -> applyPlace(operation)
                "calendar_exception" -> applyException(operation)
                else -> false
            }
            if (accepted) operationRepository.record(operation.copy(applied = true))
            accepted
        }
    }

    private suspend fun applyEvent(operation: SyncOperation): Boolean {
        val payload = CalendarSyncPayloadCodec.decodeEvent(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.calendarDao()
        val local = dao.getById(payload.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, operation.deviceId)) {
            dao.upsert(CalendarEventEntity(payload.id, payload.householdId, payload.title, payload.normalizedTitle, payload.notes, payload.location, payload.startAt, payload.endAt, payload.allDay, payload.reminderDaysBefore, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice, payload.participantIdsJson, payload.recurrenceType, payload.recurrenceInterval, payload.recurrenceWeekdays, payload.recurrenceUntil, payload.recurrenceCustomUnit))
        }
        return true
    }

    private suspend fun applyPlace(operation: SyncOperation): Boolean {
        val payload = CalendarSyncPayloadCodec.decodePlace(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.calendarPlaceDao()
        val local = dao.getById(payload.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, operation.deviceId)) {
            dao.upsert(CalendarPlaceEntity(payload.id, payload.householdId, payload.name, payload.normalizedName, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice))
        }
        return true
    }

    private suspend fun applyException(operation: SyncOperation): Boolean {
        val payload = CalendarSyncPayloadCodec.decodeException(operation.payload) ?: return false
        if (payload.householdId != operation.householdId || "${payload.seriesId}:${payload.occurrenceDate}" != operation.entityId) return false
        val dao = database.calendarEventExceptionDao()
        val local = dao.get(payload.seriesId, payload.occurrenceDate)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, operation.deviceId)) {
            dao.upsert(CalendarEventExceptionEntity(payload.seriesId, payload.occurrenceDate, payload.householdId, payload.title, payload.notes, payload.location, payload.startAt, payload.endAt, payload.allDay, payload.participantIdsJson, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice))
        }
        return true
    }
}
