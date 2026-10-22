package com.majyd.casa.ui.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingQrTest {
    @Test
    fun generatedQrCanBeDecoded() {
        val payload = "CASA|household-1|123456"

        assertEquals(payload, decodePairingQr(createPairingQr(payload, size = 320)))
    }
}
