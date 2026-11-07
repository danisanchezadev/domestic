package com.majyd.casa.ui.sync

data class PairingPayload(
    val householdId: String,
    val pairingCode: String,
    val deviceId: String? = null,
)

fun parsePairingPayload(value: String): PairingPayload? {
    val parts = value.trim().split('|')
    if (parts.size !in 3..4 || parts[0] != "CASA") return null
    if (parts[1].isBlank() || parts[2].length != 6 || !parts[2].all(Char::isDigit)) return null
    return PairingPayload(parts[1], parts[2], parts.getOrNull(3)?.takeIf { it.isNotBlank() })
}
