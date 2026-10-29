package com.majyd.casa.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.majyd.casa.R
import com.majyd.casa.data.model.TaskItem
import com.majyd.casa.data.model.MealSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNotificationScheduler @Inject constructor(
    @ApplicationContext
    private val context: Context,
) : NotificationScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    init {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(
                AlarmReceiver.CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT,
            ).setName(context.getString(R.string.notification_channel_tasks))
                .setDescription(context.getString(R.string.notification_channel_tasks_description))
                .build()
        )
    }

    override fun schedule(task: TaskItem) {
        cancel(task.id)
        // A due date is mandatory for tasks, but it does not imply a notification.
        if (task.reminderMinutesBefore == null) return
        val deadline = deadlineMillis(task)
        val reminderAtDeadline = task.reminderMinutesBefore == 0
        task.reminderMinutesBefore?.takeIf { it > 0 }?.let {
            setAlarm(task, deadline - it * 60_000L, "recordatorio", 0)
        }
        if (!task.completed && !task.deleted && reminderAtDeadline) {
            setAlarm(task, deadline, "recordatorio", 0)
        }
    }

    override fun cancel(taskId: String) {
        listOf(0, 1).forEach { kind ->
            alarmManager.cancel(pendingIntent(taskId, kind))
        }
    }

    override fun schedulePreparation(menuItemId: String, dishName: String, date: LocalDate, mealSlot: MealSlot) {
        cancelPreparation(menuItemId)
        val preparationDate = date.minusDays(1)
        val triggerAt = LocalDateTime.of(preparationDate, LocalTime.of(15, 0))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return
        setAlarm(menuItemId, triggerAt, "preparacion", dishName)
    }

    override fun cancelPreparation(menuItemId: String) {
        alarmManager.cancel(pendingIntent(menuItemId, 2))
    }

    private fun setAlarm(task: TaskItem, triggerAt: Long, kind: String, kindId: Int) {
        if (triggerAt <= System.currentTimeMillis()) return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(task.id, kindId, task.title, kind),
        )
    }

    private fun setAlarm(id: String, triggerAt: Long, kind: String, title: String) {
        if (triggerAt <= System.currentTimeMillis()) return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(id, 2, title, kind),
        )
    }

    private fun pendingIntent(taskId: String, kindId: Int, title: String? = null, kind: String? = null): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            title?.let { putExtra(AlarmReceiver.EXTRA_TITLE, it) }
            kind?.let { putExtra(AlarmReceiver.EXTRA_KIND, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode() * 10 + kindId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun deadlineMillis(task: TaskItem): Long {
        val date = LocalDate.ofEpochDay(task.dueDate)
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return start + (task.dueTimeMinutes?.toLong()?.times(60_000L) ?: 86_399_000L)
    }
}
