package com.majyd.casa.ui.weeklymenu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.WeeklyMenuItem
import com.majyd.casa.data.model.Dish
import com.majyd.casa.data.model.DishIngredient
import com.majyd.casa.data.model.DishVariant
import com.majyd.casa.data.model.MenuTemplate
import com.majyd.casa.data.model.MenuHistoryItem
import com.majyd.casa.data.repository.MenuTemplateRepository
import com.majyd.casa.data.repository.DishRepository
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
    val dishId: String? = null,
    val variantId: String? = null,
)

data class WeeklyMenuUiState(
    val activeItems: List<WeeklyMenuItem> = emptyList(),
    val deletedItems: List<WeeklyMenuItem> = emptyList(),
    val draft: WeeklyMenuDraft = WeeklyMenuDraft(),
    val dishes: List<Dish> = emptyList(),
    val variants: List<DishVariant> = emptyList(),
    val ingredientPicker: IngredientPickerState? = null,
    val templates: List<MenuTemplate> = emptyList(),
    val history: List<MenuHistoryItem> = emptyList(),
)

data class IngredientPickerState(
    val ingredients: List<DishIngredient>,
    val selectedIds: Set<String>,
)

@HiltViewModel
class WeeklyMenuViewModel @Inject constructor(
    private val weeklyMenuRepository: WeeklyMenuRepository,
    private val dishRepository: DishRepository,
    private val menuTemplateRepository: MenuTemplateRepository,
) : ViewModel() {

    private val draft = MutableStateFlow(WeeklyMenuDraft())
    private val items = weeklyMenuRepository.observeItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val dishes = dishRepository.observeDishes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val ingredientPicker = MutableStateFlow<IngredientPickerState?>(null)
    private val availableVariants = MutableStateFlow<List<DishVariant>>(emptyList())
    private val templates = menuTemplateRepository.observeTemplates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val history = weeklyMenuRepository.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val baseUiState = combine(items, draft, dishes, availableVariants, ingredientPicker) { list, draftValue, dishList, variantList, picker ->
        WeeklyMenuUiState(
            activeItems = list.filterNot { it.deleted },
            deletedItems = list.filter { it.deleted },
            draft = draftValue,
            dishes = dishList,
            variants = variantList,
            ingredientPicker = picker,
        )
    }

    val uiState: StateFlow<WeeklyMenuUiState> = combine(baseUiState, templates, history) { state, templateList, historyList ->
        state.copy(templates = templateList, history = historyList)
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
            dishId = item.dishId,
            variantId = item.variantId,
        )
        viewModelScope.launch { availableVariants.value = item.dishId?.let { dishRepository.getVariants(it) }.orEmpty() }
    }

    fun clearDraft() {
        draft.value = WeeklyMenuDraft()
    }

    fun updateDayOfWeek(value: Int) = draft.update { it.copy(dayOfWeek = value.coerceIn(1, 7)) }
    fun updateMealSlot(value: MealSlot) = draft.update { it.copy(mealSlot = value) }
    fun updateTitle(value: String) = draft.update { it.copy(title = value) }
    fun updateNotes(value: String) = draft.update { it.copy(notes = value) }
    fun selectDish(dish: Dish?) {
        draft.update { it.copy(dishId = dish?.id, variantId = null, title = dish?.name ?: it.title) }
        viewModelScope.launch { availableVariants.value = dish?.let { dishRepository.getVariants(it.id) }.orEmpty() }
    }
    fun selectVariant(variant: DishVariant?) = draft.update { it.copy(variantId = variant?.id) }

    fun requestIngredientSelection() = viewModelScope.launch {
        val dishId = draft.value.dishId
        if (dishId == null) {
            saveItem(null)
            return@launch
        }
        val ingredients = dishRepository.getIngredients(dishId, draft.value.variantId).filterNot { it.deleted }
        if (ingredients.isEmpty()) saveItem(emptySet())
        else ingredientPicker.value = IngredientPickerState(ingredients, ingredients.map { it.id }.toSet())
    }

    fun toggleIngredient(id: String) = ingredientPicker.update { state ->
        state?.copy(selectedIds = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id)
    }

    fun cancelIngredientSelection() { ingredientPicker.value = null }
    fun confirmIngredientSelection() {
        val selectedIds = ingredientPicker.value?.selectedIds ?: emptySet()
        ingredientPicker.value = null
        saveItem(selectedIds)
    }

    fun saveItem() = requestIngredientSelection()

    private fun saveItem(selectedIngredientIds: Set<String>?) = viewModelScope.launch {
        val current = draft.value
        if (Normalizer.normalizeText(current.title).isBlank()) return@launch
        weeklyMenuRepository.upsertItem(
            itemId = current.itemId,
            dayOfWeek = current.dayOfWeek,
            mealSlot = current.mealSlot,
            dishId = current.dishId,
            variantId = current.variantId,
            title = current.title,
            notes = current.notes,
            shoppingIngredientIds = selectedIngredientIds,
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

    fun saveTemplate(name: String) = viewModelScope.launch {
        if (name.trim().isBlank()) return@launch
        menuTemplateRepository.saveTemplate(name, items.value.filterNot { it.deleted })
    }

    fun loadTemplate(templateId: String) = viewModelScope.launch {
        menuTemplateRepository.loadTemplate(templateId).forEach { item ->
            weeklyMenuRepository.upsertItem(
                itemId = null,
                dayOfWeek = item.dayOfWeek,
                mealSlot = item.mealSlot,
                dishId = item.dishId,
                variantId = item.variantId,
                title = item.title,
                notes = item.notes,
                shoppingIngredientIds = emptySet(),
            )
        }
    }

    fun deleteTemplate(templateId: String) = viewModelScope.launch {
        menuTemplateRepository.deleteTemplate(templateId)
    }

    fun setHistoryCompleted(item: MenuHistoryItem, completed: Boolean) = viewModelScope.launch {
        weeklyMenuRepository.setHistoryCompleted(item.id, completed)
    }
}
