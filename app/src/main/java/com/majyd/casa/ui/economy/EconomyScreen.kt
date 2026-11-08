package com.majyd.casa.ui.economy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.EconomyCategory
import com.majyd.casa.data.model.EconomyCalculations
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay

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
    var showPeriodPicker by remember { mutableStateOf(false) }
    var showLiquidityHelp by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<EconomyMovementType?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            val nextDay = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            delay(java.time.Duration.between(java.time.Instant.now(), nextDay).toMillis().coerceAtLeast(1_000L))
        }
    }
    LaunchedEffect(selectedMonth) { viewModel.loadTarget(selectedMonth) }
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
    val month = selectedMonth
    val monthMovements = state.movements.filter { LocalDate.ofEpochDay(it.dateEpochDay).let { date -> YearMonth.from(date) == month } }
    val filteredMovements = monthMovements.filter { movement ->
        (typeFilter == null || movement.type == typeFilter) &&
            (categoryFilter == null || movement.categoryId == categoryFilter)
    }
    val totals = EconomyCalculations.totals(monthMovements)
    val income = totals.incomeCents
    val expense = totals.expenseCents
    val investment = totals.investmentCents
    val balance = totals.liquidityCents
    val daysRemaining = month.lengthOfMonth() - today.dayOfMonth + 1
    val status = when { balance >= state.target.targetCents -> Color(0xFF3B7A4A); balance >= state.target.minimumCents -> Color(0xFFD39A2C); balance >= 0L -> Color(0xFFD66A24); else -> Color(0xFFC44646) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(title = { Text("Economía", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } }, actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Ajustes") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) },
    ) { padding ->
        EconomyBackground {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "Mes anterior") }; Text(EconomyViewModel.formatMonth(month), Modifier.clickable { showPeriodPicker = true }.padding(horizontal = 12.dp), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, "Mes siguiente") } } }
            item { Card(colors = CardDefaults.cardColors(containerColor = status.copy(alpha = 0.16f)), shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(EconomyViewModel.formatMonth(month), Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold); Box(Modifier.fillMaxWidth()) { Text(EconomyViewModel.formatCents(balance), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = when { EconomyViewModel.formatCents(balance).length > 16 -> 32.sp; EconomyViewModel.formatCents(balance).length > 12 -> 40.sp; else -> 56.sp }, fontWeight = FontWeight.ExtraBold, color = status, maxLines = 1, softWrap = false) }; Text(balanceLabel(balance, state.target.targetCents, state.target.minimumCents), Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Ingresos\n${EconomyViewModel.formatCents(income)}", color = Color(0xFF3B7A4A), fontWeight = FontWeight.Bold); Text("Gastos\n${EconomyViewModel.formatCents(expense)}", color = Color(0xFFC44646), fontWeight = FontWeight.Bold); Text("Inversión\n${EconomyViewModel.formatCents(investment)}", color = Color(0xFF8E55B8), fontWeight = FontWeight.Bold) } } } }
            item { Text("Margen hasta el objetivo: ${EconomyViewModel.formatCents(balance - state.target.targetCents)}", Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, color = status) }
            item { EconomyLiquidityBar(income, balance, state.target.targetCents, state.target.minimumCents, status, onHelp = { showLiquidityHelp = true }) }
            if (month == YearMonth.from(today)) item { Text("Quedan $daysRemaining días para finalizar el mes", Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold) }
            item { FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) { Button(onClick = { entryType = EconomyMovementType.INCOME }, Modifier.widthIn(min = 120.dp)) { Text("Ingreso", fontWeight = FontWeight.Bold, maxLines = 1) }; Button(onClick = { entryType = EconomyMovementType.EXPENSE }, Modifier.widthIn(min = 120.dp)) { Text("Gasto", fontWeight = FontWeight.Bold, maxLines = 1) }; Button(onClick = { entryType = EconomyMovementType.INVESTMENT }, Modifier.widthIn(min = 120.dp)) { Text("Inversión", fontWeight = FontWeight.Bold, maxLines = 1) } } }
            item { OutlinedButton(onClick = { showHistory = true }, Modifier.fillMaxWidth()) { Text("Histórico y estadísticas", fontWeight = FontWeight.Bold) } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Movimientos de ${EconomyViewModel.formatMonth(month)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); TextButton(onClick = { showFilters = true }) { Icon(Icons.Filled.FilterList, null); Text(if (typeFilter != null || categoryFilter != null) "Filtros · activos" else "Filtros") } } }
            if (monthMovements.isEmpty()) item { Text("Sin movimientos este mes", fontWeight = FontWeight.Bold) }
            if (monthMovements.isNotEmpty() && filteredMovements.isEmpty()) item { Text("Ningún movimiento coincide con los filtros", fontWeight = FontWeight.Bold) }
            items(filteredMovements) { movement -> MovementRow(movement, state.categories, onEdit = { editing = movement }, onDelete = { pendingDelete = movement }) }
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
    if (showPeriodPicker) EconomyPeriodPickerDialog(month, onDismiss = { showPeriodPicker = false }, onSelected = { selectedMonth = it; showPeriodPicker = false })
    if (showFilters) EconomyFiltersDialog(state.categories, typeFilter, categoryFilter, onDismiss = { showFilters = false }, onApply = { type, category -> typeFilter = type; categoryFilter = category; showFilters = false })
    if (showLiquidityHelp) EconomyLiquidityHelpDialog(onDismiss = { showLiquidityHelp = false })
}
@Composable
private fun EconomyLiquidityBar(income: Long, balance: Long, target: Long, minimum: Long, status: Color, onHelp: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Liquidez", fontWeight = FontWeight.Bold)
                IconButton(onClick = onHelp) { Icon(Icons.Filled.HelpOutline, "Ayuda sobre liquidez") }
            }
            Canvas(Modifier.fillMaxWidth().height(42.dp)) {
                val safeIncome = income.coerceAtLeast(1L).toFloat()
                val positiveScale = (balance.coerceAtLeast(0L).toFloat() / safeIncome).coerceIn(0f, 1f)
                val negativeScale = EconomyCalculations.negativeLiquidityScale(balance)
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
        }
    }
}

private fun balanceLabel(balance: Long, target: Long, minimum: Long): String = when {
    balance < 0L -> "Liquidez negativa"
    balance >= target -> "Objetivo alcanzado"
    balance >= minimum -> "Mínimo aceptable alcanzado"
    else -> "Por debajo del mínimo"
}

private fun economyTypeLabel(type: EconomyMovementType): String = when (type) {
    EconomyMovementType.INCOME -> "Ingresos"
    EconomyMovementType.EXPENSE -> "Gastos"
    EconomyMovementType.INVESTMENT -> "Inversiones"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EconomyPeriodPickerDialog(current: YearMonth, onDismiss: () -> Unit, onSelected: (YearMonth) -> Unit) {
    var yearText by remember(current) { mutableStateOf(current.year.toString()) }
    var month by remember(current) { mutableStateOf(current.monthValue) }
    var expanded by remember { mutableStateOf(false) }
    val year = yearText.toIntOrNull()?.takeIf { it in 1..9999 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar período", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(yearText, { yearText = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Año") }, isError = year == null, singleLine = true)
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES")).replaceFirstChar { it.uppercase() }, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Mes") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        (1..12).forEach { value -> DropdownMenuItem(text = { Text(java.time.Month.of(value).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "ES")).replaceFirstChar { it.uppercase() }) }, onClick = { month = value; expanded = false }) }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = year != null, onClick = { onSelected(YearMonth.of(year!!, month)) }) { Text("Aceptar", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EconomyFiltersDialog(categories: List<EconomyCategory>, type: EconomyMovementType?, category: String?, onDismiss: () -> Unit, onApply: (EconomyMovementType?, String?) -> Unit) {
    var selectedType by remember(type) { mutableStateOf(type) }
    var selectedCategory by remember(category) { mutableStateOf(category) }
    val availableCategories = categories.filter { !it.deleted && (selectedType == null || it.type == selectedType) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tipo", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selectedType == null, { selectedType = null; selectedCategory = null }, label = { Text("Todos") })
                    EconomyMovementType.entries.forEach { value -> FilterChip(selectedType == value, { selectedType = if (selectedType == value) null else value; selectedCategory = null }, label = { Text(economyTypeLabel(value)) }) }
                }
                Text("Categoría", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selectedCategory == null, { selectedCategory = null }, label = { Text("Todas") })
                    availableCategories.forEach { value -> FilterChip(selectedCategory == value.id, { selectedCategory = if (selectedCategory == value.id) null else value.id }, label = { Text(value.name) }) }
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(selectedType, selectedCategory) }) { Text("Aplicar", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { onApply(null, null) }) { Text("Limpiar") } },
    )
}

@Composable
private fun EconomyLiquidityHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cómo funciona la liquidez", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("La liquidez mensual es el dinero disponible que queda en el mes seleccionado.")
                Text("Se calcula así: ingresos menos gastos menos inversiones.")
                Text("El objetivo de ahorro es la cantidad que quieres conservar. El mínimo aceptable es el umbral inferior que consideras razonable.")
                Text("El margen respecto al objetivo es la liquidez del mes menos el objetivo configurado.")
                Text("Verde indica que se ha alcanzado el objetivo; amarillo, que se ha alcanzado el mínimo; naranja, que hay liquidez positiva pero por debajo del mínimo; rojo, que la liquidez es negativa.")
                Text("El fondo de la barra representa toda la escala disponible. En negativo se muestra un fondo negro y un segmento rojo que crece con el déficit.")
                Text("La escala roja negativa es visual y no lineal para evitar que déficits muy grandes saturen la barra. Por eso su longitud no representa un porcentaje de los ingresos.")
                Text("El objetivo y el mínimo se configuran desde el engranaje de ajustes. Cada mes se calcula por separado: no se arrastran saldos de meses anteriores.")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Entendido", fontWeight = FontWeight.Bold) } },
    )
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
    val enteredYear = EconomyViewModel.parseAccountingYear(yearText)
    val yearError = when {
        yearText.isBlank() -> "Introduce el año contable."
        enteredYear == null -> "Introduce un año entre 1 y 9999."
        else -> null
    }
    val selectedYear = enteredYear ?: selectedMonth.year
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
                OutlinedTextField(yearText, { yearText = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Año contable") }, isError = yearError != null, supportingText = { yearError?.let { Text(it) } }, singleLine = true)
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
            Button(enabled = yearError == null, onClick = {
                val accountingMonth = selectedMonth.withYear(selectedYear)
                viewModel.saveMovement(editing?.id, type, amount, categoryId, accountingMonth.atDay(1), onSaved = onDismiss)
            }) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
