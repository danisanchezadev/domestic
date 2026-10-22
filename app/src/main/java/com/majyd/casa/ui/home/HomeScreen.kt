package com.majyd.casa.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.AppPreferences
import com.majyd.casa.ui.navigation.AppDestination
import com.majyd.casa.ui.navigation.toDestination
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    preferences: AppPreferences,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val summary by viewModel.state.collectAsStateWithLifecycle()
    val orderedCards = remember(preferences.dashboardCards) {
        (AppPreferences.normalizeDashboardCards(preferences.dashboardCards).map { it.toDestination() })
            .distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.colorScheme.background),
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = preferences.homeName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    IconButton(onClick = { onNavigate(AppDestination.Settings.route) }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Ajustes",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(
                    onClick = viewModel::synchronizeNow,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text("Sincronizar") }
            }
        }
        Spacer(Modifier.height(20.dp))
        orderedCards.chunked(2).forEach { rowCards ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowCards.forEach { destination ->
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = destination.title,
                        subtitle = summaryFor(destination, summary),
                        icon = destination.icon,
                        onClick = { onNavigate(destination.route) },
                    )
                }
                if (rowCards.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun summaryFor(destination: AppDestination, summary: HomeSummaryState): String {
    return when (destination) {
        AppDestination.Shopping -> "${summary.pendingShopping} productos pendientes"
        AppDestination.Tasks -> "${summary.pendingTasks} tareas pendientes"
        AppDestination.Calendar -> "${summary.upcomingEvents} proximos eventos"
        AppDestination.WeeklyMenu -> summary.todayMenu?.let { "Hoy: $it" } ?: "Sin menu para hoy"
        AppDestination.Plans -> "Sin planes guardados"
        AppDestination.Trips -> "Sin viajes en preparacion"
        AppDestination.Home -> "Resumen del hogar"
        AppDestination.Appliances -> "Mantenimiento y electrodomesticos"
        AppDestination.Contacts -> "Contactos importantes"
        AppDestination.Economy -> "Todavia no desarrollada"
        AppDestination.Trash -> "Elementos eliminados recientemente"
        AppDestination.Sync -> summary.lastSyncAt?.let { "Ultima sincronizacion: ${SimpleDateFormat("d/M HH:mm", Locale("es", "ES")).format(Date(it))}" } ?: "Sin sincronizacion"
        AppDestination.Settings -> "Preferencias y personas"
        AppDestination.Search -> "Busqueda global"
    }
}
