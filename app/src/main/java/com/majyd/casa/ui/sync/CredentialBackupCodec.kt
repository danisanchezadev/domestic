package com.majyd.casa.ui.sync

import com.majyd.casa.sync.SyncOperation
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CredentialBackup(
    val householdId: String,
    val pairingCode: String,
    val operations: List<SyncOperation> = emptyList(),
)

object CredentialBackupCodec {
    private const val PREFIX = "CASA_BACKUP_V1:"
    private const val NONCE_SIZE = 12

    fun encode(backup: CredentialBackup): String {
        val key = key(backup.pairingCode)
        val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(Json.encodeToString(backup).toByteArray(StandardCharsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(nonce + encrypted)
    }

    fun decode(value: String, pairingCode: String): CredentialBackup {
        require(value.startsWith(PREFIX)) { "Backup no válido" }
        val data = Base64.getDecoder().decode(value.removePrefix(PREFIX))
        require(data.size > NONCE_SIZE) { "Backup incompleto" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(pairingCode), GCMParameterSpec(128, data.copyOfRange(0, NONCE_SIZE)))
        return Json.decodeFromString(cipher.doFinal(data.copyOfRange(NONCE_SIZE, data.size)).toString(StandardCharsets.UTF_8))
    }

    private fun key(value: String) = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)),
        "AES",
    )
}
