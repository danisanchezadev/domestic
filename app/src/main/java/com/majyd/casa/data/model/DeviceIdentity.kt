package com.majyd.casa.data.model

data class DeviceIdentity(
    val deviceId: String,
    val householdId: String,
    val assignedPersonId: String?,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long,
)
