package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.CalendarEventEntity
import com.majyd.casa.data.local.entities.CalendarPlaceEntity
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
        val accepted = database.withTransaction {
            when (operation.entityType) {
                "calendar_event" -> applyEvent(operation)
                "calendar_place" -> applyPlace(operation)
                else -> false
            }
        }
        if (accepted) operationRepository.record(operation.copy(applied = true))
        return accepted
    }

    private suspend fun applyEvent(operation: SyncOperation): Boolean {
        val payload = CalendarSyncPayloadCodec.decodeEvent(operation.payload) ?: return false
        if (payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val dao = database.calendarDao()
        val local = dao.getById(payload.id)
        if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, operation.deviceId)) {
            dao.upsert(CalendarEventEntity(payload.id, payload.householdId, payload.title, payload.normalizedTitle, payload.notes, payload.location, payload.startAt, payload.endAt, payload.allDay, payload.reminderDaysBefore, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice))
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
}
