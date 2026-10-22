package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.WeeklyMenuDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.WeeklyMenuItemEntity
import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.WeeklyMenuItem
import com.majyd.casa.util.Normalizer
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.sync.WeeklyMenuItemSyncPayload
import com.majyd.casa.sync.WeeklyMenuSyncPayloadCodec
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomWeeklyMenuRepository @Inject constructor(
    private val database: CasaDatabase,
    private val weeklyMenuDao: WeeklyMenuDao,
    private val deviceManager: DeviceManager,
    private val syncOperationRepository: SyncOperationRepository,
) : WeeklyMenuRepository {

    override fun observeItems(): Flow<List<WeeklyMenuItem>> =
        weeklyMenuDao.observeItems().map { items -> items.map { it.toModel() } }

    override suspend fun ensureDefaults() {
        // Menu entries are user data; do not seed demo records in production.
    }

    override suspend fun upsertItem(
        itemId: String?,
        dayOfWeek: Int,
        mealSlot: MealSlot,
        title: String,
        notes: String,
    ): WeeklyMenuItem {
        return database.withTransaction {
            val device = deviceManager.ensureDevice()
            val current = itemId?.let { weeklyMenuDao.getById(it) }
            val now = System.currentTimeMillis()
            val entity = WeeklyMenuItemEntity(
                id = current?.id ?: UUID.randomUUID().toString(),
                householdId = device.householdId,
                dayOfWeek = dayOfWeek.coerceIn(1, 7),
                mealSlot = mealSlot.name,
                title = title.trim(),
                normalizedTitle = Normalizer.normalizeText(title),
                notes = notes.trim(),
                deleted = false,
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
                version = (current?.version ?: 0) + 1,
                updatedByDevice = device.deviceId,
            )
            weeklyMenuDao.upsert(entity)
            enqueue(entity, if (current == null) "CREATE" else "UPDATE")
            entity.toModel()
        }
    }

    override suspend fun deleteItem(itemId: String): WeeklyMenuItem? {
        val current = weeklyMenuDao.getById(itemId) ?: return null
        return database.withTransaction {
            val device = deviceManager.ensureDevice()
            val now = System.currentTimeMillis()
            val updated = current.copy(deleted = true, updatedAt = now, version = current.version + 1, updatedByDevice = device.deviceId)
            weeklyMenuDao.upsert(updated)
            enqueue(updated, "DELETE")
            updated.toModel()
        }
    }

    override suspend fun restoreItem(itemId: String): WeeklyMenuItem? {
        val current = weeklyMenuDao.getById(itemId) ?: return null
        return database.withTransaction {
            val device = deviceManager.ensureDevice()
            val now = System.currentTimeMillis()
            val updated = current.copy(deleted = false, updatedAt = now, version = current.version + 1, updatedByDevice = device.deviceId)
            weeklyMenuDao.upsert(updated)
            enqueue(updated, "RESTORE")
            updated.toModel()
        }
    }

    override suspend fun archiveAll() {
        database.withTransaction {
            val device = deviceManager.ensureDevice()
            val now = System.currentTimeMillis()
            weeklyMenuDao.getActive().forEach { current ->
                val updated = current.copy(deleted = true, updatedAt = now, version = current.version + 1, updatedByDevice = device.deviceId)
                weeklyMenuDao.upsert(updated)
                enqueue(updated, "ARCHIVE")
            }
        }
    }

    private suspend fun enqueue(entity: WeeklyMenuItemEntity, operationType: String) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(entity.householdId, device.deviceId, "weekly_menu_item", entity.id, WeeklyMenuSyncPayloadCodec.encode(WeeklyMenuItemSyncPayload(entity.id, entity.householdId, entity.dayOfWeek, entity.mealSlot, entity.title, entity.normalizedTitle, entity.notes, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private fun WeeklyMenuItemEntity.toModel(): WeeklyMenuItem = WeeklyMenuItem(
        id = id,
        dayOfWeek = dayOfWeek,
        mealSlot = runCatching { MealSlot.valueOf(mealSlot) }.getOrDefault(MealSlot.LUNCH),
        title = title,
        normalizedTitle = normalizedTitle,
        notes = notes,
        deleted = deleted,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
    )

}
