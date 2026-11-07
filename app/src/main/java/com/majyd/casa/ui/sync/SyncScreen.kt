package com.majyd.casa.ui.sync

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.AppPreferences
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    preferences: AppPreferences,
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showOptions by remember { mutableStateOf(false) }
    var unlinkDevice by remember { mutableStateOf<String?>(null) }
    var localName by remember(state.localDisplayName) { mutableStateOf(state.localDisplayName) }
    var wifiDraft by remember(state.domesticWifiSsid) { mutableStateOf(state.domesticWifiSsid) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshWifiState()
    }
    val linkedDevice = state.authorizedDevices.firstOrNull { it.deviceId != state.localDeviceId && !it.revoked }
    val isLinked = linkedDevice != null
    val remoteAvailable = linkedDevice?.let { remote -> state.devices.any { it.deviceId == remote.deviceId } } == true

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) { viewModel.refreshWifiState() }

    unlinkDevice?.let { deviceId ->
        AlertDialog(
            onDismissRequest = { unlinkDevice = null },
            title = { Text("Desvincular móvil") },
            text = { Text("¿Quieres desvincular este móvil de la casa?") },
            confirmButton = {
                TextButton(onClick = { viewModel.unlinkDevice(deviceId); unlinkDevice = null }) { Text("Desvincular") }
            },
            dismissButton = { TextButton(onClick = { unlinkDevice = null }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sincronización") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (isLinked) "Móvil vinculado" else "Sin vincular", style = MaterialTheme.typography.titleLarge)
                        Text(if (isLinked) "Este móvil puede sincronizar los datos de la casa." else "Vincula este móvil con otro conectado a la misma Wi-Fi.")
                        Text("Red doméstica", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = wifiDraft,
                            onValueChange = { wifiDraft = it },
                            label = { Text("SSID de la Wi-Fi permitida") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { viewModel.updateDomesticWifiSsid(wifiDraft) },
                            enabled = wifiDraft != state.domesticWifiSsid,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Guardar red") }
                        Text(
                            when {
                                !state.wifiConnected -> "No estás conectado a una red Wi-Fi"
                                state.currentWifiSsid == null -> "Android no permite identificar el SSID en este momento"
                                state.domesticWifiSsid.isBlank() -> "Conectado a ${state.currentWifiSsid}"
                                state.currentWifiSsid == state.domesticWifiSsid -> "Conectado a la red doméstica configurada"
                                else -> "Conectado a ${state.currentWifiSsid}, distinta de la red configurada"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            OutlinedButton(onClick = { locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                                Text("Permitir identificar la Wi-Fi")
                            }
                        }
                        OutlinedTextField(
                            value = localName,
                            onValueChange = { localName = it },
                            label = { Text("Nombre de este móvil") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = { viewModel.renameDevice(localName) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar nombre") }
                        if (isLinked) {
                            Text("Vinculado con ${linkedDevice?.displayName.orEmpty()}")
                            Text(if (remoteAvailable) "Disponible en esta Wi-Fi" else "No disponible ahora")
                            Text(
                                state.lastSyncAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Aún no se ha sincronizado",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = viewModel::synchronizeAll, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Sync, null)
                                Text(if (state.running) "Sincronizando..." else "Sincronizar ahora", Modifier.padding(start = 8.dp))
                            }
                            OutlinedButton(onClick = { linkedDevice?.deviceId?.let { unlinkDevice = it } }, modifier = Modifier.fillMaxWidth()) {
                                Text("Desvincular dispositivo")
                            }
                            OutlinedButton(onClick = { showOptions = !showOptions }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Link, null)
                                Text("Vincular otro móvil", Modifier.padding(start = 8.dp))
                            }
                        } else {
                            Button(onClick = { showOptions = !showOptions }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Link, null)
                                Text("Vincular otro móvil", Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
            if (showOptions) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Vincular otro móvil", style = MaterialTheme.typography.titleMedium)
                            Text("En el móvil anfitrión aparece un código de seis cifras. Ambos móviles deben estar conectados a la misma Wi-Fi.")
                            Text(state.pairingCode, style = MaterialTheme.typography.displaySmall)
                            Text("Este código caduca en 15 minutos y se bloquea tras 5 intentos fallidos.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = viewModel::regeneratePairingCode) { Text("Generar otro código") }
                            OutlinedButton(
                                onClick = {
                                    state.devices.firstOrNull()?.let(viewModel::select)
                                        ?: viewModel.showMessage("No se encuentra el móvil anfitrión en esta Wi-Fi")
                                },
                                enabled = state.devices.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Buscar en esta Wi-Fi") }
                            OutlinedTextField(
                                value = state.joinCode,
                                onValueChange = viewModel::updateJoinCode,
                                label = { Text("Código de seis cifras") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(onClick = viewModel::pairSelected, enabled = !state.running && state.joinCode.length == 6, modifier = Modifier.fillMaxWidth()) { Text("Conectar") }
                            if (state.devices.isNotEmpty()) {
                                Text("Móviles encontrados", style = MaterialTheme.typography.titleSmall)
                                state.devices.forEach { device ->
                                    Card(Modifier.fillMaxWidth().clickable { viewModel.select(device) }) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(device.serviceName, Modifier.weight(1f))
                                            if (state.selectedDevice?.serviceName == device.serviceName) Text("Seleccionado")
                                        }
                                    }
                                }
                            } else Text("Espera unos segundos mientras se buscan móviles en esta Wi-Fi.")
                        }
                    }
                }
            }
            state.message?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
}
