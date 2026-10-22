package com.majyd.casa.sync

import com.majyd.casa.data.model.DeviceIdentity
import com.majyd.casa.data.repository.DeviceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DefaultDeviceManager @Inject constructor(
    private val deviceRepository: DeviceRepository,
) : DeviceManager {

    override fun observeDevice(): Flow<DeviceIdentity?> = deviceRepository.observeDevice()

    override suspend fun ensureDevice(): DeviceIdentity = deviceRepository.ensureDevice()

    override suspend fun renameDevice(displayName: String): DeviceIdentity = deviceRepository.renameDevice(displayName)

    override suspend fun assignPerson(personId: String?): DeviceIdentity = deviceRepository.assignPerson(personId)

    override suspend fun joinHousehold(householdId: String): DeviceIdentity = deviceRepository.joinHousehold(householdId)
}
