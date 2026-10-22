package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "task_group_assignments",
    primaryKeys = ["taskId", "groupId"],
    indices = [Index(value = ["groupId"])],
)
data class TaskGroupAssignmentEntity(
    val taskId: String,
    val groupId: String,
    val createdAt: Long,
)
