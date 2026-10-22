package com.majyd.casa.notifications

import com.majyd.casa.data.model.TaskItem

interface NotificationScheduler {
    fun schedule(task: TaskItem)
    fun cancel(taskId: String)
}
