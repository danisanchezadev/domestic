package com.majyd.casa.ui.economy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.majyd.casa.data.model.EconomyCalculations
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EconomyMovementsScreen(
    state: EconomyUiState,
    viewModel: EconomyViewModel,
    initialMonth: YearMonth,
    onBack: () -> Unit,
) {
    var month by remember(initialMonth) { mutableStateOf(initialMonth) }
    var showPeriodPicker by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<EconomyMovementType?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EconomyMovement?>(null) }
    var pendingDelete by remember { mutableStateOf<EconomyMovement?>(null) }

    LaunchedEffect(month) { viewModel.loadTarget(month) }
    val loaded = state.target.monthKey == month.toString()
    val monthMovements = if (loaded) state.movements else emptyList()
    val filtered = monthMovements.filter { movement ->
        (typeFilter == null || movement.type == typeFilter) &&
            (categoryFilter == null || movement.categoryId == categoryFilter)
    }.sortedWith(compareByDescending<EconomyMovement> { it.dateEpochDay }.thenByDescending { it.id })
    val totals = EconomyCalculations.totals(monthMovements)
    val filtersActive = typeFilter != null || categoryFilter != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Movimientos", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PeriodSelector(month, onPrevious = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) }, onSelect = { showPeriodPicker = true })
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${monthMovements.size} movimientos", fontWeight = FontWeight.Bold)
                        Text("Balance ${EconomyViewModel.formatCents(totals.liquidityCents)}", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { showFilters = true }) {
                        Icon(Icons.Filled.FilterList, null)
                        Text(if (filtersActive) "Filtros · activos" else "Filtros")
                    }
                }
            }
            if (!loaded) {
                item { Text("Cargando movimientos…", fontWeight = FontWeight.Bold) }
            } else if (monthMovements.isEmpty()) {
                item { Text("Sin movimientos este mes", fontWeight = FontWeight.Bold) }
            } else if (filtered.isEmpty()) {
                item { Text("Ningún movimiento coincide con los filtros", fontWeight = FontWeight.Bold) }
            } else {
                items(filtered, key = { it.id }) { movement ->
                    MovementRow(movement, state.categories, onEdit = { editing = movement }, onDelete = { pendingDelete = movement })
                }
            }
        }
    }

    if (showPeriodPicker) EconomyPeriodPickerDialog(month, onDismiss = { showPeriodPicker = false }, onSelected = { month = it; showPeriodPicker = false })
    if (showFilters) EconomyFiltersDialog(state.categories, monthMovements.map { it.categoryId }.toSet(), typeFilter, categoryFilter, onDismiss = { showFilters = false }, onApply = { type, category -> typeFilter = type; categoryFilter = category; showFilters = false })
    editing?.let { movement ->
        EconomyMovementDialog(movement.type, movement, month, state.categories, viewModel, onDismiss = { editing = null })
    }
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
private fun PeriodSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = onPrevious) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        Text(EconomyViewModel.formatMonth(month), Modifier.clickable(onClick = onSelect).padding(horizontal = 12.dp, vertical = 10.dp), fontWeight = FontWeight.ExtraBold)
        IconButton(onClick = onNext) { Text("›", style = MaterialTheme.typography.headlineMedium) }
    }
}

internal fun sortEconomyMovements(movements: List<EconomyMovement>): List<EconomyMovement> =
    movements.sortedWith(compareByDescending<EconomyMovement> { it.dateEpochDay }.thenByDescending { it.id })

internal fun latestEconomyMovements(movements: List<EconomyMovement>, limit: Int = 5): List<EconomyMovement> =
    sortEconomyMovements(movements).take(limit.coerceAtLeast(0))
