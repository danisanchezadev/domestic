package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.ContactDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.ContactEntity
import com.majyd.casa.data.local.entities.ContactCategoryEntity
import com.majyd.casa.data.model.ContactCategoryItem
import com.majyd.casa.data.model.ContactItem
import com.majyd.casa.data.model.contactCategories
import com.majyd.casa.sync.ContactSyncPayload
import com.majyd.casa.sync.ContactSyncPayloadCodec
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.util.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class RoomContactRepository @Inject constructor(
    private val database: CasaDatabase,
    private val contactDao: ContactDao,
    private val categoryDao: com.majyd.casa.data.local.dao.ContactCategoryDao,
    private val deviceManager: DeviceManager,
    private val syncOperationRepository: SyncOperationRepository,
) : ContactRepository {
    override fun observeCategories(): Flow<List<ContactCategoryItem>> = categoryDao.observeActive().map { it.map(ContactCategoryEntity::toModel) }

    override suspend fun ensureDefaultCategories() = database.withTransaction {
        val device = deviceManager.ensureDevice()
        contactCategories.forEach { name ->
            if (categoryDao.getByNormalizedName(Normalizer.normalizeText(name)) == null) {
                val now = System.currentTimeMillis()
                val normalizedName = Normalizer.normalizeText(name)
                val entity = ContactCategoryEntity("default-contact-category-$normalizedName", device.householdId, name, normalizedName, false, now, now, 1, device.deviceId)
                categoryDao.upsert(entity)
                enqueueCategory(entity, "CREATE")
            }
        }
    }
    override fun observeContacts(): Flow<List<ContactItem>> =
        contactDao.observeAll().map { contacts -> contacts.map(ContactEntity::toModel) }

    override suspend fun saveContact(
        contactId: String?,
        category: String,
        name: String,
        phone: String,
    ): ContactItem = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val current = if (contactId != null) contactDao.getById(contactId) else null
        val now = System.currentTimeMillis()
        val entity = ContactEntity(
            id = current?.id ?: UUID.randomUUID().toString(),
            householdId = device.householdId,
            category = category.trim().ifBlank { "Otros" },
            name = name.trim(),
            normalizedName = Normalizer.normalizeText(name),
            phone = phone.trim(),
            deleted = false,
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
            version = (current?.version ?: 0) + 1,
            updatedByDevice = device.deviceId,
        )
        contactDao.upsert(entity)
        enqueue(entity, if (current == null) "CREATE" else "UPDATE")
        entity.toModel()
    }

    override suspend fun deleteContact(contactId: String): ContactItem? {
        val current = contactDao.getById(contactId) ?: return null
        return database.withTransaction {
            val device = deviceManager.ensureDevice()
            val updated = current.copy(
                deleted = true,
                updatedAt = System.currentTimeMillis(),
                version = current.version + 1,
                updatedByDevice = device.deviceId,
            )
            contactDao.upsert(updated)
            enqueue(updated, "DELETE")
            updated.toModel()
        }
    }

    override suspend fun saveCategory(categoryId: String?, name: String): ContactCategoryItem? = database.withTransaction {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withTransaction null
        val device = deviceManager.ensureDevice()
        val current = if (categoryId != null) categoryDao.getById(categoryId) else null
        if (current?.name == "Otros" && cleanName != "Otros") return@withTransaction null
        val duplicate = categoryDao.getByNormalizedName(Normalizer.normalizeText(cleanName))
        if (duplicate != null && duplicate.id != categoryId) return@withTransaction null
        val now = System.currentTimeMillis()
        val entity = ContactCategoryEntity(current?.id ?: UUID.randomUUID().toString(), device.householdId, cleanName, Normalizer.normalizeText(cleanName), false, current?.createdAt ?: now, now, (current?.version ?: 0) + 1, device.deviceId)
        categoryDao.upsert(entity)
        enqueueCategory(entity, if (current == null) "CREATE" else "UPDATE")
        entity.toModel()
    }

    override suspend fun deleteCategory(categoryId: String) = database.withTransaction {
        val current = categoryDao.getById(categoryId) ?: return@withTransaction
        if (current.name == "Otros") return@withTransaction
        val device = deviceManager.ensureDevice()
        val fallback = categoryDao.getByNormalizedName(Normalizer.normalizeText("Otros"))
        val contacts = contactDao.observeAll().first()
        contacts.filter { it.category == current.name }.forEach {
            val updated = it.copy(category = fallback?.name ?: "Otros", normalizedName = Normalizer.normalizeText(fallback?.name ?: "Otros"), updatedAt = System.currentTimeMillis(), version = it.version + 1, updatedByDevice = device.deviceId)
            contactDao.upsert(updated)
            enqueue(updated, "UPDATE")
        }
        val deleted = current.copy(deleted = true, updatedAt = System.currentTimeMillis(), version = current.version + 1, updatedByDevice = device.deviceId)
        categoryDao.upsert(deleted)
        enqueueCategory(deleted, "DELETE")
    }

    private suspend fun enqueue(entity: ContactEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(
            householdId = entity.householdId,
            deviceId = device.deviceId,
            entityType = "contact",
            entityId = entity.id,
            payload = ContactSyncPayloadCodec.encode(
                ContactSyncPayload(
                    id = entity.id,
                    householdId = entity.householdId,
                    category = entity.category,
                    name = entity.name,
                    normalizedName = entity.normalizedName,
                    phone = entity.phone,
                    deleted = entity.deleted,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    version = entity.version,
                    updatedByDevice = entity.updatedByDevice,
                )
            ),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = operationType,
        )
    }

    private suspend fun enqueueCategory(entity: ContactCategoryEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(
            householdId = entity.householdId,
            deviceId = device.deviceId,
            entityType = "contact_category",
            entityId = entity.id,
            payload = com.majyd.casa.sync.ContactCategorySyncPayloadCodec.encode(com.majyd.casa.sync.ContactCategorySyncPayload(entity.id, entity.householdId, entity.name, entity.normalizedName, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            operationType = operationType,
        )
    }
}
