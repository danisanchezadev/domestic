package com.majyd.casa.notifications

import com.majyd.casa.data.model.TaskItem
import com.majyd.casa.data.model.MealSlot
import java.time.LocalDate

interface NotificationScheduler {
    fun schedule(task: TaskItem)
    fun cancel(taskId: String)
    fun schedulePreparation(menuItemId: String, dishName: String, date: LocalDate, mealSlot: MealSlot) {}
    fun cancelPreparation(menuItemId: String) {}
}
