package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "sync_operations",
    indices = [Index(value = ["householdId"]), Index(value = ["entityId"])],
)
data class SyncOperationEntity(
    @androidx.room.PrimaryKey val operationId: String,
    val householdId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String = "UPDATE",
    val payload: String,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean,
    val applied: Boolean,
)
