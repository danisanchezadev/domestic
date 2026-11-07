package com.majyd.casa.ui.economy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.EconomyCategory
import com.majyd.casa.data.model.EconomyMonthlyTarget
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType
import com.majyd.casa.data.repository.EconomyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EconomyUiState(
    val categories: List<EconomyCategory> = emptyList(),
    val movements: List<EconomyMovement> = emptyList(),
    val target: EconomyMonthlyTarget = EconomyMonthlyTarget(YearMonth.now().toString(), 0, 0),
    val error: String? = null,
)

@HiltViewModel
class EconomyViewModel @Inject constructor(private val repository: EconomyRepository) : ViewModel() {
    private val target = MutableStateFlow(EconomyMonthlyTarget(YearMonth.now().toString(), 0, 0))
    private val error = MutableStateFlow<String?>(null)
    private val categories = repository.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val movements = repository.observeMovements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val uiState: StateFlow<EconomyUiState> = combine(categories, movements, target, error) { cats, values, goal, message -> EconomyUiState(cats, values, goal, message) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EconomyUiState())

    init { viewModelScope.launch { repository.ensureDefaults(); target.value = repository.getTarget(YearMonth.now().toString()) } }

    fun clearError() { error.value = null }
    fun saveMovement(id: String?, type: EconomyMovementType, amountText: String, categoryId: String, date: LocalDate = LocalDate.now(), onSaved: () -> Unit = {}) = viewModelScope.launch {
        val cents = parseCents(amountText)
        if (cents == null || cents <= 0) { error.value = "Introduce un importe mayor que cero."; return@launch }
        if (repository.saveMovement(id, type, cents, categoryId, date.toEpochDay()) == null) { error.value = "Selecciona una categoría válida."; return@launch }
        error.value = null
        onSaved()
    }
    fun deleteMovement(movement: EconomyMovement) = viewModelScope.launch { repository.deleteMovement(movement.id) }
    fun saveCategory(id: String?, type: EconomyMovementType, name: String) = viewModelScope.launch { if (repository.saveCategory(id, type, name) == null) error.value = "La categoría está vacía o ya existe." }
    fun archiveCategory(category: EconomyCategory) = viewModelScope.launch { repository.archiveCategory(category.id) }
    fun saveTarget(targetText: String, minimumText: String, onSaved: () -> Unit = {}) = viewModelScope.launch {
        val targetCents = parseCents(targetText)
        val minimumCents = parseCents(minimumText)
        if (targetCents == null || minimumCents == null || targetCents <= 0 || minimumCents < 0 || minimumCents > targetCents) { error.value = "El mínimo debe ser igual o menor que el objetivo."; return@launch }
        target.value = repository.saveTarget(YearMonth.now().toString(), targetCents, minimumCents) ?: return@launch
        error.value = null
        onSaved()
    }

    fun loadDemoData() = viewModelScope.launch {
        val incomeCategory = categories.value.firstOrNull { it.type == EconomyMovementType.INCOME && it.name == "Nómina" }
        val expenseCategory = categories.value.firstOrNull { it.type == EconomyMovementType.EXPENSE && it.name == "Compra" }
        if (incomeCategory == null || expenseCategory == null) {
            error.value = "No se han podido preparar las categorías de prueba."
            return@launch
        }
        val demo = listOf(
            Triple(LocalDate.of(2026, 6, 1), EconomyMovementType.INCOME, 210_000L),
            Triple(LocalDate.of(2026, 6, 6), EconomyMovementType.EXPENSE, 82_000L),
            Triple(LocalDate.of(2026, 7, 1), EconomyMovementType.INCOME, 210_000L),
            Triple(LocalDate.of(2026, 7, 8), EconomyMovementType.EXPENSE, 94_000L),
            Triple(LocalDate.of(2026, 8, 1), EconomyMovementType.INCOME, 225_000L),
            Triple(LocalDate.of(2026, 8, 9), EconomyMovementType.EXPENSE, 108_000L),
            Triple(LocalDate.of(2026, 9, 1), EconomyMovementType.INCOME, 225_000L),
            Triple(LocalDate.of(2026, 9, 12), EconomyMovementType.EXPENSE, 124_000L),
            Triple(LocalDate.of(2026, 10, 1), EconomyMovementType.INCOME, 225_000L),
            Triple(LocalDate.of(2026, 10, 10), EconomyMovementType.EXPENSE, 136_000L),
        )
        val existing = movements.value.toMutableList()
        demo.filter { it.first <= LocalDate.now() }.forEach { (date, type, amount) ->
            val categoryId = if (type == EconomyMovementType.INCOME) incomeCategory.id else expenseCategory.id
            if (existing.none { it.dateEpochDay == date.toEpochDay() && it.type == type && it.categoryId == categoryId && it.amountCents == amount }) {
                repository.saveMovement(null, type, amount, categoryId, date.toEpochDay())?.let(existing::add)
            }
        }
        error.value = null
    }

    companion object {
        fun parseCents(value: String): Long? = value.trim().replace("€", "").replace(".", "").replace(',', '.').toBigDecimalOrNull()?.movePointRight(2)?.longValueExactOrNull()
        fun formatCents(cents: Long): String = "%.2f €".format(java.util.Locale("es", "ES"), cents / 100.0)
        fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        fun formatMonth(month: YearMonth): String = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("es", "ES"))
            .replaceFirstChar { it.uppercase(Locale("es", "ES")) } + " " + month.year
    }
}

private fun java.math.BigDecimal.longValueExactOrNull(): Long? = runCatching { longValueExact() }.getOrNull()
