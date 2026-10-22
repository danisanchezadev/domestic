package com.majyd.casa.ui.sync

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.AppPreferences
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.collectLatest

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
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { message -> snackbarHostState.showSnackbar(message) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        state.backupContent?.let { content ->
            val exported = context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } != null
            if (exported) viewModel.markBackupExported()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { viewModel.importBackup(it.readBytes().toString(Charsets.UTF_8)) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        decodePairingQr(bitmap)?.let(viewModel::updateJoinPayload)
            ?: viewModel.showMessage(context.getString(com.majyd.casa.R.string.sync_qr_unreadable))
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
        else viewModel.showMessage(context.getString(com.majyd.casa.R.string.sync_camera_permission))
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { contentPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(title = { Text("Sincronizaci\u00f3n") })
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(com.majyd.casa.R.string.sync_status_title), style = MaterialTheme.typography.titleMedium)
                        if (state.running) {
                            CircularProgressIndicator()
                        }
                        Text(state.message ?: stringResource(com.majyd.casa.R.string.sync_phase_idle))
                        state.remoteDevice?.let { Text(stringResource(com.majyd.casa.R.string.sync_device_label, it), style = MaterialTheme.typography.bodySmall) }
                        if (state.finishedAt != null && !state.running) {
                            Text(stringResource(com.majyd.casa.R.string.sync_counters, state.sent, state.acknowledged, state.received, state.applied), style = MaterialTheme.typography.bodySmall)
                            state.durationMs?.let { Text(stringResource(com.majyd.casa.R.string.sync_duration, it / 1000), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dispositivos cercanos", style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(com.majyd.casa.R.string.sync_same_home_only))
                        Text(
                            state.lastSyncAt?.let { stringResource(com.majyd.casa.R.string.sync_last_at, DateFormat.getDateTimeInstance().format(Date(it))) }
                                ?: stringResource(com.majyd.casa.R.string.sync_last_never),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = state.pairingCode,
                            onValueChange = viewModel::updatePairingCode,
                            label = { Text(stringResource(com.majyd.casa.R.string.sync_pairing_code_label)) },
                            singleLine = true,
                        )
                        if (state.pairingPayload.isNotBlank()) {
                            Image(
                                bitmap = createPairingQr(state.pairingPayload).asImageBitmap(),
                                contentDescription = stringResource(com.majyd.casa.R.string.sync_pairing_qr_description),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Button(onClick = viewModel::savePairingCode) { Text(stringResource(com.majyd.casa.R.string.sync_save_code)) }
                        Button(onClick = viewModel::regeneratePairingCode) { Text(stringResource(com.majyd.casa.R.string.sync_regenerate_code)) }
                        Button(onClick = { viewModel.prepareBackup() }) { Text(stringResource(com.majyd.casa.R.string.sync_prepare_backup)) }
                        if (state.backupContent != null) {
                            Button(onClick = { exportLauncher.launch("casa-backup.bin") }) { Text(stringResource(com.majyd.casa.R.string.sync_export_backup)) }
                        }
                        Button(onClick = { importLauncher.launch(arrayOf("application/octet-stream", "text/plain")) }) { Text(stringResource(com.majyd.casa.R.string.sync_import_backup)) }
                        OutlinedTextField(
                            value = state.joinPayload,
                            onValueChange = viewModel::updateJoinPayload,
                            label = { Text(stringResource(com.majyd.casa.R.string.sync_join_payload_label)) },
                            singleLine = true,
                        )
                        Button(onClick = {
                            if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                cameraLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }) { Text(stringResource(com.majyd.casa.R.string.sync_read_qr)) }
                        Button(onClick = viewModel::joinFromPayload) { Text(stringResource(com.majyd.casa.R.string.sync_join_home)) }
                        if (state.devices.isEmpty()) Text(stringResource(com.majyd.casa.R.string.sync_searching_devices))
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dispositivos vinculados", style = MaterialTheme.typography.titleMedium)
                        state.authorizedDevices.forEach { device ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(device.displayName)
                                Text(stringResource(com.majyd.casa.R.string.sync_last_connection, DateFormat.getDateTimeInstance().format(Date(device.lastSeenAt))), style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { viewModel.revokeDevice(device.deviceId) },
                                    enabled = device.deviceId != state.localDeviceId,
                                ) {
                                    Text(stringResource(com.majyd.casa.R.string.sync_unlink))
                                }
                            }
                        }
                    }
                }
            }
            items(state.devices, key = { it.deviceId ?: it.serviceName }) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.select(device) },
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(device.serviceName, style = MaterialTheme.typography.titleMedium)
                        Text("${device.host}:${device.port}")
                        if (state.selectedDevice?.serviceName == device.serviceName) Text(stringResource(com.majyd.casa.R.string.sync_selected))
                    }
                }
            }
            item {
                Button(
                    onClick = viewModel::synchronizeAll,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.running) stringResource(com.majyd.casa.R.string.sync_running) else stringResource(com.majyd.casa.R.string.sync_retry))
                }
            }
            state.message?.let { message -> item { Text(message) } }
            item { Button(onClick = onBack) { Text(stringResource(com.majyd.casa.R.string.sync_close)) } }
        }
    }
    }
}
