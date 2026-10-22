package com.majyd.casa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizerTest {
    @Test
    fun normalizeText_removesAccentsAndExtraSpaces() {
        assertEquals("leche", Normalizer.normalizeText("  Léché  "))
        assertEquals("salsa de tomate", Normalizer.normalizeText("Salsa   de   Tomate"))
    }
}

