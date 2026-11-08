package com.majyd.casa.ui.economy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
internal fun EconomyHistoryScreen(state: EconomyUiState, viewModel: EconomyViewModel, onBack: () -> Unit) {
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
    LaunchedEffect(selectedFiscalYear, state.settings.fiscalStartMonth) { viewModel.loadHistory(fiscalStart, fiscalStart.plusMonths(11)) }
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
    val displayGroups = groups.filter { (month, values) -> month <= currentMonth || values.isNotEmpty() }
    val totalExpense = expenseByCategory.sumOf { it.second }.coerceAtLeast(1L)

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
                item { MonthlyEconomyTable(displayGroups, savings) }
            }
            if (expenseByCategory.isNotEmpty()) {
                item { SectionTitle("Gastos por categoría") }
                items(expenseByCategory, key = { it.first }) { (categoryId, amount) ->
                    val name = state.categories.firstOrNull { it.id == categoryId }?.name ?: "Categoría archivada"
                    val percentage = amount.toFloat() / totalExpense
                    Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, fontWeight = FontWeight.Bold); Text("${EconomyViewModel.formatCents(amount)} · ${(percentage * 100).toInt()}%", fontWeight = FontWeight.ExtraBold) }; androidx.compose.material3.LinearProgressIndicator(progress = { percentage.coerceIn(0f, 1f) }, Modifier.fillMaxWidth()) } }
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

@Composable
private fun MonthlyEconomyTable(groups: Map<YearMonth, List<EconomyMovement>>, totalSavings: Long) {
    val scrollState = rememberScrollState()
    val totalValues = groups.values.flatten()
    val totalIncome = totalValues.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }
    val totalExpense = totalValues.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }
    val totalInvestment = totalValues.filter { it.type == EconomyMovementType.INVESTMENT }.sumOf { it.amountCents }
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
    }
}

private fun formatSpanishMonth(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
    .replaceFirstChar { it.uppercase(Locale("es", "ES")) } + " " + month.year
