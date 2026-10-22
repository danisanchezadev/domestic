package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.DeviceIdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM device_identity ORDER BY createdAt ASC LIMIT 1")
    fun observeDevice(): Flow<DeviceIdentityEntity?>

    @Query("SELECT * FROM device_identity ORDER BY createdAt ASC LIMIT 1")
    suspend fun getDevice(): DeviceIdentityEntity?

    @Upsert
    suspend fun upsert(device: DeviceIdentityEntity)

    @Query("UPDATE device_identity SET displayName = :displayName, updatedAt = :updatedAt WHERE deviceId = :deviceId")
    suspend fun updateName(deviceId: String, displayName: String, updatedAt: Long)

    @Query("UPDATE device_identity SET assignedPersonId = :personId, updatedAt = :updatedAt WHERE deviceId = :deviceId")
    suspend fun assignPerson(deviceId: String, personId: String?, updatedAt: Long)

    @Query("UPDATE device_identity SET householdId = :householdId, updatedAt = :updatedAt WHERE deviceId = :deviceId")
    suspend fun joinHousehold(deviceId: String, householdId: String, updatedAt: Long)
}
