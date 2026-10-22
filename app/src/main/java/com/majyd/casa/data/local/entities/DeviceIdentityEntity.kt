package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_identity")
data class DeviceIdentityEntity(
    @PrimaryKey val deviceId: String,
    val householdId: String = "household-default",
    val assignedPersonId: String? = null,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long,
)
