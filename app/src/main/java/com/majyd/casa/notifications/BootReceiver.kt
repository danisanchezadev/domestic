package com.majyd.casa.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.Intent.ACTION_MY_PACKAGE_REPLACED
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(ACTION_BOOT_COMPLETED, ACTION_MY_PACKAGE_REPLACED)) return
        val pendingResult = goAsync()
        val entryPoint = EntryPointAccessors.fromApplication(context, NotificationEntryPoint::class.java)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val household = entryPoint.householdRepository().observeState().first()
                entryPoint.taskRepository().observeTasks().first()
                    .filterNot { it.completed || it.deleted }
                    .filter { TaskNotificationPolicy.isRelevant(it, household) }
                    .forEach { entryPoint.scheduler().schedule(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationEntryPoint {
    fun taskRepository(): com.majyd.casa.data.repository.TaskRepository
    fun householdRepository(): com.majyd.casa.data.repository.HouseholdRepository
    fun scheduler(): NotificationScheduler
}
