package com.majyd.casa.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.ContactItem
import com.majyd.casa.data.model.ContactCategoryItem
import com.majyd.casa.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactForm(
    val id: String? = null,
    val category: String = "Emergencias",
    val name: String = "",
    val phone: String = "",
)

@HiltViewModel
class ContactsViewModel @Inject constructor(private val repository: ContactRepository) : ViewModel() {
    init { viewModelScope.launch { repository.ensureDefaultCategories() } }
    val contacts: StateFlow<List<ContactItem>> = repository.observeContacts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<ContactCategoryItem>> = repository.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(form: ContactForm, onComplete: () -> Unit) = viewModelScope.launch {
        if (form.name.isBlank() || form.phone.isBlank()) return@launch
        repository.saveContact(form.id, form.category, form.name, form.phone)
        onComplete()
    }

    fun delete(contact: ContactItem) = viewModelScope.launch { repository.deleteContact(contact.id) }
    fun saveCategory(id: String?, name: String) = viewModelScope.launch { repository.saveCategory(id, name) }
    fun deleteCategory(category: ContactCategoryItem) = viewModelScope.launch { repository.deleteCategory(category.id) }
}
