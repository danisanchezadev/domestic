package com.majyd.casa.data.model

enum class TaskRecurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

fun nextOccurrence(epochDay: Long, recurrence: TaskRecurrence): Long? {
    val date = java.time.LocalDate.ofEpochDay(epochDay)
    return when (recurrence) {
        TaskRecurrence.NONE -> null
        TaskRecurrence.DAILY -> date.plusDays(1).toEpochDay()
        TaskRecurrence.WEEKLY -> date.plusWeeks(1).toEpochDay()
        TaskRecurrence.MONTHLY -> date.plusMonths(1).toEpochDay()
        TaskRecurrence.YEARLY -> date.plusYears(1).toEpochDay()
    }
}

fun occurrenceId(seriesId: String, dueDate: Long): String = "$seriesId:$dueDate"
