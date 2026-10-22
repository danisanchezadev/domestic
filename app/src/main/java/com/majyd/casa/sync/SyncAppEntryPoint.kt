package com.majyd.casa.sync

import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.data.repository.TaskRepository
import com.majyd.casa.data.local.dao.AuthorizedDeviceDao
import com.majyd.casa.sync.transport.NsdDeviceDiscovery
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncAppEntryPoint {
    fun syncSession(): SyncSession
    fun syncCoordinator(): SyncCoordinator
    fun deviceManager(): DeviceManager
    fun discovery(): NsdDeviceDiscovery
    fun settingsRepository(): SettingsRepository
    fun authorizedDeviceDao(): AuthorizedDeviceDao
    fun taskRepository(): TaskRepository
}
