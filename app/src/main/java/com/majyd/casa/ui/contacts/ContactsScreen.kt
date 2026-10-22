package com.majyd.casa.ui.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.ContactItem
import com.majyd.casa.data.model.ContactCategoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(onBack: () -> Unit, viewModel: ContactsViewModel = hiltViewModel()) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf<ContactForm?>(null) }
    var managingCategories by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhone by remember { mutableStateOf<String?>(null) }
    val dial = { phone: String -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.cleanForDial()}"))) }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPhone?.let { phone ->
            if (granted) context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${phone.cleanForDial()}"))) else dial(phone)
            pendingPhone = null
        }
    }
    fun call(phone: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${phone.cleanForDial()}")))
        } else {
            pendingPhone = phone
            callPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Contactos") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
            actions = {
                IconButton(onClick = { managingCategories = true }) { Icon(Icons.Filled.Edit, "Gestionar categorias") }
                IconButton(onClick = { editing = ContactForm(category = selectedCategory ?: categories.firstOrNull()?.name ?: "Otros") }) { Icon(Icons.Filled.Add, "Anadir contacto") }
            },
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedCategory == null) {
                item { Text("Categorias", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                if (categories.isEmpty()) item { Text("Todavia no hay categorias.") }
                items(categories, key = { it.id }) { category ->
                    Button(onClick = { selectedCategory = category.name }, modifier = Modifier.fillMaxWidth()) {
                        Text(category.name)
                    }
                }
            } else {
                item {
                    Button(onClick = { selectedCategory = null }, modifier = Modifier.fillMaxWidth()) { Text("Volver a categorias") }
                    Text(selectedCategory.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                val grouped = contacts.filter { it.category == selectedCategory }
                if (grouped.isEmpty()) item { Text("No hay telefonos en esta categoria.") }
                items(grouped, key = { it.id }) { contact ->
                    ContactCard(contact, { editing = ContactForm(contact.id, contact.category, contact.name, contact.phone) }, ::call)
                }
            }
            item { Spacer(Modifier.size(8.dp)) }
        }
    }
    editing?.let { form ->
        val contact = form.id?.let { id -> contacts.firstOrNull { it.id == id } }
        ContactFormDialog(form, categories, { editing = it }, { editing = null }, { viewModel.save(form) { editing = null } }, if (contact != null) { { viewModel.delete(contact); editing = null } } else null)
    }
    if (managingCategories) CategoryManagerDialog(categories, viewModel, { managingCategories = false })
}

@Composable
private fun ContactCard(contact: ContactItem, onEdit: () -> Unit, onCall: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(contact.phone, style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = { onCall(contact.phone) }) { Icon(Icons.Filled.Phone, "Llamar", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun ContactFormDialog(form: ContactForm, categories: List<ContactCategoryItem>, onChange: (ContactForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (form.id == null) "Anadir contacto" else "Editar contacto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box {
                    OutlinedTextField(form.category, {}, label = { Text("Categoría") }, readOnly = true, modifier = Modifier.fillMaxWidth().clickable { expanded = true })
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { onChange(form.copy(category = category.name)); expanded = false }) }
                    }
                }
                OutlinedTextField(form.name, { onChange(form.copy(name = it)) }, label = { Text("Nombre o servicio") }, singleLine = true)
                OutlinedTextField(form.phone, { onChange(form.copy(phone = it)) }, label = { Text("Telefono principal") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = form.name.isNotBlank() && form.phone.isNotBlank()) { Text("Guardar") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Eliminar", color = androidx.compose.ui.graphics.Color(0xFFD32F2F)) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar contacto") },
            text = { Text("Se eliminara este telefono de la lista. Quieres continuar?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("Eliminar", color = androidx.compose.ui.graphics.Color(0xFFD32F2F)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CategoryManagerDialog(categories: List<ContactCategoryItem>, viewModel: ContactsViewModel, onDismiss: () -> Unit) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorias") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(category.name, Modifier.weight(1f))
                        TextButton(onClick = { editingId = category.id; name = category.name }) { Text("Editar") }
                        TextButton(onClick = { viewModel.deleteCategory(category) }) { Text("Eliminar") }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text(if (editingId == null) "Nueva categoria" else "Editar categoria") }, singleLine = true)
                Button(onClick = { viewModel.saveCategory(editingId, name); editingId = null; name = "" }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (editingId == null) "Anadir categoria" else "Guardar cambio") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun String.cleanForDial(): String = filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
