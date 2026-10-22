package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.ContactEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        if (operationRepository.get(operation.operationId) != null) return true
        val payload = ContactSyncPayloadCodec.decode(operation.payload) ?: return false
        if (operation.entityType != "contact" || payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        database.withTransaction {
            val dao = database.contactDao()
            val local = dao.getById(payload.id)
            if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, operation.deviceId)) {
                dao.upsert(
                    ContactEntity(
                        id = payload.id,
                        householdId = payload.householdId,
                        category = payload.category,
                        name = payload.name,
                        normalizedName = payload.normalizedName,
                        phone = payload.phone,
                        deleted = payload.deleted,
                        createdAt = payload.createdAt,
                        updatedAt = payload.updatedAt,
                        version = payload.version,
                        updatedByDevice = operation.deviceId,
                    )
                )
            }
            operationRepository.record(operation.copy(applied = true))
        }
        return true
    }
}
