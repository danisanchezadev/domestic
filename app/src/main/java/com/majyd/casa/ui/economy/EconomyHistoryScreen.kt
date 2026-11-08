package com.majyd.casa.ui.economy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyCalculations
import com.majyd.casa.data.model.EconomyMovementType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
internal fun EconomyHistoryScreen(state: EconomyUiState, viewModel: EconomyViewModel, onBack: () -> Unit, onEdit: (EconomyMovement) -> Unit, onDelete: (EconomyMovement) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<EconomyMovementType?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedPeriod by remember { mutableStateOf(YearMonth.now()) }
    val currentMonth = YearMonth.now()
    val currentFiscalYear = if (currentMonth.monthValue >= state.settings.fiscalStartMonth) currentMonth.year else currentMonth.year - 1
    var selectedFiscalYear by remember(state.settings.fiscalStartMonth) { mutableStateOf(currentFiscalYear) }
    val fiscalStart = YearMonth.of(selectedFiscalYear, state.settings.fiscalStartMonth)
    val fiscalTitle = if (state.settings.fiscalStartMonth == 1) "$selectedFiscalYear" else "$selectedFiscalYear/${selectedFiscalYear + 1}"
    val fiscalMonths = (0..11).map { offset -> fiscalStart.plusMonths(offset.toLong()) }
    val groups = fiscalMonths.associateWith { month ->
        state.historyMovements.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
    }
    val exerciseMovements = groups.values.flatten()
    val visibleHistory = if (showAll) state.allHistoryMovements else state.historyMonthMovements
    LaunchedEffect(selectedFiscalYear, state.settings.fiscalStartMonth, showAll) {
        if (showAll) viewModel.loadAllHistory() else viewModel.loadHistory(fiscalStart, fiscalStart.plusMonths(11))
    }
    LaunchedEffect(selectedPeriod) { viewModel.loadHistoryMonth(selectedPeriod) }
    val filteredMovements = visibleHistory.filter { movement ->
        (typeFilter == null || movement.type == typeFilter) &&
            (categoryFilter == null || movement.categoryId == categoryFilter)
    }
    val totals = EconomyCalculations.totals(exerciseMovements)
    val income = totals.incomeCents
    val expense = totals.expenseCents
    val investment = totals.investmentCents
    val savings = totals.liquidityCents
    val expenseByCategory = exerciseMovements.filter { it.type == EconomyMovementType.EXPENSE }
        .groupBy { it.categoryId }
        .mapValues { (_, values) -> values.sumOf { it.amountCents } }
        .toList()
        .sortedByDescending { it.second }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Histórico y estadísticas", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        EconomySummaryRow("Ingresos", income, Color(0xFF3B7A4A))
                        EconomySummaryRow("Gastos", expense, Color(0xFFC44646))
                        EconomySummaryRow("Inversiones", investment, Color(0xFF8E55B8))
                        EconomySummaryRow("Ahorro", savings, if (savings >= 0) Color(0xFF3B7A4A) else Color(0xFFC44646))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ejercicio $fiscalTitle", fontWeight = FontWeight.ExtraBold)
                    androidx.compose.material3.TextButton(onClick = { selectedFiscalYear-- }) { Text("Anterior") }
                    androidx.compose.material3.TextButton(onClick = { selectedFiscalYear++ }) { Text("Siguiente") }
                }
            }
            item { SectionTitle("Ahorro mensual") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (showAll) "Todos los movimientos" else "Movimientos de ${EconomyViewModel.formatMonth(selectedPeriod)}", fontWeight = FontWeight.Bold)
                        androidx.compose.material3.TextButton(onClick = { selectedPeriod = selectedPeriod.minusMonths(1) }) { Text("Anterior") }
                        androidx.compose.material3.TextButton(onClick = { selectedPeriod = selectedPeriod.plusMonths(1) }) { Text("Siguiente") }
                        androidx.compose.material3.TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Ver mes actual" else "Ver todos") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("Todos") })
                        EconomyMovementType.entries.forEach { type ->
                            FilterChip(selected = typeFilter == type, onClick = { typeFilter = if (typeFilter == type) null else type }, label = { Text(typeLabel(type)) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.categories.filter { it.id in visibleHistory.map { movement -> movement.categoryId }.toSet() }.forEach { category ->
                            FilterChip(selected = categoryFilter == category.id, onClick = { categoryFilter = if (categoryFilter == category.id) null else category.id }, label = { Text(category.name) })
                        }
                    }
                }
            }
            if (groups.isEmpty()) {
                item { Text("Sin movimientos registrados", fontWeight = FontWeight.Bold) }
            } else {
                item { Card { Column(Modifier.padding(14.dp)) { MonthlySavingsChart(groups, state.target.targetCents, state.target.minimumCents) } } }
                items(groups.toList(), key = { it.first.toString() }) { (month, values) ->
                    val futureWithoutMovements = month > YearMonth.now() && values.isEmpty()
                    val monthIncome = values.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }
                    val monthExpense = values.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }
                    val monthInvestment = values.filter { it.type == EconomyMovementType.INVESTMENT }.sumOf { it.amountCents }
                    val monthSavings = monthlySavings(values)
                    Card(colors = CardDefaults.cardColors(containerColor = if (futureWithoutMovements) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (futureWithoutMovements) "${formatSpanishMonth(month)} · Próximo" else formatSpanishMonth(month), fontWeight = FontWeight.Bold, color = if (futureWithoutMovements) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                CompactStat("Ingresos", monthIncome, Color(0xFF3B7A4A))
                                CompactStat("Gastos", monthExpense, Color(0xFFC44646))
                                CompactStat("Inversión", monthInvestment, Color(0xFF8E55B8))
                                CompactStat("Ahorro", monthSavings, if (monthSavings >= 0) Color(0xFFB33A3A) else Color.Black)
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Gastos por categoría") }
            items(expenseByCategory, key = { it.first }) { (categoryId, amount) ->
                val name = state.categories.firstOrNull { it.id == categoryId }?.name ?: "Categoría archivada"
                Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("$name:", fontWeight = FontWeight.Bold); Text(EconomyViewModel.formatCents(amount), fontWeight = FontWeight.ExtraBold) } }
            }
            item { SectionTitle("Movimientos") }
            items(filteredMovements, key = { it.id }) { movement ->
                val category = state.categories.firstOrNull { it.id == movement.categoryId }?.name ?: "Categoría archivada"
                Card {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category, fontWeight = FontWeight.Bold)
                            Text(typeLabel(movement.type), style = MaterialTheme.typography.bodySmall)
                            Text(EconomyViewModel.formatMonth(YearMonth.from(LocalDate.ofEpochDay(movement.dateEpochDay))), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(EconomyViewModel.formatCents(movement.amountCents), fontWeight = FontWeight.ExtraBold)
                        IconButton(onClick = { onEdit(movement) }) { Icon(Icons.Filled.Edit, "Editar") }
                        IconButton(onClick = { onDelete(movement) }) { Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

private fun monthlySavings(values: List<EconomyMovement>): Long = values.sumOf {
    when (it.type) {
        EconomyMovementType.INCOME -> it.amountCents
        EconomyMovementType.EXPENSE, EconomyMovementType.INVESTMENT -> -it.amountCents
    }
}

private fun typeLabel(type: EconomyMovementType): String = when (type) {
    EconomyMovementType.INCOME -> "Ingresos"
    EconomyMovementType.EXPENSE -> "Gastos"
    EconomyMovementType.INVESTMENT -> "Inversiones"
}

@Composable
private fun EconomySummaryRow(title: String, amount: Long, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(EconomyViewModel.formatCents(amount), color = color, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.fillMaxWidth().padding(vertical = 6.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun MonthlySavingsChart(groups: Map<YearMonth, List<EconomyMovement>>, target: Long, minimum: Long) {
    val values = groups.values.map(::monthlySavings)
    val positiveMaximum = (values.filter { it > 0L } + listOf(target, minimum).filter { it > 0L }).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val negativeMaximum = values.filter { it < 0L }.minOrNull()?.let { kotlin.math.abs(it) }?.coerceAtLeast(1L) ?: 1L
    val totalRange = (positiveMaximum + negativeMaximum).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val baseline = size.height * positiveMaximum.toFloat() / totalRange
            val slot = size.width / groups.size.coerceAtLeast(1)
            val barWidth = (slot * 0.55f).coerceAtLeast(8f)
            drawLine(Color.Gray, androidx.compose.ui.geometry.Offset(0f, baseline), androidx.compose.ui.geometry.Offset(size.width, baseline), 2f)
            fun yFor(value: Long): Float = baseline - (value.toFloat() / totalRange) * size.height
            if (target != 0L) drawLine(Color(0xFF244C35), androidx.compose.ui.geometry.Offset(0f, yFor(target)), androidx.compose.ui.geometry.Offset(size.width, yFor(target)), 2f)
            if (minimum != 0L && minimum != target) drawLine(Color(0xFFD39A2C), androidx.compose.ui.geometry.Offset(0f, yFor(minimum)), androidx.compose.ui.geometry.Offset(size.width, yFor(minimum)), 2f)
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 11.dp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                canvas.nativeCanvas.drawText("0 €", 4f, baseline - 4f, paint)
                if (target == minimum && target != 0L) {
                    canvas.nativeCanvas.drawText("Objetivo/mínimo ${EconomyViewModel.formatCents(target)}", 4f, yFor(target) - 4f, paint)
                } else {
                    if (target != 0L) canvas.nativeCanvas.drawText("Objetivo ${EconomyViewModel.formatCents(target)}", 4f, yFor(target) - 4f, paint)
                    if (minimum != 0L) canvas.nativeCanvas.drawText("Mínimo ${EconomyViewModel.formatCents(minimum)}", 4f, yFor(minimum) - 4f, paint)
                }
            }
            groups.entries.forEachIndexed { index, entry ->
                if (entry.key > YearMonth.now() && entry.value.isEmpty()) return@forEachIndexed
                val amount = monthlySavings(entry.value)
                val height = size.height * kotlin.math.abs(amount).toFloat() / totalRange
                val x = slot * index + (slot - barWidth) / 2f
                val top = if (amount >= 0) baseline - height else baseline
                drawRect(if (amount >= 0) Color(0xFFB33A3A) else Color.Black, androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Size(barWidth, height))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            groups.keys.forEach { month -> Text(month.month.getDisplayName(TextStyle.SHORT, Locale("es", "ES")).replaceFirstChar { it.uppercase(Locale("es", "ES")) }, fontWeight = FontWeight.Bold) }
        }
        Text("Rojo: ahorro positivo · Negro: pérdida", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CompactStat(label: String, amount: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(EconomyViewModel.formatCents(amount), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatSpanishMonth(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
    .replaceFirstChar { it.uppercase(Locale("es", "ES")) } + " " + month.year
