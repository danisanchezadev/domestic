package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.ShoppingCatalogDao
import com.majyd.casa.data.local.dao.ShoppingCategoryDao
import com.majyd.casa.data.local.dao.ShoppingListDao
import com.majyd.casa.data.local.dao.ShoppingProductDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.ShoppingCatalogEntity
import com.majyd.casa.data.local.entities.ShoppingCategoryEntity
import com.majyd.casa.data.local.entities.ShoppingListEntity
import com.majyd.casa.data.local.entities.ShoppingProductEntity
import com.majyd.casa.data.model.ShoppingCategory
import com.majyd.casa.data.model.ShoppingList
import com.majyd.casa.data.model.ShoppingProduct
import com.majyd.casa.data.model.ShoppingSuggestion
import com.majyd.casa.util.Normalizer
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.ShoppingListSyncPayload
import com.majyd.casa.sync.ShoppingProductSyncPayload
import com.majyd.casa.sync.ShoppingCatalogSyncPayload
import com.majyd.casa.sync.ShoppingSyncPayloadCodec
import com.majyd.casa.sync.SyncOperationRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class RoomShoppingRepository @Inject constructor(
    private val database: CasaDatabase,
    private val listDao: ShoppingListDao,
    private val categoryDao: ShoppingCategoryDao,
    private val productDao: ShoppingProductDao,
    private val catalogDao: ShoppingCatalogDao,
    private val syncOperationRepository: SyncOperationRepository,
    private val deviceManager: DeviceManager,
) : ShoppingRepository {

    override fun observeLists(): Flow<List<ShoppingList>> =
        listDao.observeLists().map { items -> items.map { it.toModel() } }

    override fun observeCategories(): Flow<List<ShoppingCategory>> =
        categoryDao.observeCategories().map { items -> items.map { it.toModel() } }

    override fun observeProducts(listId: String): Flow<List<ShoppingProduct>> =
        productDao.observeProducts(listId).map { items -> items.map { it.toModel() } }

    override fun observeDeletedProducts(listId: String): Flow<List<ShoppingProduct>> =
        productDao.observeDeletedProducts(listId).map { items -> items.map { it.toModel() } }

    override fun observeSuggestions(query: String): Flow<List<ShoppingSuggestion>> =
        catalogDao.observeSuggestions(query).map { items ->
            items.map {
                ShoppingSuggestion(
                    normalizedName = it.normalizedName,
                    displayName = it.displayName,
                    categoryId = it.categoryId,
                    preferredUnit = it.preferredUnit,
                    preferredQuantity = it.preferredQuantity,
                    usageCount = it.usageCount,
                    lastUsedAt = it.lastUsedAt,
                )
            }
        }

    override suspend fun addCatalogItem(name: String) = database.withTransaction {
        val cleanName = name.trim()
        val normalizedName = Normalizer.normalizeText(cleanName)
        if (normalizedName.isBlank()) return@withTransaction
        val existing = catalogDao.getByNameIncludingDeleted(normalizedName)
        if (existing != null) {
            if (existing.deleted) {
                val restored = existing.copy(deleted = false, updatedAt = System.currentTimeMillis())
                catalogDao.upsert(restored)
                enqueueCatalog(restored, "RESTORE")
            }
            return@withTransaction
        }
        val now = System.currentTimeMillis()
        val device = deviceManager.ensureDevice()
        val entry = ShoppingCatalogEntity(
                id = UUID.randomUUID().toString(),
                normalizedName = normalizedName,
                displayName = cleanName,
                categoryId = null,
                preferredUnit = "",
                preferredQuantity = 1.0,
                usageCount = 0,
                lastUsedAt = null,
                createdAt = now,
                updatedAt = now,
                deleted = false,
                updatedByDevice = device.deviceId,
            )
        catalogDao.upsert(entry)
        enqueueCatalog(entry, "CREATE")
    }

    override suspend fun updateCatalogItem(currentName: String, newName: String) = database.withTransaction {
        val cleanName = newName.trim()
        val normalizedName = Normalizer.normalizeText(cleanName)
        if (normalizedName.isBlank()) return@withTransaction
        val current = catalogDao.getByNameIncludingDeleted(currentName) ?: return@withTransaction
        val duplicate = catalogDao.getByNameIncludingDeleted(normalizedName)
        if (duplicate != null && duplicate.id != current.id) {
            if (!duplicate.deleted) return@withTransaction
            val now = System.currentTimeMillis()
            val device = deviceManager.ensureDevice()
            catalogDao.upsert(current.copy(deleted = true, updatedAt = now, updatedByDevice = device.deviceId))
            enqueueCatalog(current.copy(deleted = true, updatedAt = now, updatedByDevice = device.deviceId), "DELETE")
            catalogDao.upsert(
                duplicate.copy(
                    displayName = cleanName,
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                    updatedByDevice = device.deviceId,
                )
            )
            enqueueCatalog(duplicate.copy(displayName = cleanName, updatedAt = now, deleted = false, updatedByDevice = device.deviceId), "UPDATE")
            return@withTransaction
        }
        val updated = current.copy(
                normalizedName = normalizedName,
                displayName = cleanName,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                updatedByDevice = deviceManager.ensureDevice().deviceId,
            )
        catalogDao.upsert(updated)
        enqueueCatalog(updated, "UPDATE")
    }

    override suspend fun deleteCatalogItem(name: String) = database.withTransaction {
        val current = catalogDao.getByNameIncludingDeleted(Normalizer.normalizeText(name)) ?: return@withTransaction
        val deleted = current.copy(deleted = true, updatedAt = System.currentTimeMillis(), updatedByDevice = deviceManager.ensureDevice().deviceId)
        catalogDao.upsert(deleted)
        enqueueCatalog(deleted, "DELETE")
    }

    override suspend fun ensureCatalogSyncOperations() {
        val device = deviceManager.ensureDevice()
        val existingOperationEntityIds = syncOperationRepository
            .observeAll(device.householdId)
            .first()
            .asSequence()
            .filter { it.entityType == "shopping_catalog" }
            .map { it.entityId }
            .toSet()
        catalogDao.getAllIncludingDeleted()
            .filterNot { it.id in existingOperationEntityIds }
            .forEach { enqueueCatalog(it, if (it.deleted) "DELETE" else "CREATE") }
    }

    override suspend fun ensureDefaults() = database.withTransaction {
        val device = deviceManager.ensureDevice()
        if (listDao.getById(DEFAULT_LIST_ID) == null) {
            val now = System.currentTimeMillis()
            listDao.upsert(
                ShoppingListEntity(
                    id = DEFAULT_LIST_ID,
                    name = "Lista de la compra",
                    sortOrder = 0,
                    deleted = false,
                    version = 1,
                    createdAt = now,
                    updatedAt = now,
                    updatedByDevice = device.deviceId,
                )
            )
        }

        if (categoryDao.getCategories().isEmpty()) {
            val now = System.currentTimeMillis()
            defaultCategories.forEachIndexed { index, category ->
                categoryDao.upsert(
                    ShoppingCategoryEntity(
                        id = UUID.randomUUID().toString(),
                        name = category,
                        normalizedName = Normalizer.normalizeText(category),
                        icon = "*",
                        color = null,
                        sortOrder = index,
                        hidden = false,
                        deleted = false,
                        version = 1,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
    }

    override suspend fun createList(name: String): ShoppingList = database.withTransaction {
        val now = System.currentTimeMillis()
        val entity = ShoppingListEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            sortOrder = listDao.getLists().size,
            deleted = false,
            version = 1,
            createdAt = now,
            updatedAt = now,
            updatedByDevice = deviceManager.ensureDevice().deviceId,
        )
        listDao.upsert(entity)
        enqueueList(entity, "CREATE", deviceManager.ensureDevice().householdId)
        entity.toModel()
    }

    override suspend fun addIngredientToDefaultList(name: String, quantity: Double, unit: String): ShoppingProduct {
        val cleanName = name.trim()
        val normalizedName = Normalizer.normalizeText(cleanName)
        require(normalizedName.isNotBlank()) { "El ingrediente no puede estar vacio" }
        ensureDefaults()
        val existing = productDao.getActiveByName(DEFAULT_LIST_ID, normalizedName)
        if (existing != null) {
            return existing.toModel()
        }
        return upsertProduct(
            listId = DEFAULT_LIST_ID,
            productId = null,
            name = cleanName,
            categoryId = null,
            quantity = quantity,
            unit = unit,
            notes = "",
            purchased = false,
        )
    }

    override suspend fun upsertProduct(
        listId: String,
        productId: String?,
        name: String,
        categoryId: String?,
        quantity: Double,
        unit: String,
        notes: String,
        purchased: Boolean,
        imageBase64: String?,
    ): ShoppingProduct {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val device = deviceManager.ensureDevice()
            val current = productId?.let { productDao.getById(it) }
            val entity = ShoppingProductEntity(
                id = current?.id ?: UUID.randomUUID().toString(),
                listId = listId,
                name = name.trim(),
                normalizedName = Normalizer.normalizeText(name),
                categoryId = categoryId,
                quantity = quantity,
                unit = unit.trim(),
                notes = notes.trim(),
                purchased = purchased,
                purchasedAt = if (purchased) current?.purchasedAt ?: now else null,
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                createdByDevice = current?.createdByDevice ?: device.deviceId,
                updatedByDevice = device.deviceId,
                deleted = false,
                version = (current?.version ?: 0) + 1,
                imageBase64 = imageBase64 ?: current?.imageBase64,
            )
            productDao.upsert(entity)
            updateCatalog(entity)
            enqueueProduct(entity, if (current == null) "CREATE" else "UPDATE", device.householdId)
            entity.toModel()
        }
    }

    override suspend fun togglePurchased(productId: String, purchased: Boolean): ShoppingProduct? {
        val current = findProduct(productId) ?: return null
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val device = deviceManager.ensureDevice()
            val updated = current.copy(
                purchased = purchased,
                purchasedAt = if (purchased) current.purchasedAt ?: now else null,
                updatedAt = now,
                version = current.version + 1,
            )
            productDao.upsert(updated)
            updateCatalog(updated)
            enqueueProduct(updated, "UPDATE", device.householdId)
            updated.toModel()
        }
    }

    override suspend fun deleteProduct(productId: String): ShoppingProduct? {
        val current = findProduct(productId) ?: return null
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val device = deviceManager.ensureDevice()
            val updated = current.copy(
                deleted = true,
                updatedAt = now,
                version = current.version + 1,
            )
            productDao.upsert(updated)
            enqueueProduct(updated, "DELETE", device.householdId)
            updated.toModel()
        }
    }

    override suspend fun restoreProduct(productId: String): ShoppingProduct? {
        val current = findProduct(productId) ?: return null
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val device = deviceManager.ensureDevice()
            val updated = current.copy(
                deleted = false,
                updatedAt = now,
                version = current.version + 1,
            )
            productDao.upsert(updated)
            enqueueProduct(updated, "RESTORE", device.householdId)
            updated.toModel()
        }
    }

    override suspend fun clearPurchased(listId: String) {
        database.withTransaction {
            val device = deviceManager.ensureDevice()
            productDao.getProducts(listId)
                .filter { it.purchased }
                .forEach {
                    val now = System.currentTimeMillis()
                    productDao.upsert(
                        it.copy(
                            deleted = true,
                            updatedAt = now,
                            version = it.version + 1,
                        )
                    )
                    enqueueProduct(productDao.getById(it.id) ?: it, "DELETE", device.householdId)
                }
        }
    }

    private suspend fun enqueueList(entity: ShoppingListEntity, operationType: String, householdId: String) {
        syncOperationRepository.enqueue(
            householdId = householdId,
            deviceId = deviceManager.ensureDevice().deviceId,
            entityType = "shopping_list",
            entityId = entity.id,
            payload = ShoppingSyncPayloadCodec.encodeList(ShoppingListSyncPayload(entity.id, householdId, entity.name, entity.sortOrder, entity.deleted, entity.version, entity.createdAt, entity.updatedAt, entity.updatedByDevice)),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = operationType,
        )
    }

    private suspend fun enqueueProduct(entity: ShoppingProductEntity, operationType: String, householdId: String) {
        val device = deviceManager.ensureDevice()
        // A delete only needs the tombstone. Avoid retransmitting a potentially
        // large photo so the delete cannot be rejected because of its payload.
        val imageBase64 = if (operationType == "DELETE") null else entity.imageBase64
        syncOperationRepository.enqueue(
            householdId = householdId,
            deviceId = device.deviceId,
            entityType = "shopping_product",
            entityId = entity.id,
            payload = ShoppingSyncPayloadCodec.encodeProduct(ShoppingProductSyncPayload(entity.id, householdId, entity.listId, entity.name, entity.normalizedName, entity.categoryId, entity.quantity, entity.unit, entity.notes, entity.purchased, entity.purchasedAt, entity.createdAt, entity.updatedAt, entity.createdByDevice, entity.updatedByDevice, entity.deleted, entity.version, imageBase64)),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = operationType,
        )
    }

    private suspend fun enqueueCatalog(entity: ShoppingCatalogEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(
            householdId = device.householdId,
            deviceId = device.deviceId,
            entityType = "shopping_catalog",
            entityId = entity.id,
            payload = ShoppingSyncPayloadCodec.encodeCatalog(
                ShoppingCatalogSyncPayload(
                    entity.id,
                    device.householdId,
                    entity.normalizedName,
                    entity.displayName,
                    entity.categoryId,
                    entity.preferredUnit,
                    entity.preferredQuantity,
                    entity.usageCount,
                    entity.lastUsedAt,
                    entity.createdAt,
                    entity.updatedAt,
                    entity.deleted,
                    entity.updatedByDevice,
                )
            ),
            version = entity.updatedAt,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = operationType,
        )
    }

    private suspend fun findProduct(productId: String): ShoppingProductEntity? = productDao.getById(productId)

    private suspend fun updateCatalog(product: ShoppingProductEntity) {
        val existing = catalogDao.getByNameIncludingDeleted(product.normalizedName)
        val now = System.currentTimeMillis()
        val updated = ShoppingCatalogEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                normalizedName = product.normalizedName,
                displayName = product.name,
                categoryId = product.categoryId,
                preferredUnit = product.unit,
                preferredQuantity = product.quantity,
                usageCount = (existing?.usageCount ?: 0) + 1,
                lastUsedAt = now,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deleted = false,
                updatedByDevice = product.updatedByDevice,
            )
        catalogDao.upsert(updated)
        enqueueCatalog(updated, "UPDATE")
    }

    private fun ShoppingListEntity.toModel(): ShoppingList = ShoppingList(
        id = id,
        name = name,
        sortOrder = sortOrder,
        deleted = deleted,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ShoppingCategoryEntity.toModel(): ShoppingCategory = ShoppingCategory(
        id = id,
        name = name,
        normalizedName = normalizedName,
        icon = icon,
        color = color,
        sortOrder = sortOrder,
        hidden = hidden,
        deleted = deleted,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ShoppingProductEntity.toModel(): ShoppingProduct = ShoppingProduct(
        id = id,
        listId = listId,
        name = name,
        normalizedName = normalizedName,
        categoryId = categoryId,
        quantity = quantity,
        unit = unit,
        notes = notes,
        purchased = purchased,
        purchasedAt = purchasedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdByDevice = createdByDevice,
        updatedByDevice = updatedByDevice,
        deleted = deleted,
        version = version,
        imageBase64 = imageBase64,
    )

    private fun ShoppingProduct.toEntity(): ShoppingProductEntity = ShoppingProductEntity(
        id = id,
        listId = listId,
        name = name,
        normalizedName = normalizedName,
        categoryId = categoryId,
        quantity = quantity,
        unit = unit,
        notes = notes,
        purchased = purchased,
        purchasedAt = purchasedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdByDevice = createdByDevice,
        updatedByDevice = updatedByDevice,
        deleted = deleted,
        version = version,
        imageBase64 = imageBase64,
    )

    companion object {
        const val DEFAULT_LIST_ID = "shopping-default"

        private val defaultCategories = listOf(
            "Fruta y verdura",
            "Carne",
            "Pescado",
            "Lacteos",
            "Panaderia",
            "Bebidas",
            "Congelados",
            "Conservas",
            "Desayuno",
            "Limpieza",
            "Higiene",
            "Hogar",
            "Otros",
        )
    }
}
