package com.majyd.casa.ui.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPayloadTest {
    @Test
    fun parsesValidPayload() {
        assertEquals(PairingPayload("home-1", "123456"), parsePairingPayload("CASA|home-1|123456"))
    }

    @Test
    fun rejectsMalformedPayloads() {
        assertNull(parsePairingPayload("CASA|home-1|12345"))
        assertNull(parsePairingPayload("CASA|home-1|abcdef"))
        assertNull(parsePairingPayload("OTHER|home-1|123456"))
    }
}
