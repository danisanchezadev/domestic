package com.majyd.casa.sync.transport

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac

internal class SyncCrypto(authToken: String) {
    private val random = SecureRandom()
    private val key = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest(authToken.toByteArray(StandardCharsets.UTF_8)),
        "AES",
    )

    fun encrypt(value: String): String {
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
    }

    fun decrypt(value: String): String {
        val data = Base64.getDecoder().decode(value)
        require(data.size > NONCE_SIZE) { "Mensaje cifrado inválido" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data.copyOfRange(0, NONCE_SIZE)))
        return cipher.doFinal(data.copyOfRange(NONCE_SIZE, data.size)).toString(StandardCharsets.UTF_8)
    }

    fun proof(clientNonce: String, serverNonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.encoded, "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal((clientNonce + serverNonce).toByteArray(StandardCharsets.UTF_8)))
    }

    companion object {
        fun nonce(): String = Base64.getEncoder().encodeToString(ByteArray(24).also(SecureRandom()::nextBytes))
        fun constantTimeEquals(first: String, second: String): Boolean = MessageDigest.isEqual(
            first.toByteArray(StandardCharsets.UTF_8),
            second.toByteArray(StandardCharsets.UTF_8),
        )

        private const val NONCE_SIZE = 12
        private const val TAG_BITS = 128
    }
}
