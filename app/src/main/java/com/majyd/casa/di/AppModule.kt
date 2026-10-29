package com.majyd.casa.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.database.ALL_MIGRATIONS
import com.majyd.casa.data.repository.CalendarRepository
import com.majyd.casa.data.repository.RoomDeviceRepository
import com.majyd.casa.data.repository.RoomCalendarRepository
import com.majyd.casa.data.repository.RoomHomeProfileRepository
import com.majyd.casa.data.repository.RoomShoppingRepository
import com.majyd.casa.data.repository.RoomTaskRepository
import com.majyd.casa.data.repository.RoomWeeklyMenuRepository
import com.majyd.casa.data.repository.RoomDishRepository
import com.majyd.casa.data.repository.ShoppingRepository
import com.majyd.casa.data.repository.TaskRepository
import com.majyd.casa.data.repository.WeeklyMenuRepository
import com.majyd.casa.data.repository.RoomMenuTemplateRepository
import com.majyd.casa.data.repository.MenuTemplateRepository
import com.majyd.casa.data.repository.DishRepository
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.data.repository.RoomHouseholdRepository
import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.data.repository.PreferencesRepository
import com.majyd.casa.data.repository.ContactRepository
import com.majyd.casa.data.repository.RoomContactRepository
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.DefaultDeviceManager
import com.majyd.casa.notifications.AndroidNotificationScheduler
import com.majyd.casa.notifications.NotificationScheduler
import com.majyd.casa.sync.RoomSyncOperationRepository
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.sync.SyncEngine
import com.majyd.casa.sync.DefaultSyncEngine
import com.majyd.casa.sync.transport.LocalWifiTransport
import com.majyd.casa.sync.transport.SocketLocalWifiTransport
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val SETTINGS_FILE = "app_preferences.preferences_pb"

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CasaDatabase {
        return Room.databaseBuilder(
            context,
            CasaDatabase::class.java,
            "casa.db",
        )
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @Provides
    @Singleton
    fun provideDeviceDao(database: CasaDatabase) = database.deviceDao()

    @Provides
    @Singleton
    fun provideAuthorizedDeviceDao(database: CasaDatabase) = database.authorizedDeviceDao()

    @Provides
    @Singleton
    fun provideTaskHistoryDao(database: CasaDatabase) = database.taskHistoryDao()

    @Provides
    @Singleton
    fun provideHomeProfileDao(database: CasaDatabase) = database.homeProfileDao()

    @Provides
    @Singleton
    fun provideShoppingListDao(database: CasaDatabase) = database.shoppingListDao()

    @Provides
    @Singleton
    fun provideShoppingCategoryDao(database: CasaDatabase) = database.shoppingCategoryDao()

    @Provides
    @Singleton
    fun provideShoppingProductDao(database: CasaDatabase) = database.shoppingProductDao()

    @Provides
    @Singleton
    fun provideShoppingCatalogDao(database: CasaDatabase) = database.shoppingCatalogDao()

    @Provides
    @Singleton
    fun provideTaskDao(database: CasaDatabase) = database.taskDao()

    @Provides
    @Singleton
    fun provideCalendarDao(database: CasaDatabase) = database.calendarDao()

    @Provides
    @Singleton
    fun provideCalendarPlaceDao(database: CasaDatabase) = database.calendarPlaceDao()

    @Provides
    @Singleton
    fun provideCalendarEventExceptionDao(database: CasaDatabase) = database.calendarEventExceptionDao()

    @Provides
    @Singleton
    fun provideRecipeCategoryDao(database: CasaDatabase) = database.recipeCategoryDao()

    @Provides
    @Singleton
    fun provideContactDao(database: CasaDatabase) = database.contactDao()

    @Provides
    @Singleton
    fun provideContactCategoryDao(database: CasaDatabase) = database.contactCategoryDao()

    @Provides
    @Singleton
    fun provideWeeklyMenuDao(database: CasaDatabase) = database.weeklyMenuDao()

    @Provides
    @Singleton
    fun provideDishDao(database: CasaDatabase) = database.dishDao()

    @Provides
    fun provideMenuTemplateDao(database: CasaDatabase) = database.menuTemplateDao()

    @Provides
    @Singleton
    fun provideMenuHistoryDao(database: CasaDatabase) = database.menuHistoryDao()

    @Provides
    @Singleton
    fun provideHouseholdDao(database: CasaDatabase) = database.householdDao()

    @Provides
    @Singleton
    fun provideSyncOperationDao(database: CasaDatabase) = database.syncOperationDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) }
        )
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: DataStore<Preferences>,
    ): SettingsRepository {
        return PreferencesRepository(dataStore)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindModule {

    @Binds
    @Singleton
    abstract fun bindSyncOperationRepository(impl: RoomSyncOperationRepository): SyncOperationRepository

    @Binds
    @Singleton
    abstract fun bindSyncEngine(impl: DefaultSyncEngine): SyncEngine

    @Binds
    @Singleton
    abstract fun bindLocalWifiTransport(impl: SocketLocalWifiTransport): LocalWifiTransport

    @Binds
    @Singleton
    abstract fun bindNotificationScheduler(
        impl: AndroidNotificationScheduler,
    ): NotificationScheduler

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(
        impl: RoomDeviceRepository,
    ): com.majyd.casa.data.repository.DeviceRepository

    @Binds
    @Singleton
    abstract fun bindHomeProfileRepository(
        impl: RoomHomeProfileRepository,
    ): com.majyd.casa.data.repository.HomeProfileRepository

    @Binds
    @Singleton
    abstract fun bindShoppingRepository(
        impl: RoomShoppingRepository,
    ): ShoppingRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        impl: RoomTaskRepository,
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: RoomCalendarRepository,
    ): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(
        impl: RoomContactRepository,
    ): ContactRepository

    @Binds
    @Singleton
    abstract fun bindWeeklyMenuRepository(
        impl: RoomWeeklyMenuRepository,
    ): WeeklyMenuRepository

    @Binds
    abstract fun bindMenuTemplateRepository(
        impl: RoomMenuTemplateRepository,
    ): MenuTemplateRepository

    @Binds
    @Singleton
    abstract fun bindDishRepository(
        impl: RoomDishRepository,
    ): DishRepository

    @Binds
    @Singleton
    abstract fun bindHouseholdRepository(
        impl: RoomHouseholdRepository,
    ): HouseholdRepository

    @Binds
    @Singleton
    abstract fun bindDeviceManager(
        impl: DefaultDeviceManager,
    ): DeviceManager
}
