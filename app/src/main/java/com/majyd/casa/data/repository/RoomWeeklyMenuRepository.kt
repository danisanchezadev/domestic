package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.WeeklyMenuDao
import com.majyd.casa.data.local.dao.DishDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.WeeklyMenuItemEntity
import com.majyd.casa.data.local.entities.MenuHistoryItemEntity
import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.WeeklyMenuItem
import com.majyd.casa.data.model.DishUsageStats
import com.majyd.casa.data.model.MenuHistoryItem
import com.majyd.casa.data.repository.ShoppingRepository
import com.majyd.casa.util.Normalizer
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.sync.WeeklyMenuItemSyncPayload
import com.majyd.casa.sync.WeeklyMenuSyncPayloadCodec
import com.majyd.casa.sync.MenuHistorySyncPayload
import com.majyd.casa.sync.MenuHistorySyncPayloadCodec
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomWeeklyMenuRepository @Inject constructor(
    private val database: CasaDatabase,
    private val weeklyMenuDao: WeeklyMenuDao,
    private val dishDao: DishDao,
    private val shoppingRepository: ShoppingRepository,
    private val deviceManager: DeviceManager,
    private val syncOperationRepository: SyncOperationRepository,
) : WeeklyMenuRepository {

    override fun observeItems(): Flow<List<WeeklyMenuItem>> =
        weeklyMenuDao.observeItems().map { items -> items.map { it.toModel() } }

    override fun observeHistory(): Flow<List<MenuHistoryItem>> = flow {
        val householdId = deviceManager.ensureDevice().householdId
        emitAll(database.menuHistoryDao().observeAll(householdId).map { items -> items.map { it.toModel() } })
    }

    override suspend fun getUsageStats(dishId: String): DishUsageStats = DishUsageStats(
        assignmentCount = weeklyMenuDao.countActiveAssignments(dishId),
        lastAssignedAt = weeklyMenuDao.lastAssignmentUpdate(dishId),
    )

    override suspend fun ensureDefaults() {
        database.withTransaction {
            val device = deviceManager.ensureDevice()
            val weekStart = currentWeekStart()
            weeklyMenuDao.getActive().forEach { snapshot(it, weekStart, device.deviceId) }
            database.menuHistoryDao().deleteOlderThan(weekStart - 21)
        }
    }

    override suspend fun upsertItem(
        itemId: String?,
        dayOfWeek: Int,
        mealSlot: MealSlot,
        dishId: String?,
        variantId: String?,
        title: String,
        notes: String,
        shoppingIngredientIds: Set<String>?,
    ): WeeklyMenuItem {
        return database.withTransaction {
            val device = deviceManager.ensureDevice()
            val current = itemId?.let { weeklyMenuDao.getById(it) }
                ?: weeklyMenuDao.getActiveBySlot(dayOfWeek.coerceIn(1, 7), mealSlot.name)
            val now = System.currentTimeMillis()
            val entity = WeeklyMenuItemEntity(
                id = current?.id ?: UUID.randomUUID().toString(),
                householdId = device.householdId,
                dayOfWeek = dayOfWeek.coerceIn(1, 7),
                mealSlot = mealSlot.name,
                dishId = dishId,
                variantId = variantId,
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
            snapshot(entity, currentWeekStart(), device.deviceId)
            enqueue(entity, if (current == null) "CREATE" else "UPDATE", shoppingIngredientIds?.toList())
            addDishIngredientsToShopping(dishId, variantId, shoppingIngredientIds)
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
            enqueue(updated, "DELETE", null)
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
            enqueue(updated, "RESTORE", null)
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
                enqueue(updated, "ARCHIVE", null)
            }
        }
    }

    override suspend fun setHistoryCompleted(historyId: String, completed: Boolean) {
        database.withTransaction {
            val dao = database.menuHistoryDao()
            val current = dao.getById(historyId) ?: return@withTransaction
            val device = deviceManager.ensureDevice()
            val now = System.currentTimeMillis()
            val updated = current.copy(
                completed = completed,
                completedAt = if (completed) now else null,
                updatedAt = now,
                version = current.version + 1,
                updatedByDevice = device.deviceId,
            )
            dao.upsert(updated)
            enqueueHistory(updated)
        }
    }

    private suspend fun enqueue(entity: WeeklyMenuItemEntity, operationType: String, shoppingIngredientIds: List<String>?) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(entity.householdId, device.deviceId, "weekly_menu_item", entity.id, WeeklyMenuSyncPayloadCodec.encode(WeeklyMenuItemSyncPayload(entity.id, entity.householdId, entity.dayOfWeek, entity.mealSlot, entity.dishId, entity.variantId, shoppingIngredientIds, entity.title, entity.normalizedTitle, entity.notes, entity.deleted, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)), entity.version, entity.updatedAt, entity.deleted, operationType)
    }

    private suspend fun addDishIngredientsToShopping(dishId: String?, variantId: String?, selectedIds: Set<String>?) {
        if (dishId == null) return
        val ingredients = dishDao.getIngredients(dishId, variantId)
        ingredients.filterNot { it.deleted }.filter { selectedIds == null || it.id in selectedIds }.forEach { ingredient ->
            shoppingRepository.addIngredientToDefaultList(
                name = ingredient.name,
                quantity = ingredient.quantity ?: 1.0,
                unit = ingredient.unit,
            )
        }
    }

    private suspend fun snapshot(item: WeeklyMenuItemEntity, weekStart: Long, deviceId: String) {
        val dao = database.menuHistoryDao()
        val current = dao.getForSlot(weekStart, item.dayOfWeek, item.mealSlot)
        val now = System.currentTimeMillis()
        val unchanged = current != null && current.dishId == item.dishId && current.variantId == item.variantId && current.title == item.title && current.notes == item.notes
        if (unchanged) return
        val updated = MenuHistoryItemEntity(
            id = current?.id ?: "${item.householdId}:$weekStart:${item.dayOfWeek}:${item.mealSlot}",
            householdId = item.householdId,
            weekStartEpochDay = weekStart,
            dayOfWeek = item.dayOfWeek,
            mealSlot = item.mealSlot,
            dishId = item.dishId,
            variantId = item.variantId,
            title = item.title,
            notes = item.notes,
            completed = current?.completed ?: false,
            completedAt = current?.completedAt,
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
            version = (current?.version ?: 0) + 1,
            updatedByDevice = deviceId,
        )
        dao.upsert(updated)
        enqueueHistory(updated)
    }

    private suspend fun enqueueHistory(entity: MenuHistoryItemEntity) {
        val device = deviceManager.ensureDevice()
        syncOperationRepository.enqueue(
            householdId = entity.householdId,
            deviceId = device.deviceId,
            entityType = "menu_history_item",
            entityId = entity.id,
            payload = MenuHistorySyncPayloadCodec.encode(MenuHistorySyncPayload(entity.id, entity.householdId, entity.weekStartEpochDay, entity.dayOfWeek, entity.mealSlot, entity.dishId, entity.variantId, entity.title, entity.notes, entity.completed, entity.completedAt, entity.createdAt, entity.updatedAt, entity.version, entity.updatedByDevice)),
            version = entity.version,
            updatedAt = entity.updatedAt,
            deleted = false,
        )
    }

    private fun currentWeekStart(): Long {
        val today = LocalDate.now()
        return today.minusDays((today.dayOfWeek.value - 1).toLong()).toEpochDay()
    }

    private fun WeeklyMenuItemEntity.toModel(): WeeklyMenuItem = WeeklyMenuItem(
        id = id,
        dayOfWeek = dayOfWeek,
        mealSlot = runCatching { MealSlot.valueOf(mealSlot) }.getOrDefault(MealSlot.LUNCH),
        dishId = dishId,
        variantId = variantId,
        title = title,
        normalizedTitle = normalizedTitle,
        notes = notes,
        deleted = deleted,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
    )

    private fun MenuHistoryItemEntity.toModel(): MenuHistoryItem = MenuHistoryItem(
        id = id,
        weekStartEpochDay = weekStartEpochDay,
        dayOfWeek = dayOfWeek,
        mealSlot = runCatching { MealSlot.valueOf(mealSlot) }.getOrDefault(MealSlot.LUNCH),
        dishId = dishId,
        variantId = variantId,
        title = title,
        notes = notes,
        completed = completed,
        completedAt = completedAt,
    )

}
