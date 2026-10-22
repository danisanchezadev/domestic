package com.majyd.casa.ui.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.SyncSession
import com.majyd.casa.sync.SyncResult
import com.majyd.casa.sync.SyncCoordinator
import com.majyd.casa.sync.SyncError
import com.majyd.casa.sync.SyncFailureException
import com.majyd.casa.sync.SyncPhase
import com.majyd.casa.sync.SyncProgress
import com.majyd.casa.sync.transport.NearbyDevice
import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.data.local.dao.AuthorizedDeviceDao
import com.majyd.casa.data.local.entities.AuthorizedDeviceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.majyd.casa.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class SyncUiState(
    val devices: List<NearbyDevice> = emptyList(),
    val selectedDevice: NearbyDevice? = null,
    val pairingCode: String = "",
    val pairingPayload: String = "",
    val joinPayload: String = "",
    val running: Boolean = false,
    val message: String? = null,
    val authorizedDevices: List<AuthorizedDeviceEntity> = emptyList(),
    val backupContent: String? = null,
    val backupExported: Boolean = false,
    val localDeviceId: String? = null,
    val lastSyncAt: Long? = null,
    val phase: SyncPhase = SyncPhase.IDLE,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val remoteDevice: String? = null,
    val sent: Int = 0,
    val received: Int = 0,
    val acknowledged: Int = 0,
    val applied: Int = 0,
    val durationMs: Long? = null,
    val error: SyncError? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceManager: DeviceManager,
    private val syncSession: SyncSession,
    private val syncCoordinator: SyncCoordinator,
    private val settingsRepository: SettingsRepository,
    private val authorizedDeviceDao: AuthorizedDeviceDao,
) : ViewModel() {
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events
    private var pairingCode = ""

    init {
        viewModelScope.launch {
            val device = deviceManager.ensureDevice()
            pairingCode = settingsRepository.getPairingCode()
            _state.value = _state.value.copy(
                pairingCode = pairingCode,
                pairingPayload = "CASA|${device.householdId}|$pairingCode",
                localDeviceId = device.deviceId,
            )
        }
        viewModelScope.launch {
            settingsRepository.preferences.collect { preferences ->
                val current = _state.value
                val persistedPhase = when (preferences.lastManualResult) {
                    "SUCCESS" -> SyncPhase.SUCCESS
                    "NO_CHANGES" -> SyncPhase.NO_CHANGES
                    "FAILED" -> SyncPhase.FAILED
                    else -> SyncPhase.IDLE
                }
                _state.value = current.copy(
                    lastSyncAt = preferences.lastManualSyncAt,
                    phase = if (current.running) current.phase else persistedPhase,
                    finishedAt = if (current.running) current.finishedAt else preferences.lastManualSyncAt,
                    remoteDevice = if (current.running) current.remoteDevice else preferences.lastManualRemoteDevice,
                    sent = if (current.running) current.sent else preferences.lastManualSent,
                    received = if (current.running) current.received else preferences.lastManualReceived,
                    acknowledged = if (current.running) current.acknowledged else preferences.lastManualAcknowledged,
                    applied = if (current.running) current.applied else preferences.lastManualApplied,
                    durationMs = if (current.running) current.durationMs else preferences.lastManualDurationMs,
                    error = if (current.running) current.error else preferences.lastManualError?.let(::errorFromName),
                    message = if (current.running) current.message else preferences.lastManualMessage,
                )
            }
        }
        viewModelScope.launch {
            deviceManager.observeDevice()
                .flatMapLatest { device -> device?.let { authorizedDeviceDao.observeForHousehold(it.householdId) } ?: flowOf(emptyList()) }
                .collect { devices ->
                    _state.value = _state.value.copy(
                        authorizedDevices = devices,
                        localDeviceId = devices.firstOrNull { it.deviceId == _state.value.localDeviceId }?.deviceId ?: _state.value.localDeviceId,
                    )
                }
        }
        viewModelScope.launch {
            syncCoordinator.nearbyDevices.collect { devices ->
                _state.value = _state.value.copy(devices = devices)
            }
        }
    }

    fun select(device: NearbyDevice) {
        _state.value = _state.value.copy(selectedDevice = device, message = null)
    }

    fun updatePairingCode(code: String) {
        if (code.length <= 6 && code.all(Char::isDigit)) {
            _state.value = _state.value.copy(pairingCode = code)
        }
    }

    fun updateJoinPayload(value: String) { _state.value = _state.value.copy(joinPayload = value) }

    fun showMessage(value: String) { _state.value = _state.value.copy(message = value) }

    fun savePairingCode() = viewModelScope.launch {
        runCatching { settingsRepository.setPairingCode(_state.value.pairingCode) }
            .onSuccess {
                pairingCode = _state.value.pairingCode
                val device = deviceManager.ensureDevice()
                _state.value = _state.value.copy(pairingPayload = "CASA|${device.householdId}|$pairingCode", message = "Código guardado")
            }
            .onFailure { error -> _state.value = _state.value.copy(message = error.message ?: "Código no válido") }
    }

    fun regeneratePairingCode() = viewModelScope.launch {
        val code = (100000..999999).random().toString()
        settingsRepository.setPairingCode(code)
        val device = deviceManager.ensureDevice()
        // Regeneration is the explicit action that permits previously revoked devices to re-pair.
        authorizedDeviceDao.clearRevokedForHousehold(device.householdId)
        pairingCode = code
        _state.value = _state.value.copy(pairingCode = code, pairingPayload = "CASA|${device.householdId}|$code", message = "Código regenerado")
    }

    fun revokeDevice(deviceId: String) = viewModelScope.launch {
        if (!_state.value.backupExported) {
            prepareBackup()
            _state.value = _state.value.copy(message = "Exporta el backup antes de desvincular el dispositivo")
            return@launch
        }
        authorizedDeviceDao.revoke(deviceId)
        _state.value = _state.value.copy(message = "Dispositivo desvinculado", backupExported = false)
    }

    fun prepareBackup() = viewModelScope.launch {
        val device = deviceManager.ensureDevice()
        val code = settingsRepository.getPairingCode()
        val operations = syncSession.all(device.householdId)
        _state.value = _state.value.copy(
            backupContent = CredentialBackupCodec.encode(CredentialBackup(device.householdId, code, operations)),
            backupExported = false,
        )
    }

    fun markBackupExported() {
        _state.value = _state.value.copy(backupExported = true, message = "Backup exportado")
    }

    fun importBackup(value: String) = viewModelScope.launch {
        runCatching {
            val currentCode = settingsRepository.getPairingCode()
            CredentialBackupCodec.decode(value, currentCode)
        }.recoverCatching {
            val code = _state.value.pairingCode
            CredentialBackupCodec.decode(value, code)
        }.onSuccess { backup ->
            settingsRepository.setPairingCode(backup.pairingCode)
            deviceManager.joinHousehold(backup.householdId)
            backup.operations.forEach { syncSession.applyIncoming(it) }
            pairingCode = backup.pairingCode
            _state.value = _state.value.copy(pairingCode = pairingCode, pairingPayload = "CASA|${backup.householdId}|$pairingCode", message = "Backup restaurado")
        }.onFailure { error -> _state.value = _state.value.copy(message = error.message ?: "No se pudo restaurar el backup") }
    }

    fun joinFromPayload(value: String = _state.value.joinPayload) = viewModelScope.launch {
        val payload = parsePairingPayload(value)
        if (payload == null) {
            _state.value = _state.value.copy(message = "QR o código de emparejamiento no válido")
            return@launch
        }
        runCatching {
            settingsRepository.setPairingCode(payload.pairingCode)
            deviceManager.joinHousehold(payload.householdId)
        }.onSuccess {
            pairingCode = payload.pairingCode
            _state.value = _state.value.copy(pairingCode = pairingCode, pairingPayload = value.trim(), message = "Hogar vinculado")
        }.onFailure { error -> _state.value = _state.value.copy(message = error.message ?: "No se pudo vincular el hogar") }
    }

    fun synchronizeAll() = viewModelScope.launch {
        if (_state.value.running) return@launch
        val startedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(
            running = true,
            phase = SyncPhase.DISCOVERING,
            startedAt = startedAt,
            finishedAt = null,
            durationMs = null,
            remoteDevice = null,
            sent = 0,
            received = 0,
            acknowledged = 0,
            applied = 0,
            error = null,
                message = context.getString(R.string.sync_phase_discovering),
        )
        try {
            val attempt = syncCoordinator.synchronizeManually { progress -> updateProgress(progress) }
            val message = if (attempt.phase == SyncPhase.NO_CHANGES) {
                context.getString(R.string.sync_no_changes)
            } else {
                context.getString(R.string.sync_success_changes, attempt.result.sent, attempt.result.received)
            }
            _state.value = _state.value.copy(
                running = false,
                phase = attempt.phase,
                finishedAt = attempt.finishedAt,
                durationMs = attempt.durationMs,
                remoteDevice = attempt.remoteDevice,
                sent = attempt.result.sent,
                received = attempt.result.received,
                acknowledged = attempt.result.acknowledged,
                applied = attempt.result.applied,
                message = message,
            )
            persistManualResult(
                result = attempt.phase.name,
                message = message,
                remoteDevice = attempt.remoteDevice,
                sent = attempt.result.sent,
                received = attempt.result.received,
                acknowledged = attempt.result.acknowledged,
                applied = attempt.result.applied,
                durationMs = attempt.durationMs,
                error = null,
            )
            _events.emit(message)
        } catch (error: SyncFailureException) {
            val finishedAt = System.currentTimeMillis()
            _state.value = _state.value.copy(
                running = false,
                phase = SyncPhase.FAILED,
                finishedAt = finishedAt,
                durationMs = finishedAt - startedAt,
                error = error.error,
                message = error.messageForUser(),
            )
            persistManualResult(
                result = SyncPhase.FAILED.name,
                message = error.messageForUser(),
                remoteDevice = _state.value.remoteDevice,
                sent = _state.value.sent,
                received = _state.value.received,
                acknowledged = _state.value.acknowledged,
                applied = _state.value.applied,
                durationMs = finishedAt - startedAt,
                error = errorName(error.error),
            )
            _events.emit(error.messageForUser())
        } catch (error: kotlinx.coroutines.CancellationException) {
            val message = context.getString(R.string.sync_cancelled_result)
            _state.value = _state.value.copy(running = false, phase = SyncPhase.IDLE, message = message)
            _events.emit(message)
        } catch (error: Exception) {
            val finishedAt = System.currentTimeMillis()
            _state.value = _state.value.copy(
                running = false,
                phase = SyncPhase.FAILED,
                finishedAt = finishedAt,
                durationMs = finishedAt - startedAt,
                error = SyncError.Unknown,
                message = context.getString(R.string.sync_unknown_error),
            )
            val message = context.getString(R.string.sync_unknown_error)
            persistManualResult(
                result = SyncPhase.FAILED.name,
                message = message,
                remoteDevice = _state.value.remoteDevice,
                sent = _state.value.sent,
                received = _state.value.received,
                acknowledged = _state.value.acknowledged,
                applied = _state.value.applied,
                durationMs = finishedAt - startedAt,
                error = errorName(SyncError.Unknown),
            )
            _events.emit(message)
        }
    }

    private suspend fun persistManualResult(
        result: String,
        message: String,
        remoteDevice: String?,
        sent: Int,
        received: Int,
        acknowledged: Int,
        applied: Int,
        durationMs: Long,
        error: String?,
    ) {
        settingsRepository.update {
            it.copy(
                lastManualResult = result,
                lastManualMessage = message,
                lastManualRemoteDevice = remoteDevice,
                lastManualSent = sent,
                lastManualReceived = received,
                lastManualAcknowledged = acknowledged,
                lastManualApplied = applied,
                lastManualDurationMs = durationMs,
                lastManualError = error,
            )
        }
    }

    private fun errorFromName(value: String): SyncError? = when (value) {
        "NetworkUnavailable" -> SyncError.NetworkUnavailable
        "DiscoveryFailed" -> SyncError.DiscoveryFailed
        "DeviceNotFound" -> SyncError.DeviceNotFound
        "ConnectionTimeout" -> SyncError.ConnectionTimeout
        "ReadTimeout" -> SyncError.ReadTimeout
        "UnauthorizedDevice" -> SyncError.UnauthorizedDevice
        "InvalidCredentials" -> SyncError.InvalidCredentials
        "HouseholdMismatch" -> SyncError.HouseholdMismatch
        "ProtocolError" -> SyncError.ProtocolError
        "TransportInterrupted" -> SyncError.TransportInterrupted
        "DatabaseApplyError" -> SyncError.DatabaseApplyError
        "AlreadyRunning" -> SyncError.AlreadyRunning
        "Cancelled" -> SyncError.Cancelled
        "Unknown" -> SyncError.Unknown
        else -> null
    }

    private fun errorName(error: SyncError): String = when (error) {
        SyncError.NetworkUnavailable -> "NetworkUnavailable"
        SyncError.DiscoveryFailed -> "DiscoveryFailed"
        SyncError.DeviceNotFound -> "DeviceNotFound"
        SyncError.ConnectionTimeout -> "ConnectionTimeout"
        SyncError.ReadTimeout -> "ReadTimeout"
        SyncError.UnauthorizedDevice -> "UnauthorizedDevice"
        SyncError.InvalidCredentials -> "InvalidCredentials"
        SyncError.HouseholdMismatch -> "HouseholdMismatch"
        SyncError.ProtocolError -> "ProtocolError"
        SyncError.TransportInterrupted -> "TransportInterrupted"
        SyncError.DatabaseApplyError -> "DatabaseApplyError"
        SyncError.AlreadyRunning -> "AlreadyRunning"
        SyncError.Cancelled -> "Cancelled"
        SyncError.Unknown -> "Unknown"
    }

    private fun updateProgress(progress: SyncProgress) {
        _state.value = _state.value.copy(
            phase = progress.phase,
            remoteDevice = progress.remoteDevice ?: _state.value.remoteDevice,
            sent = progress.sent,
            received = progress.received,
            acknowledged = progress.acknowledged,
            message = progress.phase.userMessage(),
        )
    }

    private fun SyncPhase.userMessage(): String = when (this) {
        SyncPhase.DISCOVERING -> context.getString(R.string.sync_phase_discovering)
        SyncPhase.CONNECTING -> context.getString(R.string.sync_phase_connecting)
        SyncPhase.AUTHENTICATING -> context.getString(R.string.sync_phase_authenticating)
        SyncPhase.SYNCHRONIZING -> context.getString(R.string.sync_phase_synchronizing)
        SyncPhase.SUCCESS, SyncPhase.NO_CHANGES -> context.getString(R.string.sync_phase_completed)
        SyncPhase.FAILED -> context.getString(R.string.sync_unknown_error)
        SyncPhase.IDLE -> context.getString(R.string.sync_phase_idle)
    }

    private fun SyncFailureException.messageForUser(): String = when (error) {
        SyncError.NetworkUnavailable -> context.getString(R.string.sync_no_network)
        SyncError.DeviceNotFound -> context.getString(R.string.sync_device_not_found)
        SyncError.DiscoveryFailed -> context.getString(R.string.sync_discovery_failed)
        SyncError.ConnectionTimeout -> context.getString(R.string.sync_connection_timeout)
        SyncError.ReadTimeout -> context.getString(R.string.sync_read_timeout)
        SyncError.UnauthorizedDevice -> context.getString(R.string.sync_unauthorized)
        SyncError.InvalidCredentials -> context.getString(R.string.sync_invalid_credentials)
        SyncError.HouseholdMismatch -> context.getString(R.string.sync_household_mismatch)
        SyncError.ProtocolError -> context.getString(R.string.sync_protocol_error)
        SyncError.TransportInterrupted -> context.getString(R.string.sync_transport_interrupted)
        SyncError.DatabaseApplyError -> context.getString(R.string.sync_database_error)
        SyncError.AlreadyRunning -> context.getString(R.string.sync_already_running)
        SyncError.Cancelled -> context.getString(R.string.sync_cancelled)
        SyncError.Unknown -> context.getString(R.string.sync_unknown_error)
    }

    private fun SyncResult.message(): String = "Enviadas: $sent | Confirmadas: $acknowledged | Recibidas: $received | Aplicadas: $applied"

    companion object {
        const val DEFAULT_PORT = 45678
    }
}
