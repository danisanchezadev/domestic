package com.majyd.casa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.majyd.casa.data.model.ContactCategoryItem

@Entity(tableName = "contact_categories")
data class ContactCategoryEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val normalizedName: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val updatedByDevice: String,
) {
    fun toModel() = ContactCategoryItem(id, name)
}
