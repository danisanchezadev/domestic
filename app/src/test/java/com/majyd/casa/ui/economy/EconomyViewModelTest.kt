package com.majyd.casa.ui.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EconomyViewModelTest {
    @Test
    fun parsesSpanishDecimalAmountsAsCents() {
        assertEquals(12345L, EconomyViewModel.parseCents("123,45"))
        assertEquals(100000L, EconomyViewModel.parseCents("1.000,00"))
    }

    @Test
    fun rejectsInvalidOrImpreciseAmounts() {
        assertEquals(0L, EconomyViewModel.parseCents("0"))
        assertNull(EconomyViewModel.parseCents("12,345"))
        assertNull(EconomyViewModel.parseCents("abc"))
    }
}
