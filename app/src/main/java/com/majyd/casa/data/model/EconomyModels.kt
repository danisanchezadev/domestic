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

enum class EconomyBalanceStatus { TARGET, MINIMUM, POSITIVE, NEGATIVE }
