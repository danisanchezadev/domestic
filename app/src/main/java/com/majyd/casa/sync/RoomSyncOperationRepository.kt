package com.majyd.casa.sync

import com.majyd.casa.data.local.dao.SyncOperationDao
import com.majyd.casa.data.local.entities.SyncOperationEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomSyncOperationRepository @Inject constructor(
    private val dao: SyncOperationDao,
    private val changeNotifier: SyncChangeNotifier,
) : SyncOperationRepository {
    override suspend fun enqueue(
        householdId: String,
        deviceId: String,
        entityType: String,
        entityId: String,
        payload: String,
        version: Long,
        updatedAt: Long,
        deleted: Boolean,
        operationType: String,
        createdAt: Long,
    ) {
        dao.upsert(SyncOperationEntity(UUID.randomUUID().toString(), householdId, entityType, entityId, operationType, payload, version, createdAt, updatedAt, deviceId, deleted, false))
        changeNotifier.request()
    }

    override fun observePending(householdId: String): Flow<List<SyncOperation>> = dao.observePending(householdId).map { list -> list.map { it.toModel() } }
    override fun observeAll(householdId: String): Flow<List<SyncOperation>> = dao.observeAll(householdId).map { list -> list.map { it.toModel() } }
    override suspend fun markApplied(operationId: String) = dao.markApplied(operationId)
    override suspend fun get(operationId: String): SyncOperation? = dao.get(operationId)?.toModel()
    override suspend fun record(operation: SyncOperation) {
        dao.upsert(
            SyncOperationEntity(
                operationId = operation.operationId,
                householdId = operation.householdId,
                entityType = operation.entityType,
                entityId = operation.entityId,
                operationType = operation.operationType,
                payload = operation.payload,
                version = operation.version,
                createdAt = operation.createdAt,
                updatedAt = operation.updatedAt,
                deviceId = operation.deviceId,
                deleted = operation.deleted,
                applied = operation.applied,
            ),
        )
    }
    private fun SyncOperationEntity.toModel() = SyncOperation(operationId, householdId, entityType, entityId, operationType, payload, version, createdAt, updatedAt, deviceId, deleted, applied)
}
