package com.majyd.casa.ui.dishes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.Dish
import com.majyd.casa.data.model.DishMealType
import com.majyd.casa.data.model.DishUsageStats
import com.majyd.casa.data.model.DishCollection
import com.majyd.casa.data.model.RecipeCategory
import com.majyd.casa.data.repository.DishRepository
import com.majyd.casa.data.repository.WeeklyMenuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DishDraft(
    val dishId: String? = null,
    val name: String = "",
    val category: String = "",
    val mealType: DishMealType = DishMealType.BOTH,
    val imageBase64: String? = null,
    val recipe: String = "",
    val recipeSteps: String = "",
    val steps: List<String> = emptyList(),
    val preparationMinutes: String = "",
    val servings: String = "",
    val notes: String = "",
    val preparationRequired: Boolean = false,
    val ingredients: List<DraftIngredient> = emptyList(),
    val variants: List<DraftVariant> = emptyList(),
    val categoryIds: Set<String> = emptySet(),
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
    val detail: DishDetail? = null,
    val categories: List<RecipeCategory> = emptyList(),
)

data class DishDetail(
    val dish: Dish,
    val ingredients: List<DraftIngredient> = emptyList(),
    val variants: List<DraftVariant> = emptyList(),
    val usageStats: DishUsageStats = DishUsageStats(),
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
    private val detail = MutableStateFlow<DishDetail?>(null)
    private val collection = MutableStateFlow(DishCollection.MENU)
    private val categories = repository.observeRecipeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val dishes = collection.flatMapLatest { repository.observeDishes(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCollection(value: DishCollection) {
        collection.value = value
        if (value == DishCollection.RECIPE_BOOK) viewModelScope.launch { repository.ensureRecipeCategories() }
    }

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

    val uiState: StateFlow<DishesUiState> = combine(baseUiState, usageStats, detail, categories) { state, stats, detailValue, categoryItems ->
        state.copy(usageStats = stats, detail = detailValue, categories = categoryItems)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DishesUiState())

    init {
        viewModelScope.launch {
            dishes.collect { items ->
                usageStats.value = items.associate { it.id to weeklyMenuRepository.getUsageStats(it.id) }
            }
        }
    }

    fun openNew() {
        draft.value = DishDraft(preparationMinutes = "1", servings = "2")
        editorOpen.value = true
    }

    fun openDetail(dish: Dish) = viewModelScope.launch {
        val currentDish = repository.getDish(dish.id) ?: dish
        val ingredients = repository.getIngredients(currentDish.id).filterNot { it.deleted }
            .map { DraftIngredient(it.id, it.name, it.quantity?.toString().orEmpty(), it.unit) }
        val variants = repository.getVariants(currentDish.id).filterNot { it.deleted }.map { variant ->
            DraftVariant(
                id = variant.id,
                name = variant.name,
                recipe = variant.recipe,
                notes = variant.notes,
                ingredients = repository.getIngredients(currentDish.id, variant.id).filterNot { it.deleted }.map {
                    DraftIngredient(it.id, it.name, it.quantity?.toString().orEmpty(), it.unit)
                },
            )
        }
        detail.value = DishDetail(currentDish, ingredients, variants, usageStats.value[currentDish.id] ?: DishUsageStats())
    }

    fun closeDetail() { detail.value = null }

    fun edit(dish: Dish) {
        draft.value = DishDraft(
            dishId = dish.id,
            name = dish.name,
            category = dish.category,
            mealType = dish.mealType,
            imageBase64 = dish.imageBase64,
            recipe = dish.recipe,
            recipeSteps = dish.recipeSteps,
            steps = dish.recipeSteps.lines().map { it.trim() }.filter { it.isNotBlank() },
            preparationMinutes = (dish.preparationMinutes ?: 1).coerceAtLeast(1).toString(),
            servings = (dish.servings ?: 2).coerceAtLeast(1).toString(),
            notes = dish.notes,
            preparationRequired = dish.preparationRequired,
        )
        editorOpen.value = true
        viewModelScope.launch {
            val ingredients = repository.getIngredients(dish.id)
            draft.update { current ->
                if (current.dishId != dish.id) current else current.copy(
                    ingredients = ingredients.map { DraftIngredient(it.id, it.name, it.quantity?.toString().orEmpty(), it.unit) },
                )
            }
            if (collection.value == DishCollection.RECIPE_BOOK) {
                val categoryIds = repository.getRecipeCategoryIds(dish.id)
                draft.update { current -> if (current.dishId == dish.id) current.copy(categoryIds = categoryIds) else current }
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

    fun toggleCategory(categoryId: String) = draft.update { it.copy(categoryIds = if (categoryId in it.categoryIds) it.categoryIds - categoryId else it.categoryIds + categoryId) }
    fun saveCategory(name: String) = viewModelScope.launch { repository.saveRecipeCategory(null, name) }
    fun renameCategory(category: RecipeCategory, name: String) = viewModelScope.launch { repository.saveRecipeCategory(category.id, name) }
    fun deleteCategory(category: RecipeCategory) = viewModelScope.launch { repository.deleteRecipeCategory(category.id) }

    fun closeEditor() { editorOpen.value = false }
    fun updateQuery(value: String) { query.value = value }
    fun updateMealFilter(value: DishMealType?) { mealFilter.value = value }
    fun updateName(value: String) = draft.update { it.copy(name = value) }
    fun updateCategory(value: String) = draft.update { it.copy(category = value) }
    fun updateMealType(value: DishMealType) = draft.update { it.copy(mealType = value) }
    fun updateImage(value: String?) = draft.update { it.copy(imageBase64 = value) }
    fun updateRecipe(value: String) = draft.update { it.copy(recipe = value) }
    fun updateRecipeSteps(value: String) = draft.update { it.copy(recipeSteps = value) }
    fun addStep(value: String) {
        if (value.trim().isNotBlank()) draft.update { it.copy(steps = it.steps + value.trim()) }
    }
    fun updateStep(index: Int, value: String) = draft.update { state ->
        if (index !in state.steps.indices || value.trim().isBlank()) state
        else state.copy(steps = state.steps.mapIndexed { position, step -> if (position == index) value.trim() else step })
    }
    fun removeStep(index: Int) = draft.update { state ->
        state.copy(steps = state.steps.filterIndexed { position, _ -> position != index })
    }
    fun updatePreparationMinutes(value: String) = draft.update { it.copy(preparationMinutes = value) }
    fun updateServings(value: String) = draft.update { it.copy(servings = value) }
    fun updateNotes(value: String) = draft.update { it.copy(notes = value) }
    fun updatePreparationRequired(value: Boolean) = draft.update { it.copy(preparationRequired = value) }
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
            category = if (collection.value == DishCollection.RECIPE_BOOK && current.categoryIds.isNotEmpty()) {
                categories.value.filter { it.id in current.categoryIds }.joinToString(", ") { it.name }
            } else current.category,
            mealType = current.mealType,
            imageBase64 = current.imageBase64,
            recipe = current.recipe,
            recipeSteps = current.steps.joinToString("\n"),
            preparationMinutes = current.preparationMinutes.toIntOrNull(),
            servings = current.servings.toIntOrNull(),
            notes = current.notes,
            collection = collection.value,
            preparationRequired = current.preparationRequired,
        )
        current.ingredients.forEachIndexed { index, ingredient ->
            repository.saveIngredient(ingredient.id, saved.id, ingredient.name, ingredient.quantity.toDoubleOrNull(), ingredient.unit, index)
        }
        if (collection.value == DishCollection.RECIPE_BOOK) repository.setRecipeCategories(saved.id, current.categoryIds)
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
        detail.value = null
    }
}
