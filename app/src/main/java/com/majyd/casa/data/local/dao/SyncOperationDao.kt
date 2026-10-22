package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.SyncOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {
    @Upsert suspend fun upsert(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations WHERE householdId = :householdId AND applied = 0 ORDER BY createdAt ASC, updatedAt ASC, operationId ASC")
    fun observePending(householdId: String): Flow<List<SyncOperationEntity>>

    @Query("SELECT * FROM sync_operations WHERE householdId = :householdId ORDER BY createdAt ASC, updatedAt ASC, operationId ASC")
    fun observeAll(householdId: String): Flow<List<SyncOperationEntity>>

    @Query("UPDATE sync_operations SET applied = 1 WHERE operationId = :operationId")
    suspend fun markApplied(operationId: String)

    @Query("SELECT * FROM sync_operations WHERE operationId = :operationId LIMIT 1")
    suspend fun get(operationId: String): SyncOperationEntity?
}
