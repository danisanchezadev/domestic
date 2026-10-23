package com.majyd.casa.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DishSyncPayload(
    val id: String,
    val householdId: String,
    val name: String,
    val normalizedName: String,
    val mealType: String,
    val imageBase64: String?,
    val recipe: String,
    val preparationMinutes: Int?,
    val servings: Int?,
    val notes: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)

@Serializable
data class DishIngredientSyncPayload(
    val id: String,
    val householdId: String,
    val dishId: String,
    val name: String,
    val normalizedName: String,
    val quantity: Double?,
    val unit: String,
    val sortOrder: Int,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
    val variantId: String? = null,
)

@Serializable
data class DishVariantSyncPayload(
    val id: String,
    val householdId: String,
    val dishId: String,
    val name: String,
    val normalizedName: String,
    val recipe: String,
    val notes: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
)

object DishSyncPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encodeDish(value: DishSyncPayload): String = json.encodeToString(value)
    fun decodeDish(value: String): DishSyncPayload? = runCatching { json.decodeFromString<DishSyncPayload>(value) }.getOrNull()
    fun encodeIngredient(value: DishIngredientSyncPayload): String = json.encodeToString(value)
    fun decodeIngredient(value: String): DishIngredientSyncPayload? = runCatching { json.decodeFromString<DishIngredientSyncPayload>(value) }.getOrNull()
    fun encodeVariant(value: DishVariantSyncPayload): String = json.encodeToString(value)
    fun decodeVariant(value: String): DishVariantSyncPayload? = runCatching { json.decodeFromString<DishVariantSyncPayload>(value) }.getOrNull()
}
