package com.majyd.casa.data.repository

import com.majyd.casa.data.model.ShoppingCategory
import com.majyd.casa.data.model.ShoppingList
import com.majyd.casa.data.model.ShoppingProduct
import com.majyd.casa.data.model.ShoppingSuggestion
import kotlinx.coroutines.flow.Flow

interface ShoppingRepository {
    fun observeLists(): Flow<List<ShoppingList>>
    fun observeCategories(): Flow<List<ShoppingCategory>>
    fun observeProducts(listId: String): Flow<List<ShoppingProduct>>
    fun observeDeletedProducts(listId: String): Flow<List<ShoppingProduct>>
    fun observeSuggestions(query: String): Flow<List<ShoppingSuggestion>>
    suspend fun addCatalogItem(name: String)
    suspend fun updateCatalogItem(currentName: String, newName: String)
    suspend fun deleteCatalogItem(name: String)
    suspend fun ensureCatalogSyncOperations()
    suspend fun ensureDefaults()
    suspend fun createList(name: String): ShoppingList
    suspend fun addIngredientToActiveList(name: String, quantity: Double, unit: String): ShoppingProduct
    suspend fun upsertProduct(
        listId: String,
        productId: String?,
        name: String,
        categoryId: String?,
        quantity: Double,
        unit: String,
        notes: String,
        purchased: Boolean,
        imageBase64: String? = null,
    ): ShoppingProduct
    suspend fun togglePurchased(productId: String, purchased: Boolean): ShoppingProduct?
    suspend fun deleteProduct(productId: String): ShoppingProduct?
    suspend fun restoreProduct(productId: String): ShoppingProduct?
    suspend fun clearPurchased(listId: String)
}
