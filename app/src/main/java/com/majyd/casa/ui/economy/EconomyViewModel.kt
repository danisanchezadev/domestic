package com.majyd.casa.ui.economy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.EconomyCategory
import com.majyd.casa.data.model.EconomyMonthlyTarget
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType
import com.majyd.casa.data.model.EconomySettings
import com.majyd.casa.data.repository.EconomyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EconomyUiState(
    val categories: List<EconomyCategory> = emptyList(),
    val movements: List<EconomyMovement> = emptyList(),
    val historyMovements: List<EconomyMovement> = emptyList(),
    val allHistoryMovements: List<EconomyMovement> = emptyList(),
    val target: EconomyMonthlyTarget = EconomyMonthlyTarget(YearMonth.now().toString(), 0, 0),
    val settings: EconomySettings = EconomySettings(),
    val error: String? = null,
)

@HiltViewModel
class EconomyViewModel @Inject constructor(private val repository: EconomyRepository) : ViewModel() {
    private val target = MutableStateFlow(EconomyMonthlyTarget(YearMonth.now().toString(), 0, 0))
    private val error = MutableStateFlow<String?>(null)
    private val settings = MutableStateFlow(EconomySettings())
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val historyMovements = MutableStateFlow<List<EconomyMovement>>(emptyList())
    private val allHistoryMovements = MutableStateFlow<List<EconomyMovement>>(emptyList())
    private val categories = repository.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val movements = selectedMonth.flatMapLatest { repository.observeMovements(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val baseUiState = combine(categories, movements, historyMovements, target, settings) { cats, values, history, goal, config -> EconomyUiState(categories = cats, movements = values, historyMovements = history, target = goal, settings = config) }
    private val historyState = combine(baseUiState, allHistoryMovements) { state, all -> state.copy(allHistoryMovements = all) }
    val uiState: StateFlow<EconomyUiState> = combine(historyState, error) { state, message -> state.copy(error = message) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EconomyUiState())

    init {
        viewModelScope.launch {
            repository.ensureDefaults()
            settings.value = repository.getSettings()
            loadTarget(YearMonth.now())
        }
    }

    fun clearError() { error.value = null }
    fun loadTarget(month: YearMonth) { selectedMonth.value = month; viewModelScope.launch { target.value = repository.getTarget(month.toString()) } }
    fun loadHistory() { loadHistory(YearMonth.now().minusMonths(11), YearMonth.now()) }
    fun loadHistory(start: YearMonth, end: YearMonth) { viewModelScope.launch { historyMovements.value = repository.getMovementsBetween(start, end) } }
    fun loadAllHistory() { viewModelScope.launch { allHistoryMovements.value = repository.getAllMovements() } }
    fun saveSettings(startMonth: Int) = viewModelScope.launch { settings.value = repository.saveSettings(settings.value.copy(fiscalStartMonth = startMonth.coerceIn(1, 12))) }

    fun saveConfiguration(startMonth: Int, targetText: String, minimumText: String) = viewModelScope.launch {
        val targetCents = parseCents(targetText)
        val minimumCents = parseCents(minimumText)
        if (targetCents == null || minimumCents == null || targetCents < 0 || minimumCents < 0 || minimumCents > targetCents) {
            error.value = "El mínimo debe ser igual o menor que el objetivo."
            return@launch
        }
        val saved = repository.saveSettings(settings.value.copy(fiscalStartMonth = startMonth.coerceIn(1, 12), targetCents = targetCents, minimumCents = minimumCents))
        settings.value = saved
        target.value = EconomyMonthlyTarget(target.value.monthKey, saved.targetCents, saved.minimumCents)
        error.value = null
    }

    fun saveMovement(id: String?, type: EconomyMovementType, amountText: String, categoryId: String, date: LocalDate = LocalDate.now(), onSaved: () -> Unit = {}) = viewModelScope.launch {
        val cents = parseCents(amountText)
        if (cents == null || cents <= 0) { error.value = "Introduce un importe válido mayor que cero."; return@launch }
        if (repository.saveMovement(id, type, cents, categoryId, date.toEpochDay()) == null) { error.value = "Selecciona una categoría válida."; return@launch }
        error.value = null
        onSaved()
    }

    fun deleteMovement(movement: EconomyMovement) = viewModelScope.launch { repository.deleteMovement(movement.id) }
    fun saveCategory(id: String?, type: EconomyMovementType, name: String) = viewModelScope.launch { if (repository.saveCategory(id, type, name) == null) error.value = "La categoría está vacía o ya existe." }
    fun archiveCategory(category: EconomyCategory) = viewModelScope.launch { repository.archiveCategory(category.id) }

    fun saveTarget(targetText: String, minimumText: String, month: YearMonth = YearMonth.now(), onSaved: () -> Unit = {}) = viewModelScope.launch {
        val targetCents = parseCents(targetText)
        val minimumCents = parseCents(minimumText)
        if (targetCents == null || minimumCents == null || targetCents < 0 || minimumCents < 0 || minimumCents > targetCents) {
            error.value = "El mínimo debe ser igual o menor que el objetivo."
            return@launch
        }
        target.value = repository.saveTarget(month.toString(), targetCents, minimumCents) ?: return@launch
        settings.value = repository.getSettings()
        error.value = null
        onSaved()
    }

    companion object {
        fun parseCents(value: String): Long? {
            val normalized = value.trim().replace("€", "").replace(" ", "")
            if (normalized.isBlank()) return null
            val spanish = normalized.matches(Regex("[+-]?\\d{1,3}(?:\\.\\d{3})*(?:,\\d{1,2})?"))
            val plain = normalized.matches(Regex("[+-]?\\d+(?:[.,]\\d{1,2})?"))
            if (!spanish && !plain) return null
            val decimal = when {
                normalized.contains(',') -> normalized.replace(".", "").replace(',', '.')
                normalized.matches(Regex("[+-]?\\d{1,3}(?:\\.\\d{3})+")) -> normalized.replace(".", "")
                else -> normalized
            }
            return decimal.toBigDecimalOrNull()?.movePointRight(2)?.longValueExactOrNull()
        }

        fun formatCents(cents: Long): String = "%.2f €".format(Locale("es", "ES"), cents / 100.0)
        fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        fun formatMonth(month: YearMonth): String = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("es", "ES"))
            .replaceFirstChar { it.uppercase(Locale("es", "ES")) } + " " + month.year
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
private fun BigDecimal.longValueExactOrNull(): Long? = runCatching { longValueExact() }.getOrNull()
