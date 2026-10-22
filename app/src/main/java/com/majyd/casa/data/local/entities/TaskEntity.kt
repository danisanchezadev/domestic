package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["completed"]),
        Index(value = ["deleted"]),
        Index(value = ["normalizedTitle"]),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val normalizedTitle: String,
    val notes: String,
    val assignee: String?,
    val completed: Boolean,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val dueDate: Long = 0L,
    val dueTimeMinutes: Int? = null,
    val reminderMinutesBefore: Int? = null,
    val completedAt: Long? = null,
    val completedByPersonId: String? = null,
    val completedByGroupId: String? = null,
    val createdByDeviceId: String? = null,
    val updatedByDeviceId: String? = null,
    val assignmentType: String = "NONE",
    val recurrence: String = "NONE",
    val recurrenceSeriesId: String? = null,
)
