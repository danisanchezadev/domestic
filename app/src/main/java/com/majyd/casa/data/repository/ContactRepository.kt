package com.majyd.casa.data.repository

import com.majyd.casa.data.model.ContactCategoryItem
import com.majyd.casa.data.model.ContactItem
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeCategories(): Flow<List<ContactCategoryItem>>
    suspend fun ensureDefaultCategories()
    fun observeContacts(): Flow<List<ContactItem>>
    suspend fun saveContact(
        contactId: String?,
        category: String,
        name: String,
        phone: String,
    ): ContactItem
    suspend fun deleteContact(contactId: String): ContactItem?
    suspend fun saveCategory(categoryId: String?, name: String): ContactCategoryItem?
    suspend fun deleteCategory(categoryId: String)
}
