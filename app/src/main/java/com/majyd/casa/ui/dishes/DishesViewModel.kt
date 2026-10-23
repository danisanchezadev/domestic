package com.majyd.casa.ui.dishes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.Dish
import com.majyd.casa.data.model.DishMealType
import com.majyd.casa.data.model.DishUsageStats
import com.majyd.casa.data.repository.DishRepository
import com.majyd.casa.data.repository.WeeklyMenuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DishDraft(
    val dishId: String? = null,
    val name: String = "",
    val mealType: DishMealType = DishMealType.BOTH,
    val imageBase64: String? = null,
    val recipe: String = "",
    val preparationMinutes: String = "",
    val servings: String = "",
    val notes: String = "",
    val ingredients: List<DraftIngredient> = emptyList(),
    val variants: List<DraftVariant> = emptyList(),
)

data class DraftIngredient(val id: String? = null, val name: String, val quantity: String = "", val unit: String = "")
data class DraftVariant(
    val id: String? = null,
    val name: String,
    val recipe: String = "",
    val notes: String = "",
    val ingredients: List<DraftIngredient> = emptyList(),
)

data class DishesUiState(
    val dishes: List<Dish> = emptyList(),
    val query: String = "",
    val mealFilter: DishMealType? = null,
    val draft: DishDraft = DishDraft(),
    val editorOpen: Boolean = false,
    val usageStats: Map<String, DishUsageStats> = emptyMap(),
)

@HiltViewModel
class DishesViewModel @Inject constructor(
    private val repository: DishRepository,
    private val weeklyMenuRepository: WeeklyMenuRepository,
) : ViewModel() {
    private val draft = MutableStateFlow(DishDraft())
    private val editorOpen = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val mealFilter = MutableStateFlow<DishMealType?>(null)
    private val usageStats = MutableStateFlow<Map<String, DishUsageStats>>(emptyMap())
    private val dishes = repository.observeDishes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val baseUiState = combine(dishes, draft, editorOpen, query, mealFilter) { items, draftValue, open, queryValue, filter ->
        val normalizedQuery = queryValue.trim()
        DishesUiState(
            dishes = items.filter { dish ->
                (normalizedQuery.isBlank() || dish.name.contains(normalizedQuery, ignoreCase = true)) &&
                    (filter == null || dish.mealType == filter || dish.mealType == DishMealType.BOTH)
            },
            query = queryValue,
            mealFilter = filter,
            draft = draftValue,
            editorOpen = open,
        )
    }

    val uiState: StateFlow<DishesUiState> = combine(baseUiState, usageStats) { state, stats ->
        state.copy(usageStats = stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DishesUiState())

    init {
        viewModelScope.launch {
            dishes.collect { items ->
                usageStats.value = items.associate { it.id to weeklyMenuRepository.getUsageStats(it.id) }
            }
        }
    }

    fun openNew() {
        draft.value = DishDraft()
        editorOpen.value = true
    }

    fun edit(dish: Dish) {
        draft.value = DishDraft(
            dishId = dish.id,
            name = dish.name,
            mealType = dish.mealType,
            imageBase64 = dish.imageBase64,
            recipe = dish.recipe,
            preparationMinutes = dish.preparationMinutes?.toString().orEmpty(),
            servings = dish.servings?.toString().orEmpty(),
            notes = dish.notes,
        )
        editorOpen.value = true
        viewModelScope.launch {
            val ingredients = repository.getIngredients(dish.id)
            draft.update { current ->
                if (current.dishId != dish.id) current else current.copy(
                    ingredients = ingredients.map { DraftIngredient(it.id, it.name, it.quantity?.toString().orEmpty(), it.unit) },
                )
            }
            val variants = repository.getVariants(dish.id)
            draft.update { current ->
                if (current.dishId != dish.id) current else current.copy(
                    variants = variants.map { variant ->
                        DraftVariant(
                            id = variant.id,
                            name = variant.name,
                            recipe = variant.recipe,
                            notes = variant.notes,
                            ingredients = repository.getIngredients(dish.id, variant.id).map {
                                DraftIngredient(it.id, it.name, it.quantity?.toString().orEmpty(), it.unit)
                            },
                        )
                    },
                )
            }
        }
    }

    fun closeEditor() { editorOpen.value = false }
    fun updateQuery(value: String) { query.value = value }
    fun updateMealFilter(value: DishMealType?) { mealFilter.value = value }
    fun updateName(value: String) = draft.update { it.copy(name = value) }
    fun updateMealType(value: DishMealType) = draft.update { it.copy(mealType = value) }
    fun updateImage(value: String?) = draft.update { it.copy(imageBase64 = value) }
    fun updateRecipe(value: String) = draft.update { it.copy(recipe = value) }
    fun updatePreparationMinutes(value: String) = draft.update { it.copy(preparationMinutes = value) }
    fun updateServings(value: String) = draft.update { it.copy(servings = value) }
    fun updateNotes(value: String) = draft.update { it.copy(notes = value) }
    fun addIngredient(name: String, quantity: String, unit: String) {
        if (name.trim().isNotBlank()) draft.update { it.copy(ingredients = it.ingredients + DraftIngredient(name = name.trim(), quantity = quantity, unit = unit)) }
    }
    fun removeIngredient(index: Int) = draft.update { state -> state.copy(ingredients = state.ingredients.filterIndexed { position, _ -> position != index }) }
    fun addVariant(name: String, recipe: String, notes: String) {
        if (name.trim().isNotBlank()) draft.update { it.copy(variants = it.variants + DraftVariant(name = name.trim(), recipe = recipe, notes = notes)) }
    }
    fun removeVariant(index: Int) = draft.update { state -> state.copy(variants = state.variants.filterIndexed { position, _ -> position != index }) }
    fun addVariantIngredient(variantIndex: Int, name: String, quantity: String, unit: String) {
        if (name.trim().isNotBlank()) draft.update { state ->
            state.copy(variants = state.variants.mapIndexed { index, variant ->
                if (index == variantIndex) variant.copy(
                    ingredients = variant.ingredients + DraftIngredient(name = name.trim(), quantity = quantity, unit = unit),
                ) else variant
            })
        }
    }
    fun removeVariantIngredient(variantIndex: Int, ingredientIndex: Int) = draft.update { state ->
        state.copy(variants = state.variants.mapIndexed { index, variant ->
            if (index == variantIndex) variant.copy(ingredients = variant.ingredients.filterIndexed { position, _ -> position != ingredientIndex }) else variant
        })
    }

    fun save() = viewModelScope.launch {
        val current = draft.value
        if (current.name.trim().isBlank()) return@launch
        val saved = repository.saveDish(
            dishId = current.dishId,
            name = current.name,
            mealType = current.mealType,
            imageBase64 = current.imageBase64,
            recipe = current.recipe,
            preparationMinutes = current.preparationMinutes.toIntOrNull(),
            servings = current.servings.toIntOrNull(),
            notes = current.notes,
        )
        current.ingredients.forEachIndexed { index, ingredient ->
            repository.saveIngredient(ingredient.id, saved.id, ingredient.name, ingredient.quantity.toDoubleOrNull(), ingredient.unit, index)
        }
        current.variants.forEach { variant ->
            val savedVariant = repository.saveVariant(variant.id, saved.id, variant.name, variant.recipe, variant.notes)
            variant.ingredients.forEachIndexed { index, ingredient ->
                repository.saveIngredient(
                    ingredientId = ingredient.id,
                    dishId = saved.id,
                    name = ingredient.name,
                    quantity = ingredient.quantity.toDoubleOrNull(),
                    unit = ingredient.unit,
                    sortOrder = index,
                    variantId = savedVariant.id,
                )
            }
        }
        editorOpen.value = false
    }

    fun delete(dish: Dish) = viewModelScope.launch {
        repository.deleteDish(dish.id)
        editorOpen.value = false
    }
}
