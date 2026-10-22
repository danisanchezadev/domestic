package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_history")
data class TaskHistoryEntity(
    @PrimaryKey val normalizedTitle: String,
    val title: String,
    val notes: String,
    val assignmentType: String,
    val personIdsJson: String,
    val groupId: String?,
    val dueTimeMinutes: Int?,
    val reminderMinutesBefore: Int?,
    val recurrence: String,
    val usageCount: Int,
    val lastUsedAt: Long,
)
