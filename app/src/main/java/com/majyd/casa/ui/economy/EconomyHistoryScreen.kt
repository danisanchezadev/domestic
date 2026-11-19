package com.majyd.casa.ui.economy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
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
internal fun EconomyHistoryScreen(
    state: EconomyUiState,
    viewModel: EconomyViewModel,
    onBack: () -> Unit,
    onOpenMovements: (YearMonth) -> Unit,
) {
    val currentMonth = YearMonth.now()
    val currentFiscalYear = if (currentMonth.monthValue >= state.settings.fiscalStartMonth) currentMonth.year else currentMonth.year - 1
    var selectedFiscalYear by remember(state.settings.fiscalStartMonth) { mutableStateOf(currentFiscalYear) }
    var showCategoryExpenses by remember { mutableStateOf(false) }
    val fiscalStart = YearMonth.of(selectedFiscalYear, state.settings.fiscalStartMonth)
    val fiscalTitle = if (state.settings.fiscalStartMonth == 1) "$selectedFiscalYear" else "$selectedFiscalYear/${selectedFiscalYear + 1}"
    val fiscalMonths = (0..11).map { offset -> fiscalStart.plusMonths(offset.toLong()) }
    val groups = fiscalMonths.associateWith { month ->
        state.historyMovements.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
    }
    val exerciseMovements = groups.values.flatten()
    LaunchedEffect(selectedFiscalYear, state.settings.fiscalStartMonth) { viewModel.loadHistory(fiscalStart, fiscalStart.plusMonths(11)) }
    if (showCategoryExpenses) {
        EconomyCategoryExpensesScreen(
            movements = exerciseMovements,
            categories = state.categories,
            fiscalTitle = fiscalTitle,
            onBack = { showCategoryExpenses = false },
        )
        return
    }
    val totals = EconomyCalculations.totals(exerciseMovements)
    val income = totals.incomeCents
    val expense = totals.expenseCents
    val investment = totals.investmentCents
    val savings = totals.liquidityCents
    val displayGroups = groups.filter { (month, values) -> month <= currentMonth || values.isNotEmpty() }

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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { selectedFiscalYear-- }) { Icon(Icons.Filled.ChevronLeft, "Ejercicio anterior") }
                    Text("Ejercicio $fiscalTitle", Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    IconButton(onClick = { selectedFiscalYear++ }) { Icon(Icons.Filled.ChevronRight, "Ejercicio siguiente") }
                }
            }
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
            if (displayGroups.isEmpty()) {
                item { Text("Sin movimientos registrados", fontWeight = FontWeight.Bold) }
            } else {
                item { SectionTitle("Ahorro mensual") }
                item { Card { Column(Modifier.padding(14.dp)) { MonthlySavingsChart(displayGroups, state.target.targetCents, state.target.minimumCents) } } }
                item { SectionTitle("Resumen mensual") }
                item { MonthlyEconomyTable(displayGroups) }
            }
            item { OutlinedButton(onClick = { showCategoryExpenses = true }, Modifier.fillMaxWidth()) { Text("Gastos por categoría", fontWeight = FontWeight.Bold) } }
            item { OutlinedButton(onClick = { onOpenMovements(fiscalStart) }, Modifier.fillMaxWidth()) { Text("Ver movimientos", fontWeight = FontWeight.Bold) } }
        }
    }
}

private fun monthlySavings(values: List<EconomyMovement>): Long = values.sumOf {
    when (it.type) {
        EconomyMovementType.INCOME -> it.amountCents
        EconomyMovementType.EXPENSE, EconomyMovementType.INVESTMENT -> -it.amountCents
    }
}

@Composable
private fun MonthlyEconomyTable(groups: Map<YearMonth, List<EconomyMovement>>) {
    val scrollState = rememberScrollState()
    val totalValues = groups.values.flatten()
    val totalIncome = totalValues.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }
    val totalExpense = totalValues.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }
    val totalInvestment = totalValues.filter { it.type == EconomyMovementType.INVESTMENT }.sumOf { it.amountCents }
    val totalSavings = totalIncome - totalExpense - totalInvestment
    Column(Modifier.horizontalScroll(scrollState)) {
        Row(Modifier.width(620.dp)) {
            TableCell("Mes", 92.dp, true)
            TableCell("Ingresos", 132.dp, true, TextAlign.End)
            TableCell("Gastos", 132.dp, true, TextAlign.End)
            TableCell("Inversión", 132.dp, true, TextAlign.End)
            TableCell("Ahorro", 132.dp, true, TextAlign.End)
        }
        groups.forEach { (month, values) ->
            val income = values.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }
            val expense = values.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }
            val investment = values.filter { it.type == EconomyMovementType.INVESTMENT }.sumOf { it.amountCents }
            val savings = monthlySavings(values)
            Row(Modifier.width(620.dp)) {
                TableCell(month.month.getDisplayName(TextStyle.SHORT, Locale("es", "ES")).replaceFirstChar { it.uppercase() }, 92.dp)
                TableCell(compactAmount(income), 132.dp, textAlign = TextAlign.End)
                TableCell(compactAmount(expense), 132.dp, textAlign = TextAlign.End)
                TableCell(compactAmount(investment), 132.dp, textAlign = TextAlign.End)
                TableCell(compactAmount(savings), 132.dp, color = when { savings > 0L -> Color(0xFF1976FF); savings < 0L -> Color(0xFFC44646); else -> MaterialTheme.colorScheme.onSurface }, textAlign = TextAlign.End)
            }
        }
        Row(Modifier.width(620.dp)) {
            TableCell("Total", 92.dp, true)
            TableCell(compactAmount(totalIncome), 132.dp, true, TextAlign.End)
            TableCell(compactAmount(totalExpense), 132.dp, true, TextAlign.End)
            TableCell(compactAmount(totalInvestment), 132.dp, true, TextAlign.End)
            TableCell(compactAmount(totalSavings), 132.dp, true, TextAlign.End, when { totalSavings > 0L -> Color(0xFF1976FF); totalSavings < 0L -> Color(0xFFC44646); else -> MaterialTheme.colorScheme.onSurface })
        }
    }
}

@Composable
private fun TableCell(value: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false, textAlign: TextAlign = TextAlign.Start, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(value, Modifier.width(width).padding(horizontal = 6.dp, vertical = 8.dp), color = color, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal, textAlign = textAlign, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

private fun compactAmount(cents: Long): String = EconomyViewModel.formatCents(cents).replace(",00 €", " €")

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
    val positiveMaximum = maxOf(values.filter { it > 0L }.maxOfOrNull { it.toDouble() } ?: 0.0, target.toDouble().coerceAtLeast(0.0), minimum.toDouble().coerceAtLeast(0.0), 1.0)
    val negativeMaximum = maxOf(values.filter { it < 0L }.maxOfOrNull { kotlin.math.abs(it.toDouble()) } ?: 0.0, 1.0)
    val totalRange = positiveMaximum + negativeMaximum
    val baselineFraction = positiveMaximum / totalRange
    val plotHeight = 180.dp
    val labelWidth = 112.dp
    val monthWidth = 58.dp
    val plotWidth = monthWidth * groups.size.coerceAtLeast(1)
    val scrollState = rememberScrollState()
    fun fractionFor(value: Long): Float = (baselineFraction - value.toDouble() / totalRange).toFloat().coerceIn(0f, 1f)
    val targetFraction = fractionFor(target)
    val minimumFraction = fractionFor(minimum)
    val minimumLabelFraction = if (target != 0L && minimum != 0L && kotlin.math.abs(targetFraction - minimumFraction) < 0.10f) (minimumFraction + 0.10f).coerceIn(0.04f, 0.96f) else minimumFraction
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(labelWidth).height(plotHeight)) {
                Text("0 €", Modifier.align(Alignment.TopEnd).offset(y = (baselineFraction * plotHeight.value).dp - 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (target == minimum && target != 0L) Text("Obj./mín. ${EconomyViewModel.formatCents(target)}", Modifier.align(Alignment.TopEnd).offset(y = (targetFraction * plotHeight.value).dp - 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else {
                    if (target != 0L) Text("Obj. ${EconomyViewModel.formatCents(target)}", Modifier.align(Alignment.TopEnd).offset(y = (targetFraction * plotHeight.value).dp - 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (minimum != 0L) Text("Mín. ${EconomyViewModel.formatCents(minimum)}", Modifier.align(Alignment.TopEnd).offset(y = (minimumLabelFraction * plotHeight.value).dp - 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(scrollState)) {
                Canvas(Modifier.width(plotWidth).height(plotHeight)) {
                    val baseline = size.height * baselineFraction.toFloat()
                    val slot = size.width / groups.size.coerceAtLeast(1)
                    val barWidth = (slot * 0.55f).coerceAtLeast(8f)
                    fun yFor(value: Long): Float = size.height * fractionFor(value)
                    drawLine(Color.Gray, androidx.compose.ui.geometry.Offset(0f, baseline), androidx.compose.ui.geometry.Offset(size.width, baseline), 2f)
                    if (target != 0L) drawLine(Color(0xFF244C35), androidx.compose.ui.geometry.Offset(0f, yFor(target)), androidx.compose.ui.geometry.Offset(size.width, yFor(target)), 2f)
                    if (minimum != 0L && minimum != target) drawLine(Color(0xFFD39A2C), androidx.compose.ui.geometry.Offset(0f, yFor(minimum)), androidx.compose.ui.geometry.Offset(size.width, yFor(minimum)), 2f)
                    groups.entries.forEachIndexed { index, entry ->
                        if (entry.key > YearMonth.now() && entry.value.isEmpty()) return@forEachIndexed
                        val amount = monthlySavings(entry.value)
                        val barHeight = size.height * kotlin.math.abs(amount.toDouble() / totalRange).toFloat()
                        val x = slot * index + (slot - barWidth) / 2f
                        val top = if (amount >= 0) baseline - barHeight else baseline
                        drawRect(if (amount >= 0) Color(0xFFB33A3A) else Color.Black, androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Size(barWidth, barHeight))
                    }
                }
                Row(Modifier.width(plotWidth)) {
                    groups.keys.forEach { month -> Box(Modifier.width(monthWidth).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) { Text(month.monthValue.toString(), fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1) } }
                }
            }
        }
    }
}

private fun formatSpanishMonth(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
    .replaceFirstChar { it.uppercase(Locale("es", "ES")) } + " " + month.year

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun EconomyCategoryExpensesScreen(
    movements: List<EconomyMovement>,
    categories: List<com.majyd.casa.data.model.EconomyCategory>,
    fiscalTitle: String,
    onBack: () -> Unit,
) {
    val expenseByCategory = movements.filter { it.type == EconomyMovementType.EXPENSE }
        .groupBy { it.categoryId }
        .mapValues { (_, values) -> values.sumOf { it.amountCents } }
        .toList()
        .sortedByDescending { it.second }
    val totalExpense = expenseByCategory.sumOf { it.second }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gastos por categoría", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Ejercicio $fiscalTitle", fontWeight = FontWeight.Bold) }
            if (expenseByCategory.isEmpty()) {
                item { Text("Sin gastos registrados en este ejercicio", fontWeight = FontWeight.Bold) }
            } else {
                items(expenseByCategory, key = { it.first }) { (categoryId, amount) ->
                    val name = categories.firstOrNull { it.id == categoryId }?.let { if (it.deleted) "${it.name} (archivada)" else it.name } ?: "Categoría archivada"
                    val percentage = amount.toFloat() / totalExpense.coerceAtLeast(1L)
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, fontWeight = FontWeight.Bold)
                                Text("${EconomyViewModel.formatCents(amount)} · ${(percentage * 100).toInt()}%", fontWeight = FontWeight.ExtraBold)
                            }
                            androidx.compose.material3.LinearProgressIndicator(progress = { percentage.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
