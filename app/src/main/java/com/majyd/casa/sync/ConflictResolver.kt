package com.majyd.casa.sync

interface ConflictResolver {
    fun incomingWins(localVersion: Long, localUpdatedAt: Long, localDeviceId: String, incomingVersion: Long, incomingUpdatedAt: Long, incomingDeviceId: String): Boolean
}

class DeterministicConflictResolver : ConflictResolver {
    override fun incomingWins(localVersion: Long, localUpdatedAt: Long, localDeviceId: String, incomingVersion: Long, incomingUpdatedAt: Long, incomingDeviceId: String): Boolean {
        return compareValues(incomingVersion, localVersion, incomingUpdatedAt, localUpdatedAt, incomingDeviceId, localDeviceId) > 0
    }

    private fun compareValues(versionA: Long, versionB: Long, updatedA: Long, updatedB: Long, deviceA: String, deviceB: String): Int {
        if (versionA != versionB) return versionA.compareTo(versionB)
        if (updatedA != updatedB) return updatedA.compareTo(updatedB)
        return deviceA.compareTo(deviceB)
    }
}
