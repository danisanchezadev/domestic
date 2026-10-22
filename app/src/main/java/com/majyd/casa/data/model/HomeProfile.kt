package com.majyd.casa.data.model

data class HomeProfile(
    val profileId: String,
    val homeName: String,
    val personOneName: String,
    val personTwoName: String,
    val lastSyncAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

