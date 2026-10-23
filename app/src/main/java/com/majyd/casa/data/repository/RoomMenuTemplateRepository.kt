package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.MenuTemplateDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.MenuTemplateEntity
import com.majyd.casa.data.local.entities.MenuTemplateItemEntity
import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.MenuTemplate
import com.majyd.casa.data.model.MenuTemplateItem
import com.majyd.casa.data.model.WeeklyMenuItem
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.MenuTemplateItemSyncPayload
import com.majyd.casa.sync.MenuTemplateSyncPayload
import com.majyd.casa.sync.MenuTemplateSyncPayloadCodec
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.util.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomMenuTemplateRepository @Inject constructor(
    private val database: CasaDatabase,
    private val dao: MenuTemplateDao,
    private val deviceManager: DeviceManager,
    private val syncOperationRepository: SyncOperationRepository,
) : MenuTemplateRepository {
    override fun observeTemplates(): Flow<List<MenuTemplate>> = dao.observeTemplates().map { templates ->
        templates.map { template ->
            MenuTemplate(template.id, template.name, emptyList())
        }
    }

    override suspend fun saveTemplate(name: String, items: List<WeeklyMenuItem>): MenuTemplate = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val now = System.currentTimeMillis()
        val template = MenuTemplateEntity(
            id = UUID.randomUUID().toString(), householdId = device.householdId,
            name = name.trim(), normalizedName = Normalizer.normalizeText(name),
            deleted = false, createdAt = now, updatedAt = now, version = 1,
            updatedByDevice = device.deviceId,
        )
        dao.upsertTemplate(template)
        enqueueTemplate(template)
        items.forEach { item ->
            val entity = MenuTemplateItemEntity(template.id, item.dayOfWeek, item.mealSlot.name, item.dishId, item.variantId, item.title, item.notes, false, now, 1, device.deviceId)
            dao.upsertItem(entity)
            enqueueItem(entity, template.householdId)
        }
        MenuTemplate(template.id, template.name, items.map { it.toTemplateItem() })
    }

    override suspend fun deleteTemplate(templateId: String) {
        val device = deviceManager.ensureDevice()
        val current = dao.getById(templateId) ?: return
        val updated = current.copy(
            deleted = true,
            updatedAt = System.currentTimeMillis(),
            version = current.version + 1,
            updatedByDevice = device.deviceId,
        )
        dao.upsertTemplate(updated)
        enqueueTemplate(updated)
    }

    override suspend fun loadTemplate(templateId: String): List<MenuTemplateItemValue> =
        dao.getItems(templateId).map { item ->
            MenuTemplateItemValue(item.dayOfWeek, runCatching { MealSlot.valueOf(item.mealSlot) }.getOrDefault(MealSlot.LUNCH), item.dishId, item.variantId, item.title, item.notes)
        }

    private fun WeeklyMenuItem.toTemplateItem() = MenuTemplateItem(dayOfWeek, mealSlot, dishId, variantId, title, notes)

    private suspend fun enqueueTemplate(entity: MenuTemplateEntity) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(
            householdId = entity.householdId,
            deviceId = device.deviceId,
            entityType = "menu_template",
            entityId = entity.id,
            payload = MenuTemplateSyncPayloadCodec.encodeTemplate(
                MenuTemplateSyncPayload(entity.id, entity.householdId, entity.name, entity.normalizedName, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice),
            ),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
        )
    }

    private suspend fun enqueueItem(entity: MenuTemplateItemEntity, householdId: String) {
        val device = deviceManager.ensureDevice()
        val entityId = "${entity.templateId}:${entity.dayOfWeek}:${entity.mealSlot}"
        syncOperationRepository.enqueue(
            householdId = householdId,
            deviceId = device.deviceId,
            entityType = "menu_template_item",
            entityId = entityId,
            payload = MenuTemplateSyncPayloadCodec.encodeItem(
                MenuTemplateItemSyncPayload(entityId, entity.templateId, householdId, entity.dayOfWeek, entity.mealSlot, entity.dishId, entity.variantId, entity.title, entity.notes, entity.deleted, entity.updatedAt, entity.version, entity.updatedByDevice),
            ),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
        )
    }
}
