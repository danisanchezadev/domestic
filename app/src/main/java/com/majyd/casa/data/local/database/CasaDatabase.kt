package com.majyd.casa.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.majyd.casa.data.local.dao.TaskDao
import com.majyd.casa.data.local.dao.CalendarDao
import com.majyd.casa.data.local.dao.ShoppingCategoryDao
import com.majyd.casa.data.local.dao.DeviceDao
import com.majyd.casa.data.local.dao.HomeProfileDao
import com.majyd.casa.data.local.dao.ShoppingCatalogDao
import com.majyd.casa.data.local.dao.ShoppingListDao
import com.majyd.casa.data.local.dao.ShoppingProductDao
import com.majyd.casa.data.local.dao.WeeklyMenuDao
import com.majyd.casa.data.local.entities.DeviceIdentityEntity
import com.majyd.casa.data.local.entities.HomeProfileEntity
import com.majyd.casa.data.local.entities.TaskEntity
import com.majyd.casa.data.local.entities.CalendarEventEntity
import com.majyd.casa.data.local.entities.ShoppingCategoryEntity
import com.majyd.casa.data.local.entities.ShoppingCatalogEntity
import com.majyd.casa.data.local.entities.ShoppingListEntity
import com.majyd.casa.data.local.entities.ShoppingProductEntity
import com.majyd.casa.data.local.entities.WeeklyMenuItemEntity
import com.majyd.casa.data.local.entities.PersonEntity
import com.majyd.casa.data.local.entities.HouseholdGroupEntity
import com.majyd.casa.data.local.entities.GroupMemberEntity
import com.majyd.casa.data.local.entities.TaskPersonAssignmentEntity
import com.majyd.casa.data.local.entities.TaskGroupAssignmentEntity
import com.majyd.casa.data.local.entities.SyncOperationEntity
import com.majyd.casa.data.local.entities.AuthorizedDeviceEntity
import com.majyd.casa.data.local.entities.TaskHistoryEntity
import com.majyd.casa.data.local.entities.ContactEntity
import com.majyd.casa.data.local.entities.ContactCategoryEntity
import com.majyd.casa.data.local.entities.CalendarPlaceEntity
import com.majyd.casa.data.local.entities.DishEntity
import com.majyd.casa.data.local.entities.DishIngredientEntity
import com.majyd.casa.data.local.entities.DishVariantEntity
import com.majyd.casa.data.local.entities.MenuTemplateEntity
import com.majyd.casa.data.local.entities.MenuTemplateItemEntity
import com.majyd.casa.data.local.entities.MenuHistoryItemEntity

@Database(
    entities = [
        DeviceIdentityEntity::class,
        HomeProfileEntity::class,
        ShoppingListEntity::class,
        ShoppingCategoryEntity::class,
        ShoppingProductEntity::class,
        ShoppingCatalogEntity::class,
        TaskEntity::class,
        CalendarEventEntity::class,
        WeeklyMenuItemEntity::class,
        PersonEntity::class,
        HouseholdGroupEntity::class,
        GroupMemberEntity::class,
        TaskPersonAssignmentEntity::class,
        TaskGroupAssignmentEntity::class,
        SyncOperationEntity::class,
        AuthorizedDeviceEntity::class,
        TaskHistoryEntity::class,
        ContactEntity::class,
        ContactCategoryEntity::class,
        CalendarPlaceEntity::class,
        DishEntity::class,
        DishIngredientEntity::class,
        DishVariantEntity::class,
        MenuTemplateEntity::class,
        MenuTemplateItemEntity::class,
        MenuHistoryItemEntity::class,
    ],
    version = 30,
    exportSchema = true,
)
abstract class CasaDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun homeProfileDao(): HomeProfileDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun shoppingCategoryDao(): ShoppingCategoryDao
    abstract fun shoppingProductDao(): ShoppingProductDao
    abstract fun shoppingCatalogDao(): ShoppingCatalogDao
    abstract fun taskDao(): TaskDao
    abstract fun calendarDao(): CalendarDao
    abstract fun weeklyMenuDao(): WeeklyMenuDao
    abstract fun householdDao(): com.majyd.casa.data.local.dao.HouseholdDao
    abstract fun syncOperationDao(): com.majyd.casa.data.local.dao.SyncOperationDao
    abstract fun authorizedDeviceDao(): com.majyd.casa.data.local.dao.AuthorizedDeviceDao
    abstract fun taskHistoryDao(): com.majyd.casa.data.local.dao.TaskHistoryDao
    abstract fun contactDao(): com.majyd.casa.data.local.dao.ContactDao
    abstract fun contactCategoryDao(): com.majyd.casa.data.local.dao.ContactCategoryDao
    abstract fun calendarPlaceDao(): com.majyd.casa.data.local.dao.CalendarPlaceDao
    abstract fun dishDao(): com.majyd.casa.data.local.dao.DishDao
    abstract fun menuTemplateDao(): com.majyd.casa.data.local.dao.MenuTemplateDao
    abstract fun menuHistoryDao(): com.majyd.casa.data.local.dao.MenuHistoryDao
}
