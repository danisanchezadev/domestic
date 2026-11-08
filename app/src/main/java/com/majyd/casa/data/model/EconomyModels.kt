package com.majyd.casa.data.model

enum class EconomyMovementType { INCOME, EXPENSE, INVESTMENT }

data class EconomyCategory(
    val id: String,
    val type: EconomyMovementType,
    val name: String,
    val deleted: Boolean = false,
)

data class EconomyMovement(
    val id: String,
    val type: EconomyMovementType,
    val amountCents: Long,
    val categoryId: String,
    val dateEpochDay: Long,
    val deleted: Boolean = false,
)

data class EconomyMonthlyTarget(
    val monthKey: String,
    val targetCents: Long,
    val minimumCents: Long,
)

data class EconomySettings(
    val fiscalStartMonth: Int = 1,
    val currency: String = "EUR",
    val targetCents: Long = 0L,
    val minimumCents: Long = 0L,
)

data class EconomyTotals(
    val incomeCents: Long,
    val expenseCents: Long,
    val investmentCents: Long,
) {
    val liquidityCents: Long get() = incomeCents - expenseCents - investmentCents
}

object EconomyCalculations {
    fun totals(movements: Iterable<EconomyMovement>): EconomyTotals = EconomyTotals(
        incomeCents = movements.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents },
        expenseCents = movements.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents },
        investmentCents = movements.filter { it.type == EconomyMovementType.INVESTMENT }.sumOf { it.amountCents },
    )

    fun marginToTarget(totals: EconomyTotals, targetCents: Long): Long = totals.liquidityCents - targetCents
}

enum class EconomyBalanceStatus { TARGET, MINIMUM, POSITIVE, NEGATIVE }
