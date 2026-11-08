package com.majyd.casa.ui.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.majyd.casa.data.model.EconomyCalculations
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType

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

    @Test
    fun rejectsInvalidAccountingYearsWithoutFallback() {
        assertNull(EconomyViewModel.parseAccountingYear(""))
        assertNull(EconomyViewModel.parseAccountingYear("0"))
        assertNull(EconomyViewModel.parseAccountingYear("10000"))
        assertEquals(2026, EconomyViewModel.parseAccountingYear("2026"))
    }

    @Test
    fun calculatesLiquidityIncludingInvestments() {
        val movements = listOf(
            EconomyMovement("income", EconomyMovementType.INCOME, 500_000, "income", 0),
            EconomyMovement("expense", EconomyMovementType.EXPENSE, 300_000, "expense", 0),
            EconomyMovement("investment", EconomyMovementType.INVESTMENT, 50_000, "investment", 0),
        )

        val totals = EconomyCalculations.totals(movements)

        assertEquals(500_000L, totals.incomeCents)
        assertEquals(300_000L, totals.expenseCents)
        assertEquals(50_000L, totals.investmentCents)
        assertEquals(150_000L, totals.liquidityCents)
        assertEquals(-50_000L, EconomyCalculations.marginToTarget(totals, 200_000L))
    }

    @Test
    fun zeroIncomeWithExpensesProducesNegativeLiquidity() {
        val totals = EconomyCalculations.totals(
            listOf(EconomyMovement("expense", EconomyMovementType.EXPENSE, 1_000, "expense", 0)),
        )

        assertEquals(-1_000L, totals.liquidityCents)
    }
}
