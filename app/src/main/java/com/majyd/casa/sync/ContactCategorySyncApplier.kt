package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.ContactCategoryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactCategorySyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        if (operationRepository.get(operation.operationId) != null) return true
        val payload = ContactCategorySyncPayloadCodec.decode(operation.payload) ?: return false
        if (operation.entityType != "contact_category" || payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        database.withTransaction {
            val dao = database.contactCategoryDao()
            val local = dao.getById(payload.id)
            if (local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, operation.deviceId)) {
                dao.upsert(ContactCategoryEntity(payload.id, payload.householdId, payload.name, payload.normalizedName, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, operation.deviceId))
            }
        }
        operationRepository.record(operation.copy(applied = true))
        return true
    }
}
