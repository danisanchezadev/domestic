package com.majyd.casa.sync

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSyncEngine @Inject constructor(
    private val taskSyncApplier: TaskSyncApplier,
    private val householdSyncApplier: HouseholdSyncApplier,
    private val shoppingSyncApplier: ShoppingSyncApplier,
    private val contactSyncApplier: ContactSyncApplier,
    private val contactCategorySyncApplier: ContactCategorySyncApplier,
    private val calendarSyncApplier: CalendarSyncApplier,
    private val weeklyMenuSyncApplier: WeeklyMenuSyncApplier,
) : SyncEngine {
    override suspend fun apply(operation: SyncOperation): Boolean {
        return when (operation.entityType) {
            "task" -> taskSyncApplier.apply(operation)
            "person", "group" -> householdSyncApplier.apply(operation)
            "shopping_list", "shopping_product", "shopping_catalog" -> shoppingSyncApplier.apply(operation)
            "contact" -> contactSyncApplier.apply(operation)
            "contact_category" -> contactCategorySyncApplier.apply(operation)
            "calendar_event", "calendar_place" -> calendarSyncApplier.apply(operation)
            "weekly_menu_item" -> weeklyMenuSyncApplier.apply(operation)
            else -> false
        }
    }
}
