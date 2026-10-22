package com.majyd.casa.ui.sync

import com.majyd.casa.sync.SyncOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialBackupCodecTest {
    @Test
    fun encryptedBackupRoundTrips() {
        val original = CredentialBackup(
            householdId = "home-1",
            pairingCode = "123456",
            operations = listOf(
                SyncOperation("op-1", "home-1", "task", "task-1", payload = "{}", version = 1L, updatedAt = 2L, deviceId = "device-1", deleted = false, applied = true),
            ),
        )
        val encoded = CredentialBackupCodec.encode(original)

        assertTrue(encoded.startsWith("CASA_BACKUP_V2:"))
        assertEquals(original, CredentialBackupCodec.decode(encoded, "123456"))
        assertThrows(Exception::class.java) { CredentialBackupCodec.decode(encoded, "000000") }
    }

    @Test
    fun oldCredentialBackupRemainsCompatible() {
        val original = CredentialBackup("home-1", "123456")
        val decoded = CredentialBackupCodec.decode(CredentialBackupCodec.encode(original), "123456")

        assertEquals(emptyList<SyncOperation>(), decoded.operations)
    }
}
