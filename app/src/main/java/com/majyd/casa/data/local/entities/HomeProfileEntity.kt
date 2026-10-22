package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_profile")
data class HomeProfileEntity(
    @PrimaryKey val profileId: String = DEFAULT_PROFILE_ID,
    val homeName: String,
    val personOneName: String,
    val personTwoName: String,
    val lastSyncAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val DEFAULT_PROFILE_ID: String = "home"
    }
}

