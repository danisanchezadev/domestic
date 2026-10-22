package com.majyd.casa.ui.sync

import com.majyd.casa.sync.SyncOperation
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
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
    private const val LEGACY_PREFIX = "CASA_BACKUP_V1:"
    private const val PREFIX = "CASA_BACKUP_V2:"
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val KEY_BITS = 256
    private const val KDF_ITERATIONS = 120_000

    fun encode(backup: CredentialBackup): String {
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(backup.pairingCode, salt), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(Json.encodeToString(backup).toByteArray(StandardCharsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(salt + nonce + encrypted)
    }

    fun decode(value: String, pairingCode: String): CredentialBackup {
        val legacy = value.startsWith(LEGACY_PREFIX)
        require(legacy || value.startsWith(PREFIX)) { "Backup no valido" }
        val data = Base64.getDecoder().decode(value.substringAfter(':'))
        val salt = if (legacy) null else data.copyOfRange(0, SALT_SIZE)
        val nonceStart = if (legacy) 0 else SALT_SIZE
        require(data.size > nonceStart + NONCE_SIZE) { "Backup incompleto" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secret = salt?.let { key(pairingCode, it) } ?: legacyKey(pairingCode)
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, data.copyOfRange(nonceStart, nonceStart + NONCE_SIZE)))
        return Json.decodeFromString(
            cipher.doFinal(data.copyOfRange(nonceStart + NONCE_SIZE, data.size)).toString(StandardCharsets.UTF_8),
        )
    }

    private fun legacyKey(value: String) = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)),
        "AES",
    )

    private fun key(value: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(value.toCharArray(), salt, KDF_ITERATIONS, KEY_BITS)
        return SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
            "AES",
        )
    }
}
