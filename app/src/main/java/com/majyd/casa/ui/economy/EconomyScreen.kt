package com.majyd.casa.ui.economy

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.EconomyCategory
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EconomyScreen(onBack: () -> Unit, viewModel: EconomyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var entryType by remember { mutableStateOf<EconomyMovementType?>(null) }
    var editing by remember { mutableStateOf<EconomyMovement?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCategorySettings by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<EconomyMovement?>(null) }
    if (showHistory) {
        EconomyHistoryScreen(state, viewModel, onBack = { showHistory = false })
        return
    }
    if (showSettings) {
        if (showCategorySettings) {
            EconomyCategorySettingsScreen(state, viewModel, onBack = { showCategorySettings = false })
        } else {
            EconomySettingsScreen(state, viewModel, onBack = { showSettings = false }, onCategories = { showCategorySettings = true })
        }
        return
    }
    val month = YearMonth.now()
    val monthMovements = state.movements.filter { LocalDate.ofEpochDay(it.dateEpochDay).let { date -> YearMonth.from(date) == month } }
    val income = monthMovements.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }
    val expense = monthMovements.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }
    val investment = monthMovements.filter { it.type == EconomyMovementType.INVESTMENT }.sumOf { it.amountCents }
    val balance = income - expense - investment
    val daysRemaining = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
    val status = when { balance >= state.target.targetCents && state.target.targetCents > 0 -> Color(0xFF1677FF) ; balance >= state.target.minimumCents && state.target.minimumCents > 0 -> Color(0xFF4F8A5B); balance >= 0L -> Color(0xFFE08A24); else -> Color(0xFFD94444) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(title = { Text("Economía", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } }, actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Ajustes") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) },
    ) { padding ->
        EconomyBackground {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card(colors = CardDefaults.cardColors(containerColor = status.copy(alpha = 0.16f)), shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(EconomyViewModel.formatMonth(month), Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold); Text(EconomyViewModel.formatCents(balance), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = status); Text(balanceLabel(balance, state.target.targetCents, state.target.minimumCents), Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Ingresos\n${EconomyViewModel.formatCents(income)}", color = Color(0xFF3B7A4A), fontWeight = FontWeight.Bold); Text("Gastos\n${EconomyViewModel.formatCents(expense)}", color = Color(0xFFC44646), fontWeight = FontWeight.Bold); Text("Inversión\n${EconomyViewModel.formatCents(investment)}", color = Color(0xFF8E55B8), fontWeight = FontWeight.Bold) } } } }
            item { Text("Objetivo: ${EconomyViewModel.formatCents(state.target.targetCents)}", Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold) }
            item { Text("Quedan $daysRemaining días para finalizar el mes", Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { entryType = EconomyMovementType.INCOME }, Modifier.weight(1f)) { Text("Ingreso", fontWeight = FontWeight.Bold) }; Button(onClick = { entryType = EconomyMovementType.EXPENSE }, Modifier.weight(1f)) { Text("Gasto", fontWeight = FontWeight.Bold) }; Button(onClick = { entryType = EconomyMovementType.INVESTMENT }, Modifier.weight(1f)) { Text("Inversión", fontWeight = FontWeight.Bold) } } }
            item { OutlinedButton(onClick = { showHistory = true }, Modifier.fillMaxWidth()) { Text("Histórico y estadísticas", fontWeight = FontWeight.Bold) } }
            item { Text("Movimientos de ${EconomyViewModel.formatMonth(month)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
            item { OutlinedButton(onClick = viewModel::loadDemoData, Modifier.fillMaxWidth()) { Text("Completar datos de prueba desde junio", fontWeight = FontWeight.Bold) } }
            if (monthMovements.isEmpty()) item { Text("Sin movimientos este mes", fontWeight = FontWeight.Bold) }
            items(monthMovements) { movement -> MovementRow(movement, state.categories, onEdit = { editing = movement }, onDelete = { pendingDelete = movement }) }
            }
        }
    }
    state.error?.let { message -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("Economía") }, text = { Text(message) }, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("Cerrar") } }) }
    (entryType ?: editing?.type)?.let { type -> EconomyMovementDialog(type, editing, state.categories, viewModel, onDismiss = { entryType = null; editing = null }) }
    pendingDelete?.let { movement ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar movimiento", fontWeight = FontWeight.ExtraBold) },
            text = { Text("¿Quieres eliminar este movimiento? No volverá a aparecer después de sincronizar.") },
            confirmButton = { Button(onClick = { viewModel.deleteMovement(movement); pendingDelete = null }) { Text("Eliminar", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

private fun balanceLabel(balance: Long, target: Long, minimum: Long) = ""

@Composable
private fun MovementRow(movement: EconomyMovement, categories: List<EconomyCategory>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val category = categories.firstOrNull { it.id == movement.categoryId }?.name ?: "Categoría archivada"
    val amountColor = when (movement.type) { EconomyMovementType.INCOME -> Color(0xFF3B7A4A); EconomyMovementType.INVESTMENT -> Color(0xFF8E55B8); EconomyMovementType.EXPENSE -> Color(0xFFC44646) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(category, fontWeight = FontWeight.Bold); Text("Mes: ${EconomyViewModel.formatMonth(YearMonth.from(LocalDate.ofEpochDay(movement.dateEpochDay)))}", style = MaterialTheme.typography.bodySmall) }; Text((if (movement.type == EconomyMovementType.INCOME) "+" else "-") + EconomyViewModel.formatCents(movement.amountCents), color = amountColor, fontWeight = FontWeight.ExtraBold); IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar") }; IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EconomyMovementDialog(type: EconomyMovementType, editing: EconomyMovement?, categories: List<EconomyCategory>, viewModel: EconomyViewModel, onDismiss: () -> Unit) {
    var amount by remember(editing) { mutableStateOf(editing?.let { "%.2f".format(java.util.Locale.US, it.amountCents / 100.0) } ?: "") }
    var categoryId by remember(editing) { mutableStateOf(editing?.categoryId ?: categories.firstOrNull { it.type == type }?.id.orEmpty()) }
    var selectedMonth by remember(editing) { mutableStateOf(editing?.let { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) } ?: YearMonth.now().minusMonths(1)) }
    var expanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    val options = categories.filter { it.type == type && !it.deleted }
    val monthOptions = remember { (0..36).map { YearMonth.now().minusMonths(24L - it) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (type == EconomyMovementType.INCOME) "Nuevo ingreso" else if (type == EconomyMovementType.INVESTMENT) "Nueva inversión" else "Nuevo gasto", fontWeight = FontWeight.ExtraBold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Importe (€)") }, singleLine = true); ExposedDropdownMenuBox(expanded, { expanded = !expanded }) { OutlinedTextField(options.firstOrNull { it.id == categoryId }?.name.orEmpty(), {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Categoría") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }); ExposedDropdownMenu(expanded, { expanded = false }) { options.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; expanded = false }) } } }; ExposedDropdownMenuBox(monthExpanded, { monthExpanded = !monthExpanded }) { OutlinedTextField(EconomyViewModel.formatMonth(selectedMonth), {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Mes contable") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(monthExpanded) }); ExposedDropdownMenu(monthExpanded, { monthExpanded = false }) { monthOptions.forEach { monthOption -> DropdownMenuItem(text = { Text(EconomyViewModel.formatMonth(monthOption)) }, onClick = { selectedMonth = monthOption; monthExpanded = false }) } } } } }, confirmButton = { Button(onClick = { viewModel.saveMovement(editing?.id, type, amount, categoryId, selectedMonth.atDay(1), onSaved = onDismiss) }) { Text("Guardar", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EconomyHistoryScreenLegacy(state: EconomyUiState, onBack: () -> Unit) {
    val income = state.movements.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }
    val expense = state.movements.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }
    val groups = state.movements.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) }.toSortedMap()
    val expenseByCategory = state.movements.filter { it.type == EconomyMovementType.EXPENSE }.groupBy { it.categoryId }.mapValues { (_, values) -> values.sumOf { it.amountCents } }.toList().sortedByDescending { it.second }
    Scaffold(topBar = { TopAppBar(title = { Text("Histórico y estadísticas", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } }) }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { SummaryCard("Total ingresado", income, Color(0xFF3B7A4A)); SummaryCard("Total gastado", expense, Color(0xFFC44646)); SummaryCard("Total ahorrado", income - expense, if (income >= expense) Color(0xFF3B7A4A) else Color(0xFFC44646)) }; item { Text("Evolución mensual", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }; items(groups.toList()) { (month, values) -> val i = values.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }; val e = values.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }; Card { Column(Modifier.padding(14.dp)) { Text("${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}", fontWeight = FontWeight.Bold); Text("Ingresos ${EconomyViewModel.formatCents(i)} | Gastos ${EconomyViewModel.formatCents(e)} | Ahorro ${EconomyViewModel.formatCents(i - e)}"); EconomyBar(i, e) } } }; item { Text("Gastos por categoría", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }; items(expenseByCategory) { (categoryId, amount) -> val name = state.categories.firstOrNull { it.id == categoryId }?.name ?: "Categoría archivada"; Card { Column(Modifier.padding(12.dp)) { Text(name, fontWeight = FontWeight.Bold); Text(EconomyViewModel.formatCents(amount)) } } } } }
}

@Composable
private fun EconomyBar(income: Long, expense: Long) {
    val maximum = maxOf(income, expense, 1L)
    Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.width(34.dp).height((140f * income / maximum).dp.coerceAtLeast(4.dp)).background(Color(0xFF4F8A5B), RoundedCornerShape(8.dp)))
            Text("Ingresos", fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.width(34.dp).height((140f * expense / maximum).dp.coerceAtLeast(4.dp)).background(Color(0xFFC44646), RoundedCornerShape(8.dp)))
            Text("Gastos", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryCard(title: String, amount: Long, color: Color) { Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f))) { Column(Modifier.fillMaxWidth().padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(EconomyViewModel.formatCents(amount), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = color) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EconomySettingsScreenLegacy(state: EconomyUiState, viewModel: EconomyViewModel, onBack: () -> Unit) {
    var target by remember(state.target) { mutableStateOf("%.2f".format(java.util.Locale.US, state.target.targetCents / 100.0)) }
    var minimum by remember(state.target) { mutableStateOf("%.2f".format(java.util.Locale.US, state.target.minimumCents / 100.0)) }
    var categoryType by remember { mutableStateOf(EconomyMovementType.EXPENSE) }
    var categoryName by remember { mutableStateOf("") }
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes de economía", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } }) }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Objetivos mensuales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text("Objetivo de ahorro (€)") }); OutlinedTextField(minimum, { minimum = it }, Modifier.fillMaxWidth(), label = { Text("Mínimo aceptable (€)") }); Button(onClick = { viewModel.saveTarget(target, minimum) }, Modifier.fillMaxWidth()) { Text("Guardar objetivos", fontWeight = FontWeight.Bold) } }; item { Text("Categorías", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EconomyMovementType.values().forEach { type -> FilledTonalButton(onClick = { categoryType = type }) { Text(if (type == EconomyMovementType.INCOME) "Ingresos" else "Gastos") } } }; Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(categoryName, { categoryName = it }, Modifier.weight(1f), label = { Text(if (editingCategoryId == null) "Nueva categoría" else "Editar categoría") }); IconButton(onClick = { viewModel.saveCategory(editingCategoryId, categoryType, categoryName); categoryName = ""; editingCategoryId = null }) { Icon(Icons.Filled.Add, "Guardar categoría") } } }; items(state.categories.filter { it.type == categoryType }) { category -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(category.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); IconButton(onClick = { editingCategoryId = category.id; categoryName = category.name; categoryType = category.type }) { Icon(Icons.Filled.Edit, "Editar") }; IconButton(onClick = { viewModel.archiveCategory(category) }) { Icon(Icons.Filled.Delete, "Desactivar", tint = MaterialTheme.colorScheme.error) } } } } }
}
