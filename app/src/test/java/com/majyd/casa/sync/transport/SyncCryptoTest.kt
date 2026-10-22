package com.majyd.casa.sync.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncCryptoTest {
    @Test
    fun encryptsAndDecryptsWithIntegrity() {
        val crypto = SyncCrypto("123456")
        val encrypted = crypto.encrypt("contenido privado")

        assertEquals("contenido privado", crypto.decrypt(encrypted))
        assertNotEquals(encrypted, crypto.encrypt("contenido privado"))
        assertThrows(Exception::class.java) { crypto.decrypt(encrypted.dropLast(2) + "xx") }
    }

    @Test
    fun proofRequiresTheSharedToken() {
        val proof = SyncCrypto("123456").proof("client", "server")

        assertEquals(proof, SyncCrypto("123456").proof("client", "server"))
        assertNotEquals(proof, SyncCrypto("000000").proof("client", "server"))
    }
}
