package com.majyd.casa.ui.weeklymenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.MealSlot
import com.majyd.casa.data.model.MenuHistoryItem
import com.majyd.casa.data.model.WeeklyMenuItem
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeeklyMenuScreen(
    onBack: () -> Unit,
    onOpenDishes: () -> Unit,
    viewModel: WeeklyMenuViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var templateName by rememberSaveable { mutableStateOf("") }

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
                    IconButton(onClick = onOpenDishes) {
                        Icon(Icons.Filled.Restaurant, contentDescription = "Mis platos")
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
                    dishes = state.dishes,
                    variants = state.variants,
                    onDayChange = viewModel::updateDayOfWeek,
                    onMealSlotChange = viewModel::updateMealSlot,
                    onTitleChange = viewModel::updateTitle,
                    onNotesChange = viewModel::updateNotes,
                    onDishChange = viewModel::selectDish,
                    onVariantChange = viewModel::selectVariant,
                    onSave = viewModel::saveItem,
                    onClear = viewModel::clearDraft,
                )
            }
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Plantillas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(templateName, { templateName = it }, Modifier.weight(1f), label = { Text("Nombre") }, singleLine = true)
                            Button(onClick = { viewModel.saveTemplate(templateName); templateName = "" }, enabled = templateName.isNotBlank()) { Text("Guardar") }
                        }
                        if (state.templates.isEmpty()) Text("Sin plantillas")
                        state.templates.forEach { template ->
                            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(template.name, Modifier.weight(1f))
                                TextButton(onClick = { viewModel.loadTemplate(template.id) }) { Text("Cargar") }
                                IconButton(onClick = { viewModel.deleteTemplate(template.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar plantilla", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            item {
                MenuHistorySection(state.history, viewModel::setHistoryCompleted)
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
    state.ingredientPicker?.let { picker ->
        AlertDialog(
            onDismissRequest = viewModel::cancelIngredientSelection,
            title = { Text("Ingredientes para la compra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Selecciona los ingredientes que quieres añadir.")
                    picker.ingredients.forEach { ingredient ->
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ingredient.id in picker.selectedIds,
                                onCheckedChange = { viewModel.toggleIngredient(ingredient.id) },
                            )
                            Text(listOf(ingredient.name, ingredient.quantity?.toString().orEmpty(), ingredient.unit).filter { it.isNotBlank() }.joinToString(" "))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmIngredientSelection) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelIngredientSelection) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MenuHistorySection(
    items: List<MenuHistoryItem>,
    onCompletedChange: (MenuHistoryItem, Boolean) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Historial de las ultimas semanas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) {
                Text("Todavia no hay historial")
            } else {
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                            Text("Semana del ${formatHistoryDate(item.weekStartEpochDay)} - ${dayName(item.dayOfWeek)} - ${item.mealSlot.label}", style = MaterialTheme.typography.bodySmall)
                        }
                        androidx.compose.material3.Checkbox(
                            checked = item.completed,
                            onCheckedChange = { checked -> onCompletedChange(item, checked) },
                        )
                    }
                }
            }
        }
    }
}

private fun formatHistoryDate(epochDay: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy").format(LocalDate.ofEpochDay(epochDay))

@Composable
private fun WeeklyMenuDraftCard(
    draft: WeeklyMenuDraft,
    dishes: List<com.majyd.casa.data.model.Dish>,
    variants: List<com.majyd.casa.data.model.DishVariant>,
    onDayChange: (Int) -> Unit,
    onMealSlotChange: (MealSlot) -> Unit,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onDishChange: (com.majyd.casa.data.model.Dish?) -> Unit,
    onVariantChange: (com.majyd.casa.data.model.DishVariant?) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nuevo plato", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Plato del catalogo", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.dishId == null,
                    onClick = { onDishChange(null) },
                    label = { Text("Texto libre") },
                )
                dishes.forEach { dish ->
                    FilterChip(
                        selected = draft.dishId == dish.id,
                        onClick = { onDishChange(dish) },
                        label = { Text(dish.name) },
                    )
                }
            }
            if (variants.isNotEmpty()) {
                Text("Variante", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.variantId == null, { onVariantChange(null) }, label = { Text("Base") })
                    variants.forEach { variant ->
                        FilterChip(draft.variantId == variant.id, { onVariantChange(variant) }, label = { Text(variant.name) })
                    }
                }
            }
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
