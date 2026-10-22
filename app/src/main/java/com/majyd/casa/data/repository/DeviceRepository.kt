package com.majyd.casa.data.repository

import com.majyd.casa.data.model.DeviceIdentity
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun observeDevice(): Flow<DeviceIdentity?>
    suspend fun ensureDevice(): DeviceIdentity
    suspend fun renameDevice(displayName: String): DeviceIdentity
    suspend fun assignPerson(personId: String?): DeviceIdentity
    suspend fun joinHousehold(householdId: String): DeviceIdentity
}
