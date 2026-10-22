package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "task_person_assignments",
    primaryKeys = ["taskId", "personId"],
    indices = [Index(value = ["personId"])],
)
data class TaskPersonAssignmentEntity(
    val taskId: String,
    val personId: String,
    val createdAt: Long,
)
