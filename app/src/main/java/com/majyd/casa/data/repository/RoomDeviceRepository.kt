package com.majyd.casa.data.repository

import com.majyd.casa.data.local.dao.DeviceDao
import com.majyd.casa.data.local.entities.DeviceIdentityEntity
import com.majyd.casa.data.model.DeviceIdentity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomDeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao,
) : DeviceRepository {

    override fun observeDevice(): Flow<DeviceIdentity?> {
        return deviceDao.observeDevice().map { entity -> entity?.toModel() }
    }

    override suspend fun ensureDevice(): DeviceIdentity {
        val existing = deviceDao.getDevice()
        if (existing != null) return existing.toModel()

        val now = System.currentTimeMillis()
        val entity = DeviceIdentityEntity(
            deviceId = UUID.randomUUID().toString(),
            householdId = "household-default",
            assignedPersonId = null,
            displayName = "Este móvil",
            createdAt = now,
            updatedAt = now,
        )
        deviceDao.upsert(entity)
        return entity.toModel()
    }

    override suspend fun renameDevice(displayName: String): DeviceIdentity {
        val current = deviceDao.getDevice()
        val now = System.currentTimeMillis()
        return if (current != null) {
            deviceDao.updateName(current.deviceId, displayName, now)
            current.toModel().copy(displayName = displayName, updatedAt = now)
        } else {
            val created = ensureDevice()
            deviceDao.updateName(created.deviceId, displayName, now)
            created.copy(displayName = displayName, updatedAt = now)
        }
    }

    override suspend fun assignPerson(personId: String?): DeviceIdentity {
        val current = ensureDevice()
        val now = System.currentTimeMillis()
        deviceDao.assignPerson(current.deviceId, personId, now)
        return current.copy(assignedPersonId = personId, updatedAt = now)
    }

    override suspend fun joinHousehold(householdId: String): DeviceIdentity {
        require(householdId.isNotBlank()) { "Hogar no válido" }
        val current = ensureDevice()
        val now = System.currentTimeMillis()
        deviceDao.joinHousehold(current.deviceId, householdId, now)
        return current.copy(householdId = householdId, updatedAt = now)
    }

    private fun DeviceIdentityEntity.toModel(): DeviceIdentity = DeviceIdentity(
        deviceId = deviceId,
        householdId = householdId,
        assignedPersonId = assignedPersonId,
        displayName = displayName,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
