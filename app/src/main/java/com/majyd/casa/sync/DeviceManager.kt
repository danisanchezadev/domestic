package com.majyd.casa.sync

import com.majyd.casa.data.model.DeviceIdentity
import kotlinx.coroutines.flow.Flow

interface DeviceManager {
    fun observeDevice(): Flow<DeviceIdentity?>
    suspend fun ensureDevice(): DeviceIdentity
    suspend fun renameDevice(displayName: String): DeviceIdentity
    suspend fun assignPerson(personId: String?): DeviceIdentity
    suspend fun joinHousehold(householdId: String): DeviceIdentity
}
