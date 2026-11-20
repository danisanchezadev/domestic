package com.majyd.casa.ui.economy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
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
    selectedFiscalYear: Int,
    onFiscalYearChange: (Int) -> Unit,
) {
    val currentMonth = YearMonth.now()
    var showCategoryExpenses by remember { mutableStateOf(false) }
    val fiscalStart = YearMonth.of(selectedFiscalYear, state.settings.fiscalStartMonth)
    val fiscalTitle = if (state.settings.fiscalStartMonth == 1) "$selectedFiscalYear" else "$selectedFiscalYear/${selectedFiscalYear + 1}"
    val fiscalMonths = (0..11).map { offset -> fiscalStart.plusMonths(offset.toLong()) }
    val groups = fiscalMonths.associateWith { month ->
        state.historyMovements.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
    }
    val requestedRange = fiscalStart to fiscalStart.plusMonths(11)
    val historyReady = state.historyRange == requestedRange
    val exerciseMovements = if (historyReady) groups.values.flatten() else emptyList()
    LaunchedEffect(selectedFiscalYear, state.settings.fiscalStartMonth) { viewModel.loadHistory(fiscalStart, fiscalStart.plusMonths(11)) }
    if (!historyReady) {
        EconomyHistoryLoadingScreen(onBack)
        return
    }
    if (showCategoryExpenses) {
        EconomyCategoryExpensesScreen(
            movements = exerciseMovements,
            categories = state.categories,
            fiscalStart = fiscalStart,
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
    val displayGroups = displayableEconomyMonths(groups, currentMonth)

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
                    IconButton(onClick = { onFiscalYearChange(selectedFiscalYear - 1) }) { Icon(Icons.Filled.ChevronLeft, "Ejercicio anterior") }
                    Text("Ejercicio $fiscalTitle", Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    IconButton(onClick = { onFiscalYearChange(selectedFiscalYear + 1) }) { Icon(Icons.Filled.ChevronRight, "Ejercicio siguiente") }
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
            if (!historyReady) {
                item { Text("Cargando histórico…", fontWeight = FontWeight.Bold) }
            } else if (displayGroups.isEmpty()) {
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

internal fun displayableEconomyMonths(
    groups: Map<YearMonth, List<EconomyMovement>>,
    currentMonth: YearMonth,
): Map<YearMonth, List<EconomyMovement>> = groups.filter { (month, values) -> month <= currentMonth || values.isNotEmpty() }

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun EconomyHistoryLoadingScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico y estadísticas", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text("Cargando histórico…", fontWeight = FontWeight.Bold)
        }
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
    val monthWidth = 58.dp
    val plotWidth = monthWidth * groups.size.coerceAtLeast(1)
    val scrollState = rememberScrollState()
    fun fractionFor(value: Long): Float = (baselineFraction - value.toDouble() / totalRange).toFloat().coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ReferenceLegend("0", Color.Gray)
            if (target == minimum && target != 0L) ReferenceLegend("Objetivo/mínimo ${EconomyViewModel.formatCents(target)}", Color(0xFF244C35))
            else {
                if (target != 0L) ReferenceLegend("Objetivo ${EconomyViewModel.formatCents(target)}", Color(0xFF244C35))
                if (minimum != 0L) ReferenceLegend("Mínimo ${EconomyViewModel.formatCents(minimum)}", Color(0xFFD39A2C))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f).horizontalScroll(scrollState)) {
                Column(Modifier.width(plotWidth)) {
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
}

@Composable
private fun ReferenceLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(18.dp).height(3.dp).background(color))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatSpanishMonth(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
    .replaceFirstChar { it.uppercase(Locale("es", "ES")) } + " " + month.year

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun EconomyCategoryExpensesScreen(
    movements: List<EconomyMovement>,
    categories: List<com.majyd.casa.data.model.EconomyCategory>,
    fiscalStart: YearMonth,
    fiscalTitle: String,
    onBack: () -> Unit,
) {
    var selectedPeriod by remember { mutableStateOf<YearMonth?>(null) }
    var periodExpanded by remember { mutableStateOf(false) }
    val periodMovements = selectedPeriod?.let { selected ->
        movements.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == selected }
    } ?: movements
    val expenseByCategory = periodMovements.filter { it.type == EconomyMovementType.EXPENSE }
        .groupBy { it.categoryId }
        .mapValues { (_, values) -> values.sumOf { it.amountCents } }
        .toList()
        .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
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
            item {
                Box {
                    OutlinedButton(onClick = { periodExpanded = true }) {
                        Text(selectedPeriod?.let(EconomyViewModel::formatMonth) ?: "Ejercicio completo", fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Ejercicio completo") },
                            onClick = { selectedPeriod = null; periodExpanded = false },
                        )
                        (0..11).forEach { offset ->
                            val option = fiscalStart.plusMonths(offset.toLong())
                            DropdownMenuItem(
                                text = { Text(EconomyViewModel.formatMonth(option)) },
                                onClick = { selectedPeriod = option; periodExpanded = false },
                            )
                        }
                    }
                }
            }
            if (expenseByCategory.isEmpty()) {
                item { Text("Sin gastos registrados en este ejercicio", fontWeight = FontWeight.Bold) }
            } else {
                item { CategoryExpenseChart(expenseByCategory, categories) }
                items(expenseByCategory, key = { it.first }) { (categoryId, amount) ->
                    val name = categories.firstOrNull { it.id == categoryId }?.let { if (it.deleted) "${it.name} (archivada)" else it.name } ?: "Categoría archivada"
                    val percentage = amount.toFloat() / totalExpense.coerceAtLeast(1L)
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.25f
                                if (compact) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(name, fontWeight = FontWeight.Bold)
                                        Text("${EconomyViewModel.formatCents(amount)} · ${(percentage * 100).toInt()}%", fontWeight = FontWeight.ExtraBold)
                                    }
                                } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2)
                                Text("${EconomyViewModel.formatCents(amount)} · ${(percentage * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                            }
                                }
                            }
                            androidx.compose.material3.LinearProgressIndicator(progress = { percentage.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryExpenseChart(
    expenseByCategory: List<Pair<String, Long>>,
    categories: List<com.majyd.casa.data.model.EconomyCategory>,
) {
    val scrollState = rememberScrollState()
    val slotWidth = 92.dp
    val chartHeight = 190.dp
    val chartWidth = slotWidth * expenseByCategory.size.coerceAtLeast(1)
    val maximum = expenseByCategory.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Comparativa de gastos", fontWeight = FontWeight.ExtraBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                Column(Modifier.width(chartWidth)) {
                    Canvas(Modifier.width(chartWidth).height(chartHeight)) {
                        val slot = size.width / expenseByCategory.size.coerceAtLeast(1)
                        val barWidth = (slot * 0.58f).coerceAtLeast(22f)
                        expenseByCategory.forEachIndexed { index, (_, amount) ->
                            val height = size.height * (amount.toFloat() / maximum.toFloat())
                            val x = slot * index + (slot - barWidth) / 2f
                            val top = size.height - height
                            drawRoundRect(
                                color = Color(0xFFC44646),
                                topLeft = androidx.compose.ui.geometry.Offset(x, top),
                                size = androidx.compose.ui.geometry.Size(barWidth, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                            )
                        }
                    }
                    Row(Modifier.width(chartWidth)) {
                        expenseByCategory.forEach { (_, amount) ->
                            Text(
                                EconomyViewModel.formatCents(amount),
                                Modifier.width(slotWidth).padding(horizontal = 2.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                    Row(Modifier.width(chartWidth)) {
                        expenseByCategory.forEach { (categoryId, _) ->
                            val name = categories.firstOrNull { it.id == categoryId }?.name ?: "Archivada"
                            Text(
                                name,
                                Modifier.width(slotWidth).padding(horizontal = 4.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}
