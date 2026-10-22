package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ShoppingListSyncPayload(
    val id: String,
    val householdId: String,
    val name: String,
    val sortOrder: Int,
    val deleted: Boolean,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val updatedByDevice: String = "",
)

@Serializable
data class ShoppingProductSyncPayload(
    val id: String,
    val householdId: String,
    val listId: String,
    val name: String,
    val normalizedName: String,
    val categoryId: String?,
    val quantity: Double,
    val unit: String,
    val notes: String,
    val purchased: Boolean,
    val purchasedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val createdByDevice: String,
    val updatedByDevice: String,
    val deleted: Boolean,
    val version: Long,
    val imageBase64: String? = null,
)

@Serializable
data class ShoppingCatalogSyncPayload(
    val id: String,
    val householdId: String,
    val normalizedName: String,
    val displayName: String,
    val categoryId: String?,
    val preferredUnit: String,
    val preferredQuantity: Double?,
    val usageCount: Int,
    val lastUsedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val updatedByDevice: String = "",
)

object ShoppingSyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeList(payload: ShoppingListSyncPayload): String = json.encodeToString<ShoppingListSyncPayload>(payload)
    fun decodeList(value: String): ShoppingListSyncPayload? = runCatching { json.decodeFromString<ShoppingListSyncPayload>(value) }.getOrNull()
    fun encodeProduct(payload: ShoppingProductSyncPayload): String = json.encodeToString<ShoppingProductSyncPayload>(payload)
    fun decodeProduct(value: String): ShoppingProductSyncPayload? = runCatching { json.decodeFromString<ShoppingProductSyncPayload>(value) }.getOrNull()
    fun encodeCatalog(payload: ShoppingCatalogSyncPayload): String = json.encodeToString<ShoppingCatalogSyncPayload>(payload)
    fun decodeCatalog(value: String): ShoppingCatalogSyncPayload? = runCatching { json.decodeFromString<ShoppingCatalogSyncPayload>(value) }.getOrNull()
}
