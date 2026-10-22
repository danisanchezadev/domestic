package com.majyd.casa.sync.transport

data class NearbyDevice(
    val serviceName: String,
    val host: String,
    val port: Int,
    val householdId: String?,
    val deviceId: String? = null,
)
