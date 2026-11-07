package com.majyd.casa.ui.economy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EconomyHistoryScreen(state: EconomyUiState, viewModel: EconomyViewModel, onBack: () -> Unit) {
    val income = state.movements.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }
    val expense = state.movements.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }
    val investment = state.movements.filter { it.type == EconomyMovementType.INVESTMENT }.sumOf { it.amountCents }
    val groups = state.movements.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) }.toSortedMap()
    val expenseByCategory = state.movements.filter { it.type == EconomyMovementType.EXPENSE }
        .groupBy { it.categoryId }
        .mapValues { (_, values) -> values.sumOf { it.amountCents } }
        .toList()
        .sortedByDescending { it.second }

    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            title = { Text("Histórico y estadísticas", fontWeight = FontWeight.ExtraBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
    }) { padding ->
        EconomyBackground {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item {
                Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF8F1E8).copy(alpha = 0.92f))) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EconomySummaryRow("Ingresos", income, Color(0xFF3B7A4A))
                        EconomySummaryRow("Gastos", expense, Color(0xFFC44646))
                        EconomySummaryRow("Inversiones", investment, Color(0xFF8E55B8))
                        EconomySummaryRow("Ahorro", income - expense - investment, if (income >= expense + investment) Color(0xFF3B7A4A) else Color(0xFFC44646))
                    }
                }
            }
            item { SectionTitle("Evolución mensual") }
            item { androidx.compose.material3.OutlinedButton(onClick = viewModel::loadDemoData, modifier = Modifier.fillMaxWidth()) { Text("Completar datos de prueba desde junio", fontWeight = FontWeight.Bold) } }
            if (groups.isNotEmpty()) {
                item { Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF8F1E8).copy(alpha = 0.92f))) { Column(Modifier.padding(14.dp)) { MonthlyIncomeExpenseChart(groups) } } }
                items(emptyList<Pair<YearMonth, List<EconomyMovement>>>()) { (month, values) ->
                    val monthIncome = values.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }
                    val monthExpense = values.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }
                    Text("${formatSpanishMonth(month)}: ingresos ${EconomyViewModel.formatCents(monthIncome)} · gastos ${EconomyViewModel.formatCents(monthExpense)} · ahorro ${EconomyViewModel.formatCents(monthIncome - monthExpense)}")
                }
            }
            item { SectionTitle("Gastos por categoría") }
            items(expenseByCategory) { (categoryId, amount) ->
                val name = state.categories.firstOrNull { it.id == categoryId }?.name ?: "Categoría archivada"
                Card {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("$name:", fontWeight = FontWeight.Bold)
                        Text(EconomyViewModel.formatCents(amount), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            val investmentByCategory = state.movements.filter { it.type == EconomyMovementType.INVESTMENT }
                .groupBy { it.categoryId }
                .mapValues { (_, values) -> values.sumOf { it.amountCents } }
                .toList()
                .sortedByDescending { it.second }
            item { SectionTitle("Inversiones por categoría") }
            items(investmentByCategory) { (categoryId, amount) ->
                val name = state.categories.firstOrNull { it.id == categoryId }?.name ?: "Categoría archivada"
                Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("$name:", fontWeight = FontWeight.Bold); Text(EconomyViewModel.formatCents(amount), color = Color(0xFF8E55B8), fontWeight = FontWeight.ExtraBold) } }
            }
            }
        }
    }
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
    Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF8F1E8).copy(alpha = 0.92f))) {
        Text(title, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MonthlyIncomeExpenseChart(groups: Map<YearMonth, List<EconomyMovement>>) {
    val maximum = groups.values.flatMap { values -> values }.maxOfOrNull { it.amountCents }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val slot = size.width / groups.size
            val barWidth = (slot * 0.18f).coerceAtLeast(7f)
            groups.entries.forEachIndexed { index, entry ->
                val income = entry.value.filter { it.type == EconomyMovementType.INCOME }.sumOf { it.amountCents }
                val expense = entry.value.filter { it.type == EconomyMovementType.EXPENSE }.sumOf { it.amountCents }
                val investment = entry.value.filter { it.type == EconomyMovementType.INVESTMENT }.sumOf { it.amountCents }
                val incomeHeight = size.height * income.toFloat() / maximum.toFloat()
                val expenseHeight = size.height * expense.toFloat() / maximum.toFloat()
                val investmentHeight = size.height * investment.toFloat() / maximum.toFloat()
                val center = slot * index + slot / 2f
                drawRoundRect(Color(0xFF4F8A5B), androidx.compose.ui.geometry.Offset(center - barWidth * 1.5f - 3f, size.height - incomeHeight), androidx.compose.ui.geometry.Size(barWidth, incomeHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                drawRoundRect(Color(0xFFC44646), androidx.compose.ui.geometry.Offset(center - barWidth / 2f, size.height - expenseHeight), androidx.compose.ui.geometry.Size(barWidth, expenseHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                drawRoundRect(Color(0xFF8E55B8), androidx.compose.ui.geometry.Offset(center + barWidth / 2f + 3f, size.height - investmentHeight), androidx.compose.ui.geometry.Size(barWidth, investmentHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            groups.keys.forEach { month -> Text(month.month.getDisplayName(TextStyle.SHORT, Locale("es", "ES")).replaceFirstChar { it.uppercase(Locale("es", "ES")) }, fontWeight = FontWeight.Bold) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Ingresos", color = Color(0xFF4F8A5B), fontWeight = FontWeight.Bold)
            Text("Gastos", color = Color(0xFFC44646), fontWeight = FontWeight.Bold)
            Text("Inversiones", color = Color(0xFF8E55B8), fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatSpanishMonth(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
    .replaceFirstChar { it.uppercase(Locale("es", "ES")) } + " " + month.year
