package com.majyd.casa.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class SetupMode { CHOICE, CREATE, JOIN }

@Composable
fun SetupWizardScreen(viewModel: SetupViewModel = hiltViewModel()) {
    var mode by remember { mutableStateOf(SetupMode.CHOICE) }
    var homeName by remember { mutableStateOf("") }
    var firstPersonName by remember { mutableStateOf("") }
    var otherPeople by remember { mutableStateOf("") }
    var domesticWifiSsid by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var newProfileName by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val nearbyDevices by viewModel.nearbyDevices.collectAsStateWithLifecycle()
    val household by viewModel.household.collectAsStateWithLifecycle()
    val awaitingProfile by viewModel.awaitingProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.complete(homeName, firstPersonName, otherPeople, domesticWifiSsid)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (awaitingProfile) {
            Text("Elegir perfil", style = MaterialTheme.typography.headlineSmall)
            Text("Este telefono ya esta vinculado al hogar. Elige la persona que lo usara.")
            household.people.forEach { person ->
                Button(onClick = { viewModel.selectProfile(person.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(person.name)
                }
            }
            OutlinedTextField(
                value = newProfileName,
                onValueChange = { newProfileName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Crear un perfil nuevo") },
                singleLine = true,
            )
            Button(
                onClick = { viewModel.createProfile(newProfileName) },
                enabled = newProfileName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Crear perfil y continuar") }
        } else when (mode) {
            SetupMode.CHOICE -> {
                Text("Casa", style = MaterialTheme.typography.headlineMedium)
                Text("Que quieres hacer en este dispositivo?", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { mode = SetupMode.CREATE }, modifier = Modifier.fillMaxWidth()) { Text("Crear un hogar") }
                Button(onClick = { mode = SetupMode.JOIN }, modifier = Modifier.fillMaxWidth()) { Text("Unirme a un hogar existente") }
            }
            SetupMode.CREATE -> {
                Button(onClick = { mode = SetupMode.CHOICE }, modifier = Modifier.fillMaxWidth()) { Text("Volver a opciones") }
                Text("Crear un hogar", style = MaterialTheme.typography.headlineSmall)
                Text("Configura el hogar y las personas que lo utilizaran.")
                OutlinedTextField(homeName, { homeName = it.take(18) }, Modifier.fillMaxWidth(), label = { Text("Nombre del hogar") }, singleLine = true)
                OutlinedTextField(firstPersonName, { firstPersonName = it }, Modifier.fillMaxWidth(), label = { Text("Tu nombre") }, singleLine = true)
                Text("Este dispositivo se asociara a este nombre.")
                OutlinedTextField(otherPeople, { otherPeople = it }, Modifier.fillMaxWidth(), label = { Text("Otras personas, separadas por comas") }, singleLine = true)
                OutlinedTextField(domesticWifiSsid, { domesticWifiSsid = it }, Modifier.fillMaxWidth(), label = { Text("Red Wi-Fi domestica (opcional)") }, singleLine = true)
                Text("La red Wi-Fi se usa como filtro adicional para la sincronizacion local.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        if (domesticWifiSsid.isNotBlank() && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            viewModel.complete(homeName, firstPersonName, otherPeople, domesticWifiSsid)
                        }
                    },
                    enabled = firstPersonName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Finalizar configuracion") }
            }
            SetupMode.JOIN -> {
                Button(onClick = { mode = SetupMode.CHOICE }, modifier = Modifier.fillMaxWidth()) { Text("Volver a opciones") }
                Text("Unirme a un hogar", style = MaterialTheme.typography.headlineSmall)
                Text("Los dos telefonos deben estar conectados a la misma Wi-Fi.")
                Button(onClick = viewModel::discoverNearby, modifier = Modifier.fillMaxWidth()) { Text("Buscar en esta Wi-Fi") }
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) pairingCode = value },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Codigo de emparejamiento") },
                    supportingText = { Text("Es el codigo de 6 cifras del telefono que creo el hogar.") },
                    singleLine = true,
                )
                nearbyDevices.forEach { device ->
                    Button(
                        onClick = { viewModel.joinNearby(device, pairingCode) },
                        enabled = pairingCode.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Unirme a ${device.serviceName}") }
                }
                Text("Al unirte, la sincronizacion se inicia automaticamente.", style = MaterialTheme.typography.bodySmall)
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
