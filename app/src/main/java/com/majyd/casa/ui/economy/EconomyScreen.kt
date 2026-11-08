package com.majyd.casa.ui.economy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.EconomyCategory
import com.majyd.casa.data.model.EconomyCalculations
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType
import java.time.LocalDate
import java.time.YearMonth

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
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    LaunchedEffect(selectedMonth) { viewModel.loadTarget(selectedMonth) }
    if (showHistory) {
        EconomyHistoryScreen(state, viewModel, onBack = { showHistory = false }, onEdit = { editing = it; showHistory = false }, onDelete = { pendingDelete = it; showHistory = false })
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
    val month = selectedMonth
    val monthMovements = state.movements.filter { LocalDate.ofEpochDay(it.dateEpochDay).let { date -> YearMonth.from(date) == month } }
    val totals = EconomyCalculations.totals(monthMovements)
    val income = totals.incomeCents
    val expense = totals.expenseCents
    val investment = totals.investmentCents
    val balance = totals.liquidityCents
    val daysRemaining = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
    val status = when { balance >= state.target.targetCents -> Color(0xFF3B7A4A); balance >= state.target.minimumCents -> Color(0xFFD39A2C); balance >= 0L -> Color(0xFFD66A24); else -> Color(0xFFC44646) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(title = { Text("Economía", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } }, actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Ajustes") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) },
    ) { padding ->
        EconomyBackground {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) { Text("Anterior") }; Text(EconomyViewModel.formatMonth(month), fontWeight = FontWeight.ExtraBold); TextButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) { Text("Siguiente") } } }
            item { Card(colors = CardDefaults.cardColors(containerColor = status.copy(alpha = 0.16f)), shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(EconomyViewModel.formatMonth(month), Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold); Text(EconomyViewModel.formatCents(balance), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = status); Text(balanceLabel(balance, state.target.targetCents, state.target.minimumCents), Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Ingresos\n${EconomyViewModel.formatCents(income)}", color = Color(0xFF3B7A4A), fontWeight = FontWeight.Bold); Text("Gastos\n${EconomyViewModel.formatCents(expense)}", color = Color(0xFFC44646), fontWeight = FontWeight.Bold); Text("Inversión\n${EconomyViewModel.formatCents(investment)}", color = Color(0xFF8E55B8), fontWeight = FontWeight.Bold) } } } }
            item { Text("Margen hasta el objetivo: ${EconomyViewModel.formatCents(balance - state.target.targetCents)}", Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, color = status) }
            item { EconomyLiquidityBar(income, balance, state.target.targetCents, state.target.minimumCents, status) }
            if (month == YearMonth.now()) item { Text("Quedan $daysRemaining días para finalizar el mes", Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { entryType = EconomyMovementType.INCOME }, Modifier.weight(1f)) { Text("Ingreso", fontWeight = FontWeight.Bold) }; Button(onClick = { entryType = EconomyMovementType.EXPENSE }, Modifier.weight(1f)) { Text("Gasto", fontWeight = FontWeight.Bold) }; Button(onClick = { entryType = EconomyMovementType.INVESTMENT }, Modifier.weight(1f)) { Text("Inversión", fontWeight = FontWeight.Bold) } } }
            item { OutlinedButton(onClick = { showHistory = true }, Modifier.fillMaxWidth()) { Text("Histórico y estadísticas", fontWeight = FontWeight.Bold) } }
            item { Text("Movimientos de ${EconomyViewModel.formatMonth(month)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
            if (monthMovements.isEmpty()) item { Text("Sin movimientos este mes", fontWeight = FontWeight.Bold) }
            items(monthMovements) { movement -> MovementRow(movement, state.categories, onEdit = { editing = movement }, onDelete = { pendingDelete = movement }) }
            }
        }
    }
    state.error?.let { message -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("Economía") }, text = { Text(message) }, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("Cerrar") } }) }
    (entryType ?: editing?.type)?.let { type -> EconomyMovementDialog(type, editing, month, state.categories, viewModel, onDismiss = { entryType = null; editing = null }) }
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
@Composable
private fun EconomyLiquidityBar(income: Long, balance: Long, target: Long, minimum: Long, status: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Liquidez del mes", fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(42.dp)) {
                val safeIncome = income.coerceAtLeast(1L).toFloat()
                val positiveScale = (balance.coerceAtLeast(0L).toFloat() / safeIncome).coerceIn(0f, 1f)
                val negativeScale = if (balance < 0L) (kotlin.math.abs(balance).toFloat() / safeIncome).coerceIn(0f, 1f) else 0f
                if (balance >= 0L) {
                    drawRoundRect(Color(0xFFE3E8E2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
                    drawRoundRect(status, size = androidx.compose.ui.geometry.Size(size.width * positiveScale, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
                    val targetX = (target.coerceIn(0L, income).toFloat() / safeIncome) * size.width
                    val minimumX = (minimum.coerceIn(0L, income).toFloat() / safeIncome) * size.width
                    drawLine(Color(0xFF244C35), androidx.compose.ui.geometry.Offset(targetX, 0f), androidx.compose.ui.geometry.Offset(targetX, size.height), strokeWidth = 4f)
                    drawLine(Color(0xFFD39A2C), androidx.compose.ui.geometry.Offset(minimumX, 0f), androidx.compose.ui.geometry.Offset(minimumX, size.height), strokeWidth = 4f)
                } else {
                    drawRoundRect(Color.Black, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
                    drawRoundRect(Color(0xFFC44646), size = androidx.compose.ui.geometry.Size(size.width * negativeScale, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
                }
            }
            Text(if (balance < 0L) "Déficit respecto a los ingresos asignados" else "Objetivo y mínimo: referencias de ahorro", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun balanceLabel(balance: Long, target: Long, minimum: Long): String = when {
    balance < 0L -> "Liquidez negativa"
    balance >= target -> "Objetivo alcanzado"
    balance >= minimum -> "Mínimo aceptable alcanzado"
    else -> "Por debajo del mínimo"
}

@Composable
private fun MovementRow(movement: EconomyMovement, categories: List<EconomyCategory>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val category = categories.firstOrNull { it.id == movement.categoryId }?.name ?: "Categoría archivada"
    val amountColor = when (movement.type) { EconomyMovementType.INCOME -> Color(0xFF3B7A4A); EconomyMovementType.INVESTMENT -> Color(0xFF8E55B8); EconomyMovementType.EXPENSE -> Color(0xFFC44646) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(category, fontWeight = FontWeight.Bold); Text("Mes: ${EconomyViewModel.formatMonth(YearMonth.from(LocalDate.ofEpochDay(movement.dateEpochDay)))}", style = MaterialTheme.typography.bodySmall) }; Text((if (movement.type == EconomyMovementType.INCOME) "+" else "-") + EconomyViewModel.formatCents(movement.amountCents), color = amountColor, fontWeight = FontWeight.ExtraBold); IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar") }; IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EconomyMovementDialog(
    type: EconomyMovementType,
    editing: EconomyMovement?,
    defaultMonth: YearMonth,
    categories: List<EconomyCategory>,
    viewModel: EconomyViewModel,
    onDismiss: () -> Unit,
) {
    var amount by remember(editing) { mutableStateOf(editing?.let { "%.2f".format(java.util.Locale.US, it.amountCents / 100.0) } ?: "") }
    var categoryId by remember(editing) { mutableStateOf(editing?.categoryId ?: categories.firstOrNull { it.type == type }?.id.orEmpty()) }
    var selectedMonth by remember(editing, defaultMonth) { mutableStateOf(editing?.let { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) } ?: defaultMonth) }
    var yearText by remember(editing, defaultMonth) { mutableStateOf(selectedMonth.year.toString()) }
    var expanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    val options = categories.filter { it.type == type && !it.deleted }
    val selectedYear = yearText.toIntOrNull()?.coerceIn(1, 9999) ?: selectedMonth.year
    val monthOptions = (1..12).map { YearMonth.of(selectedYear, it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (type == EconomyMovementType.INCOME) "Nuevo ingreso" else if (type == EconomyMovementType.INVESTMENT) "Nueva inversión" else "Nuevo gasto",
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Importe (€)") }, singleLine = true)
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(
                        options.firstOrNull { it.id == categoryId }?.name.orEmpty(),
                        {},
                        Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    )
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        options.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; expanded = false })
                        }
                    }
                }
                OutlinedTextField(yearText, { yearText = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Año contable") }, singleLine = true)
                ExposedDropdownMenuBox(monthExpanded, { monthExpanded = !monthExpanded }) {
                    OutlinedTextField(
                        EconomyViewModel.formatMonth(selectedMonth),
                        {},
                        Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        label = { Text("Mes contable") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(monthExpanded) },
                    )
                    ExposedDropdownMenu(monthExpanded, { monthExpanded = false }) {
                        monthOptions.forEach { monthOption ->
                            DropdownMenuItem(
                                text = { Text(EconomyViewModel.formatMonth(monthOption)) },
                                onClick = { selectedMonth = monthOption; monthExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val accountingMonth = selectedMonth.withYear(selectedYear)
                viewModel.saveMovement(editing?.id, type, amount, categoryId, accountingMonth.atDay(1), onSaved = onDismiss)
            }) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
