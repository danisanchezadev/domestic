package com.majyd.casa.ui.weeklymenu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.WeeklyMenuItem
import com.majyd.casa.data.repository.WeeklyMenuRepository
import com.majyd.casa.util.Normalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeeklyMenuDraft(
    val itemId: String? = null,
    val dayOfWeek: Int = 1,
    val mealSlot: MealSlot = MealSlot.LUNCH,
    val title: String = "",
    val notes: String = "",
)

data class WeeklyMenuUiState(
    val activeItems: List<WeeklyMenuItem> = emptyList(),
    val deletedItems: List<WeeklyMenuItem> = emptyList(),
    val draft: WeeklyMenuDraft = WeeklyMenuDraft(),
)

@HiltViewModel
class WeeklyMenuViewModel @Inject constructor(
    private val weeklyMenuRepository: WeeklyMenuRepository,
) : ViewModel() {

    private val draft = MutableStateFlow(WeeklyMenuDraft())
    private val items = weeklyMenuRepository.observeItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val uiState: StateFlow<WeeklyMenuUiState> = combine(items, draft) { list, draftValue ->
        WeeklyMenuUiState(
            activeItems = list.filterNot { it.deleted },
            deletedItems = list.filter { it.deleted },
            draft = draftValue,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeeklyMenuUiState(),
    )

    init {
        viewModelScope.launch {
            weeklyMenuRepository.ensureDefaults()
        }
    }

    fun editItem(item: WeeklyMenuItem) {
        draft.value = WeeklyMenuDraft(
            itemId = item.id,
            dayOfWeek = item.dayOfWeek,
            mealSlot = item.mealSlot,
            title = item.title,
            notes = item.notes,
        )
    }

    fun clearDraft() {
        draft.value = WeeklyMenuDraft()
    }

    fun updateDayOfWeek(value: Int) = draft.update { it.copy(dayOfWeek = value.coerceIn(1, 7)) }
    fun updateMealSlot(value: MealSlot) = draft.update { it.copy(mealSlot = value) }
    fun updateTitle(value: String) = draft.update { it.copy(title = value) }
    fun updateNotes(value: String) = draft.update { it.copy(notes = value) }

    fun saveItem() = viewModelScope.launch {
        val current = draft.value
        if (Normalizer.normalizeText(current.title).isBlank()) return@launch
        weeklyMenuRepository.upsertItem(
            itemId = current.itemId,
            dayOfWeek = current.dayOfWeek,
            mealSlot = current.mealSlot,
            title = current.title,
            notes = current.notes,
        )
        draft.value = WeeklyMenuDraft()
    }

    fun deleteItem(item: WeeklyMenuItem) = viewModelScope.launch {
        weeklyMenuRepository.deleteItem(item.id)
    }

    fun restoreItem(item: WeeklyMenuItem) = viewModelScope.launch {
        weeklyMenuRepository.restoreItem(item.id)
    }

    fun archiveAll() = viewModelScope.launch {
        weeklyMenuRepository.archiveAll()
    }
}
