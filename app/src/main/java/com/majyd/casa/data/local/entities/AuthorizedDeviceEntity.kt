package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "authorized_devices")
data class AuthorizedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val householdId: String,
    val displayName: String,
    val lastSeenAt: Long,
    val revoked: Boolean = false,
)
