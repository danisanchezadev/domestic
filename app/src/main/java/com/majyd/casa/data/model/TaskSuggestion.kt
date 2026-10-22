package com.majyd.casa.data.model

data class TaskSuggestion(
    val title: String,
    val notes: String,
    val assignmentType: TaskAssignmentType,
    val personIds: Set<String>,
    val groupId: String?,
    val dueTimeMinutes: Int?,
    val reminderMinutesBefore: Int?,
    val recurrence: TaskRecurrence,
    val usageCount: Int,
    val lastUsedAt: Long,
)
