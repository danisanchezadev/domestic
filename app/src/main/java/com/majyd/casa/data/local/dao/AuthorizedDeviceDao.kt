package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.AuthorizedDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthorizedDeviceDao {
    @Query("SELECT * FROM authorized_devices WHERE householdId = :householdId ORDER BY lastSeenAt DESC")
    fun observeForHousehold(householdId: String): Flow<List<AuthorizedDeviceEntity>>

    @Query("SELECT * FROM authorized_devices WHERE householdId = :householdId AND revoked = 0")
    suspend fun activeForHousehold(householdId: String): List<AuthorizedDeviceEntity>

    @Query("SELECT * FROM authorized_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun get(deviceId: String): AuthorizedDeviceEntity?

    @Upsert
    suspend fun upsert(device: AuthorizedDeviceEntity)

    @Query("UPDATE authorized_devices SET revoked = 1 WHERE deviceId = :deviceId")
    suspend fun revoke(deviceId: String)

    @Query("UPDATE authorized_devices SET revoked = 0 WHERE householdId = :householdId")
    suspend fun clearRevokedForHousehold(householdId: String)

    @Query("DELETE FROM authorized_devices WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)
}
