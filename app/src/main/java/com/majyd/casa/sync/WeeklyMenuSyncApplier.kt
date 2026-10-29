package com.majyd.casa.sync

import androidx.room.withTransaction
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.WeeklyMenuItemEntity
import com.majyd.casa.data.repository.ShoppingRepository
import com.majyd.casa.notifications.NotificationScheduler
import com.majyd.casa.data.model.MealSlot
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyMenuSyncApplier @Inject constructor(
    private val database: CasaDatabase,
    private val operationRepository: SyncOperationRepository,
    private val shoppingRepository: ShoppingRepository,
    private val notificationScheduler: NotificationScheduler,
) {
    private val conflictResolver = DeterministicConflictResolver()

    suspend fun apply(operation: SyncOperation): Boolean {
        if (operationRepository.get(operation.operationId) != null) return true
        val payload = WeeklyMenuSyncPayloadCodec.decode(operation.payload) ?: return false
        if (operation.entityType != "weekly_menu_item" || payload.id != operation.entityId || payload.householdId != operation.householdId) return false
        val appliedToLocal = database.withTransaction {
            val dao = database.weeklyMenuDao()
            val local = dao.getById(payload.id)
            val incomingWins = local == null || conflictResolver.incomingWins(local.version, local.updatedAt, local.updatedByDevice, payload.version, payload.updatedAt, operation.deviceId)
            if (incomingWins) {
                dao.upsert(WeeklyMenuItemEntity(payload.id, payload.householdId, payload.dayOfWeek, payload.mealSlot, payload.dishId, payload.variantId, payload.title, payload.normalizedTitle, payload.notes, payload.deleted, payload.createdAt, payload.updatedAt, payload.version, payload.updatedByDevice))
            }
            incomingWins
        }
        if (appliedToLocal && !payload.deleted && payload.dishId != null) {
            // The menu assignment is also the trigger for the shopping ingredients on the peer device.
            val dish = database.dishDao().getById(payload.dishId)
            dish?.let {
                if (it.preparationRequired) {
                    val weekStart = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()).toEpochDay()
                    notificationScheduler.schedulePreparation(
                        payload.id,
                        it.name,
                        LocalDate.ofEpochDay(weekStart).plusDays((payload.dayOfWeek - 1).toLong()),
                        runCatching { MealSlot.valueOf(payload.mealSlot) }.getOrDefault(MealSlot.LUNCH),
                    )
                } else {
                    notificationScheduler.cancelPreparation(payload.id)
                }
                database.dishDao().getIngredients(it.id, payload.variantId)
                    .filterNot { ingredient -> ingredient.deleted }
                    .forEach { ingredient ->
                        if (payload.shoppingIngredientIds == null || ingredient.id in payload.shoppingIngredientIds) {
                            shoppingRepository.addIngredientToActiveList(ingredient.name, ingredient.quantity ?: 1.0, ingredient.unit)
                        }
                    }
            }
        }
        if (appliedToLocal && (payload.deleted || payload.dishId == null)) {
            notificationScheduler.cancelPreparation(payload.id)
        }
        // Record only after the menu and its shopping side effects have succeeded so a failed
        // ingredient import remains retryable.
        operationRepository.record(operation.copy(applied = true))
        return true
    }
}
