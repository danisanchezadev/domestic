package com.majyd.casa.sync

import com.majyd.casa.data.local.entities.AuthorizedDeviceEntity

object AuthorizedDevicePolicy {
    fun canAccept(
        existing: AuthorizedDeviceEntity?,
        householdId: String,
        activeDeviceCount: Int,
        maxDevices: Int = 2,
    ): Boolean = existing?.let {
        it.householdId == householdId && !it.revoked
    } ?: (activeDeviceCount < maxDevices)
}
