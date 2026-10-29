package com.majyd.casa.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: "recordatorio"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(
                when (kind) {
                    "preparacion" -> "Preparación previa"
                    "vencimiento" -> "Tarea vencida"
                    else -> "Recordatorio de tarea"
                },
            )
            .setContentText(if (kind == "preparacion") "El plato $title necesita que prepares algo para hacerlo" else title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(taskId.hashCode() + kind.hashCode(), notification) }
    }

    companion object {
        const val CHANNEL_ID = "task_notifications"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_KIND = "kind"
    }
}
