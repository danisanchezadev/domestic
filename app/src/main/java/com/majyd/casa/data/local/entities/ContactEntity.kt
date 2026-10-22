package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.majyd.casa.data.model.ContactItem

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val category: String,
    val name: String,
    val normalizedName: String,
    val phone: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
) {
    fun toModel() = ContactItem(id, category, name, phone, deleted, createdAt, updatedAt, version)
}
