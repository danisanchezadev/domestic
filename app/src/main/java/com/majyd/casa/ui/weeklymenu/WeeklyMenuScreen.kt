package com.majyd.casa.ui.weeklymenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.WeeklyMenuItem

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeeklyMenuScreen(
    onBack: () -> Unit,
    viewModel: WeeklyMenuViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = { Text("Menu semanal") },
                actions = {
                    TextButton(onClick = viewModel::archiveAll) {
                        Text("Archivar todo")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                WeeklyMenuDraftCard(
                    draft = state.draft,
                    onDayChange = viewModel::updateDayOfWeek,
                    onMealSlotChange = viewModel::updateMealSlot,
                    onTitleChange = viewModel::updateTitle,
                    onNotesChange = viewModel::updateNotes,
                    onSave = viewModel::saveItem,
                    onClear = viewModel::clearDraft,
                )
            }
            item {
                WeeklyMenuSection(
                    title = "Menu activo",
                    items = state.activeItems,
                    onEdit = viewModel::editItem,
                    onDelete = viewModel::deleteItem,
                    restoreMode = false,
                )
            }
            item {
                WeeklyMenuSection(
                    title = "Papelera",
                    items = state.deletedItems,
                    onEdit = viewModel::editItem,
                    onDelete = viewModel::deleteItem,
                    onRestore = viewModel::restoreItem,
                    restoreMode = true,
                )
            }
        }
    }
}

@Composable
private fun WeeklyMenuDraftCard(
    draft: WeeklyMenuDraft,
    onDayChange: (Int) -> Unit,
    onMealSlotChange: (MealSlot) -> Unit,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nuevo plato", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dayLabels().forEachIndexed { index, label ->
                    FilterChip(
                        selected = draft.dayOfWeek == index + 1,
                        onClick = { onDayChange(index + 1) },
                        label = { Text(label) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealSlot.entries.forEach { slot ->
                    FilterChip(
                        selected = draft.mealSlot == slot,
                        onClick = { onMealSlotChange(slot) },
                        label = { Text(slot.label) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Plato") },
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notas") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSave) {
                    Text("Guardar")
                }
                Button(onClick = onClear) {
                    Text("Limpiar")
                }
            }
        }
    }
}

@Composable
private fun WeeklyMenuSection(
    title: String,
    items: List<WeeklyMenuItem>,
    onEdit: (WeeklyMenuItem) -> Unit,
    onDelete: (WeeklyMenuItem) -> Unit,
    restoreMode: Boolean,
    onRestore: ((WeeklyMenuItem) -> Unit)? = null,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) {
                Text("Sin elementos")
            } else {
                items.forEach { item ->
                    WeeklyMenuRow(
                        item = item,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        restoreMode = restoreMode,
                        onRestore = onRestore,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyMenuRow(
    item: WeeklyMenuItem,
    onEdit: (WeeklyMenuItem) -> Unit,
    onDelete: (WeeklyMenuItem) -> Unit,
    restoreMode: Boolean,
    onRestore: ((WeeklyMenuItem) -> Unit)?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${dayName(item.dayOfWeek)} - ${item.mealSlot.label}", style = MaterialTheme.typography.bodyMedium)
            if (item.notes.isNotBlank()) {
                Text(item.notes, style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!restoreMode) {
                    AssistChip(onClick = { onEdit(item) }, label = { Text("Editar") })
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                    }
                } else {
                    IconButton(onClick = { onRestore?.invoke(item) }) {
                        Icon(Icons.Filled.Restore, contentDescription = "Restaurar")
                    }
                }
            }
        }
    }
}

private fun dayLabels(): List<String> = listOf("Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom")

private fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miercoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sabado"
    7 -> "Domingo"
    else -> "Dia"
}
