package com.majyd.casa.ui.economy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.majyd.casa.data.model.EconomyCategory
import com.majyd.casa.data.model.EconomyMovementType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EconomySettingsScreen(state: EconomyUiState, viewModel: EconomyViewModel, onBack: () -> Unit, onCategories: () -> Unit) {
    var target by remember(state.target) { mutableStateOf("%.2f".format(java.util.Locale.US, state.target.targetCents / 100.0)) }
    var minimum by remember(state.target) { mutableStateOf("%.2f".format(java.util.Locale.US, state.target.minimumCents / 100.0)) }
    var fiscalStartMonth by remember(state.settings) { mutableStateOf(state.settings.fiscalStartMonth) }
    var monthMenuOpen by remember { mutableStateOf(false) }
    Scaffold(containerColor = Color.Transparent, topBar = { TopAppBar(title = { Text("Ajustes de economía", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F1E8).copy(alpha = 0.94f))) }) { padding ->
        EconomyBackground {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Objetivos mensuales", fontWeight = FontWeight.ExtraBold)
                OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text("Objetivo de ahorro (€)") })
                OutlinedTextField(minimum, { minimum = it }, Modifier.fillMaxWidth(), label = { Text("Mínimo aceptable (€)") })
                Text("Ejercicio económico", fontWeight = FontWeight.ExtraBold)
                androidx.compose.foundation.layout.Box {
                    OutlinedTextField(
                        value = java.time.Month.of(fiscalStartMonth).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES")),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mes de inicio") },
                    )
                    androidx.compose.foundation.layout.Box(Modifier.matchParentSize().clickable { monthMenuOpen = true })
                    DropdownMenu(expanded = monthMenuOpen, onDismissRequest = { monthMenuOpen = false }) {
                        (1..12).forEach { month ->
                            DropdownMenuItem(
                                text = { Text(java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES"))) },
                                onClick = { fiscalStartMonth = month; monthMenuOpen = false },
                            )
                        }
                    }
                }
                Button(onClick = { viewModel.saveTarget(target, minimum); viewModel.saveSettings(fiscalStartMonth) }, Modifier.fillMaxWidth()) { Text("Guardar objetivos", fontWeight = FontWeight.Bold) }
                Card(Modifier.fillMaxWidth()) { TextButton(onClick = onCategories, Modifier.fillMaxWidth().padding(8.dp)) { Text("Configurar categorías", fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EconomyCategorySettingsScreen(state: EconomyUiState, viewModel: EconomyViewModel, onBack: () -> Unit) {
    var selectedType by remember { mutableStateOf<EconomyMovementType?>(null) }
    if (selectedType != null) {
        EconomyCategoryTypeScreen(state, viewModel, selectedType!!, onBack = { selectedType = null })
        return
    }
    Scaffold(containerColor = Color.Transparent, topBar = { TopAppBar(title = { Text("Configurar categorías", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F1E8).copy(alpha = 0.94f))) }) { padding ->
        EconomyBackground {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { selectedType = EconomyMovementType.INCOME },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B7A4A)),
                ) { Text("Ingresos", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { selectedType = EconomyMovementType.EXPENSE },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC44646)),
                ) { Text("Gastos", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { selectedType = EconomyMovementType.INVESTMENT },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E55B8)),
                ) { Text("Inversiones", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EconomyCategoryTypeScreen(state: EconomyUiState, viewModel: EconomyViewModel, type: EconomyMovementType, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    val title = when (type) { EconomyMovementType.INCOME -> "Categorías de ingresos"; EconomyMovementType.EXPENSE -> "Categorías de gastos"; EconomyMovementType.INVESTMENT -> "Categorías de inversión" }
    Scaffold(containerColor = Color.Transparent, topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.ExtraBold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F1E8).copy(alpha = 0.94f))) }) { padding ->
        EconomyBackground {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF8F1E8).copy(alpha = 0.94f))) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text(if (editingId == null) "Nueva categoría" else "Editar categoría") }, singleLine = true); IconButton(onClick = { viewModel.saveCategory(editingId, type, name); name = ""; editingId = null }) { Icon(Icons.Filled.Add, "Guardar categoría") } } } }
                items(state.categories.filter { it.type == type }) { category -> CategoryRow(category, onEdit = { editingId = category.id; name = category.name }, onDelete = { viewModel.archiveCategory(category) }) }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: EconomyCategory, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(category.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar") }; IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Eliminar") } } }
}
