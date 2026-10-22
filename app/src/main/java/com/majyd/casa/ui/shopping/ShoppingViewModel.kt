package com.majyd.casa.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.ShoppingCategory
import com.majyd.casa.data.model.ShoppingList
import com.majyd.casa.data.model.ShoppingProduct
import com.majyd.casa.data.model.ShoppingSuggestion
import com.majyd.casa.data.repository.ShoppingRepository
import com.majyd.casa.util.Normalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShoppingSortMode {
    DEFAULT,
    CATEGORY,
    ALPHABETICAL,
}

data class ShoppingDraft(
    val productId: String? = null,
    val name: String = "",
    val categoryId: String? = null,
    val quantity: String = "1",
    val unit: String = "",
    val notes: String = "",
    val purchased: Boolean = false,
    val imageBase64: String? = null,
)

data class ShoppingUiState(
    val lists: List<ShoppingList> = emptyList(),
    val categories: List<ShoppingCategory> = emptyList(),
    val pending: List<ShoppingProduct> = emptyList(),
    val purchased: List<ShoppingProduct> = emptyList(),
    val deleted: List<ShoppingProduct> = emptyList(),
    val suggestions: List<ShoppingSuggestion> = emptyList(),
    val selectedListId: String = "",
    val selectedCategoryId: String? = null,
    val query: String = "",
    val sortMode: ShoppingSortMode = ShoppingSortMode.DEFAULT,
    val draft: ShoppingDraft = ShoppingDraft(),
    val newListName: String = "",
    val message: String? = null,
)

data class ShoppingControls(
    val selectedListId: String = "shopping-default",
    val selectedCategoryId: String? = null,
    val query: String = "",
    val sortMode: ShoppingSortMode = ShoppingSortMode.DEFAULT,
    val draft: ShoppingDraft = ShoppingDraft(),
    val newListName: String = "",
)

data class ShoppingContent(
    val lists: List<ShoppingList> = emptyList(),
    val categories: List<ShoppingCategory> = emptyList(),
    val pending: List<ShoppingProduct> = emptyList(),
    val purchased: List<ShoppingProduct> = emptyList(),
    val deleted: List<ShoppingProduct> = emptyList(),
    val suggestions: List<ShoppingSuggestion> = emptyList(),
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
) : ViewModel() {

    private val controls = MutableStateFlow(ShoppingControls())

    val lists: StateFlow<List<ShoppingList>> = shoppingRepository.observeLists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val categories: StateFlow<List<ShoppingCategory>> = shoppingRepository.observeCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val selectedListId = controls.map { it.selectedListId }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = controls.value.selectedListId,
    )

    private val selectedCategoryId = controls.map { it.selectedCategoryId }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = controls.value.selectedCategoryId,
    )

    private val query = controls.map { it.query }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = controls.value.query,
    )

    private val sortMode = controls.map { it.sortMode }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = controls.value.sortMode,
    )

    private val draft = controls.map { it.draft }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = controls.value.draft,
    )

    private val newListName = controls.map { it.newListName }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = controls.value.newListName,
    )

    private val products = selectedListId.flatMapLatest { listId ->
        shoppingRepository.observeProducts(listId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val deletedProducts = selectedListId.flatMapLatest { listId ->
        shoppingRepository.observeDeletedProducts(listId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val suggestions = query
        .map { Normalizer.normalizeText(it) }
        .flatMapLatest { normalized ->
            shoppingRepository.observeSuggestions(normalized)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val contentState: StateFlow<ShoppingContent> = combine(
        lists,
        categories,
        products,
        deletedProducts,
        suggestions,
    ) { lists, categories, items, deleted, suggestions ->
        ShoppingContent(
            lists = lists,
            categories = categories,
            pending = items.filterNot { it.purchased },
            purchased = items.filter { it.purchased },
            deleted = deleted,
            suggestions = suggestions,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShoppingContent(),
    )

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ShoppingUiState> = combine(
        contentState,
        controls,
        message,
    ) { content, controls, messageValue ->
        ShoppingUiState(
            lists = content.lists,
            categories = content.categories,
            pending = content.pending,
            purchased = content.purchased,
            deleted = content.deleted,
            suggestions = content.suggestions,
            selectedListId = controls.selectedListId,
            selectedCategoryId = controls.selectedCategoryId,
            query = controls.query,
            sortMode = controls.sortMode,
            draft = controls.draft,
            newListName = controls.newListName,
            message = messageValue,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShoppingUiState(),
    )

    fun selectList(listId: String) {
        controls.update {
            it.copy(
                selectedListId = listId,
                draft = it.draft.copy(productId = null),
            )
        }
    }

    fun addProduct(name: String, imageBase64: String? = null) = viewModelScope.launch {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            message.value = "Escribe un producto."
            return@launch
        }
        val duplicate = products.value.firstOrNull {
            !it.deleted && Normalizer.normalizeText(it.name) == Normalizer.normalizeText(cleanName)
        }
        if (duplicate != null) {
            // A previous product may exist without its catalog entry due to older data.
            shoppingRepository.addCatalogItem(cleanName)
            message.value = "${duplicate.name} ya esta en la lista."
            return@launch
        }
        shoppingRepository.upsertProduct(
            listId = controls.value.selectedListId,
            productId = null,
            name = cleanName,
            categoryId = null,
            quantity = 1.0,
            unit = "",
            notes = "",
            purchased = false,
            imageBase64 = imageBase64,
        )
        message.value = "$cleanName añadido a la lista."
    }

    fun addCatalogItem(name: String) = viewModelScope.launch {
        shoppingRepository.addCatalogItem(name)
    }

    fun addSuggestionToList(suggestion: ShoppingSuggestion) {
        addProduct(suggestion.displayName)
    }

    fun updateCatalogItem(currentName: String, newName: String) = viewModelScope.launch {
        shoppingRepository.updateCatalogItem(currentName, newName)
    }

    fun deleteCatalogItem(name: String) = viewModelScope.launch {
        shoppingRepository.deleteCatalogItem(name)
    }

    fun clearMessage() { message.value = null }

    fun setCategoryFilter(categoryId: String?) {
        controls.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun setQuery(value: String) {
        controls.update { it.copy(query = value) }
    }

    fun setSortMode(value: ShoppingSortMode) {
        controls.update { it.copy(sortMode = value) }
    }

    fun setNewListName(value: String) {
        controls.update { it.copy(newListName = value) }
    }

    fun createList() = viewModelScope.launch {
        val name = controls.value.newListName.trim()
        if (name.isNotBlank()) {
            val created = shoppingRepository.createList(name)
            controls.update {
                it.copy(
                    selectedListId = created.id,
                    newListName = "",
                )
            }
        }
    }

    fun editProduct(product: ShoppingProduct) {
        controls.update {
            it.copy(
                draft = ShoppingDraft(
                    productId = product.id,
                    name = product.name,
                    categoryId = product.categoryId,
                    quantity = product.quantity.toString(),
                    unit = product.unit,
                    notes = product.notes,
                    purchased = product.purchased,
                    imageBase64 = product.imageBase64,
                ),
            )
        }
    }

    fun applySuggestion(suggestion: ShoppingSuggestion) {
        controls.update {
            it.copy(
                draft = it.draft.copy(
                    name = suggestion.displayName,
                    categoryId = suggestion.categoryId,
                    quantity = suggestion.preferredQuantity?.toString() ?: it.draft.quantity,
                    unit = suggestion.preferredUnit,
                ),
            )
        }
    }

    fun updateDraftName(value: String) = controls.update { it.copy(draft = it.draft.copy(name = value)) }
    fun updateDraftCategory(value: String?) = controls.update { it.copy(draft = it.draft.copy(categoryId = value)) }
    fun updateDraftQuantity(value: String) = controls.update { it.copy(draft = it.draft.copy(quantity = value)) }
    fun updateDraftUnit(value: String) = controls.update { it.copy(draft = it.draft.copy(unit = value)) }
    fun updateDraftNotes(value: String) = controls.update { it.copy(draft = it.draft.copy(notes = value)) }
    fun updateDraftPurchased(value: Boolean) = controls.update { it.copy(draft = it.draft.copy(purchased = value)) }
    fun updateDraftImage(value: String?) = controls.update { it.copy(draft = it.draft.copy(imageBase64 = value)) }
    fun clearDraft() = controls.update { it.copy(draft = ShoppingDraft()) }

    fun saveDraft() = viewModelScope.launch {
        val current = controls.value.draft
        val quantity = current.quantity.toDoubleOrNull() ?: 1.0
        val saved = shoppingRepository.upsertProduct(
            listId = controls.value.selectedListId,
            productId = current.productId,
            name = current.name,
            categoryId = current.categoryId,
            quantity = quantity,
            unit = current.unit,
            notes = current.notes,
            purchased = current.purchased,
            imageBase64 = current.imageBase64,
        )
        controls.update { it.copy(draft = ShoppingDraft()) }
        if (saved.purchased) {
            controls.update { it.copy(query = it.query) }
        }
    }

    fun togglePurchased(product: ShoppingProduct) = viewModelScope.launch {
        shoppingRepository.togglePurchased(product.id, !product.purchased)
    }

    fun deleteProduct(product: ShoppingProduct) = viewModelScope.launch {
        shoppingRepository.deleteProduct(product.id)
    }

    fun restoreProduct(product: ShoppingProduct) = viewModelScope.launch {
        shoppingRepository.restoreProduct(product.id)
    }

    fun clearPurchased() = viewModelScope.launch {
        shoppingRepository.clearPurchased(controls.value.selectedListId)
    }

    private fun filterAndSort(
        items: List<ShoppingProduct>,
        queryValue: String,
        categoryFilter: String?,
        sort: ShoppingSortMode,
    ): List<ShoppingProduct> {
        val normalizedQuery = Normalizer.normalizeText(queryValue)
        val filtered = items.filter { product ->
            (normalizedQuery.isBlank() || product.normalizedName.contains(normalizedQuery)) &&
                (categoryFilter == null || product.categoryId == categoryFilter)
        }
        return when (sort) {
            ShoppingSortMode.DEFAULT -> filtered.sortedWith(
                compareBy<ShoppingProduct> { it.purchased }
                    .thenByDescending { it.updatedAt }
            )
            ShoppingSortMode.CATEGORY -> filtered.sortedWith(
                compareBy<ShoppingProduct> { it.categoryId ?: "" }
                    .thenBy { it.name.lowercase() }
            )
            ShoppingSortMode.ALPHABETICAL -> filtered.sortedBy { it.name.lowercase() }
        }
    }
}
