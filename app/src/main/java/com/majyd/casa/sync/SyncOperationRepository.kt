package com.majyd.casa.sync

import kotlinx.coroutines.flow.Flow

interface SyncOperationRepository {
    suspend fun enqueue(householdId: String, deviceId: String, entityType: String, entityId: String, payload: String, version: Long, updatedAt: Long, deleted: Boolean, operationType: String = "UPDATE", createdAt: Long = updatedAt)
    fun observePending(householdId: String): Flow<List<SyncOperation>>
    fun observeAll(householdId: String): Flow<List<SyncOperation>> = observePending(householdId)
    suspend fun markApplied(operationId: String)
    suspend fun get(operationId: String): SyncOperation?
    suspend fun record(operation: SyncOperation)
}
