package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "personId"],
    indices = [Index(value = ["personId"])],
)
data class GroupMemberEntity(
    val groupId: String,
    val personId: String,
    val createdAt: Long,
)
