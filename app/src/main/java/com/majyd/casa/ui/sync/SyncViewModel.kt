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
import com.majyd.casa.sync.transport.WifiNetworkPolicy
import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.data.model.Person
import com.majyd.casa.data.local.dao.AuthorizedDeviceDao
import com.majyd.casa.data.local.entities.AuthorizedDeviceEntity
import com.majyd.casa.sync.DevicePersonSyncPayload
import com.majyd.casa.sync.HouseholdSyncPayloadCodec
import com.majyd.casa.sync.SyncOperationRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class SyncUiState(
    val devices: List<NearbyDevice> = emptyList(),
    val selectedDevice: NearbyDevice? = null,
    val pairingCode: String = "",
    val joinCode: String = "",
    val people: List<Person> = emptyList(),
    val assignedPersonId: String? = null,
    val showProfilePrompt: Boolean = false,
    val running: Boolean = false,
    val message: String? = null,
    val authorizedDevices: List<AuthorizedDeviceEntity> = emptyList(),
    val backupContent: String? = null,
    val backupExported: Boolean = false,
    val localDeviceId: String? = null,
    val localDisplayName: String = "",
    val domesticWifiSsid: String = "",
    val currentWifiSsid: String? = null,
    val wifiConnected: Boolean = false,
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
    private val wifiNetworkPolicy: WifiNetworkPolicy,
    private val householdRepository: HouseholdRepository,
    private val syncOperationRepository: SyncOperationRepository,
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
                localDisplayName = device.displayName,
                domesticWifiSsid = settingsRepository.preferences.first().domesticWifiSsid,
                currentWifiSsid = wifiNetworkPolicy.currentSsid(),
                wifiConnected = wifiNetworkPolicy.isConnectedToWifi(),
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
                    domesticWifiSsid = preferences.domesticWifiSsid,
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
            householdRepository.observeState().collect { household ->
                _state.value = _state.value.copy(
                    people = household.people,
                    assignedPersonId = household.assignedPersonId,
                )
            }
        }
        viewModelScope.launch {
            syncCoordinator.nearbyDevices.collect { devices ->
                _state.value = _state.value.copy(
                    devices = devices,
                    selectedDevice = _state.value.selectedDevice?.let { selected -> devices.firstOrNull { it.deviceId == selected.deviceId } } ?: _state.value.selectedDevice,
                )
            }
        }
    }

    fun select(device: NearbyDevice) {
        _state.value = _state.value.copy(selectedDevice = device, message = null)
    }

    fun updateJoinCode(code: String) {
        if (code.length <= 6 && code.all(Char::isDigit)) _state.value = _state.value.copy(joinCode = code)
    }

    fun updateDomesticWifiSsid(value: String) = viewModelScope.launch {
        settingsRepository.update { it.copy(domesticWifiSsid = value.trim()) }
        _state.value = _state.value.copy(domesticWifiSsid = value.trim(), message = "Red doméstica guardada")
    }

    fun refreshWifiState() {
        _state.value = _state.value.copy(
            currentWifiSsid = wifiNetworkPolicy.currentSsid(),
            wifiConnected = wifiNetworkPolicy.isConnectedToWifi(),
        )
    }

    fun renameDevice(value: String) = viewModelScope.launch {
        val name = value.trim()
        if (name.isBlank()) {
            _state.value = _state.value.copy(message = "Escribe un nombre para este móvil")
            return@launch
        }
        val device = deviceManager.renameDevice(name)
        _state.value = _state.value.copy(localDisplayName = device.displayName, message = "Nombre guardado")
    }

    fun pairSelected() = viewModelScope.launch {
        val peer = _state.value.selectedDevice ?: _state.value.devices.firstOrNull()
        if (peer == null) {
            _state.value = _state.value.copy(message = "No se encuentra el otro móvil en esta Wi-Fi")
            return@launch
        }
        if (_state.value.joinCode.length != 6) {
            _state.value = _state.value.copy(message = "Introduce un código de seis cifras")
            return@launch
        }
        _state.value = _state.value.copy(running = true, phase = SyncPhase.AUTHENTICATING, message = "Verificando código y vinculando móviles")
        runCatching {
            syncCoordinator.pairDevice(peer, _state.value.joinCode) { progress -> updateProgress(progress) }
        }.onSuccess {
            val current = householdRepository.observeState().first()
            _state.value = _state.value.copy(
                running = false,
                phase = SyncPhase.SUCCESS,
                finishedAt = System.currentTimeMillis(),
                showProfilePrompt = current.assignedPersonId == null,
                message = if (current.assignedPersonId == null) "Vinculación completada. Elige la persona que usará este móvil." else "Vinculación completada",
            )
            _events.emit(_state.value.message.orEmpty())
        }.onFailure { error ->
            _state.value = _state.value.copy(running = false, phase = SyncPhase.FAILED, message = error.message ?: "No se pudo vincular el móvil")
            _events.emit(_state.value.message.orEmpty())
        }
    }

    fun showMessage(value: String) { _state.value = _state.value.copy(message = value) }

    fun selectProfile(personId: String) = viewModelScope.launch {
        associateCurrentDevice(personId)
        _state.value = _state.value.copy(showProfilePrompt = false, message = "Persona asociada")
    }

    fun createProfile(name: String) = viewModelScope.launch {
        val person = householdRepository.addPerson(name)
        if (person == null) {
            _state.value = _state.value.copy(message = "Escribe un nombre para crear la persona")
            return@launch
        }
        associateCurrentDevice(person.id)
        _state.value = _state.value.copy(showProfilePrompt = false, message = "Persona creada y asociada")
    }

    fun dismissProfilePrompt() {
        _state.value = _state.value.copy(showProfilePrompt = false, message = "Puedes asociar este móvil desde Ajustes > Hogar")
    }

    private suspend fun associateCurrentDevice(personId: String?) {
        val updated = deviceManager.assignPerson(personId)
        val now = updated.updatedAt
        val existing = authorizedDeviceDao.get(updated.deviceId)
        authorizedDeviceDao.upsert(
            existing?.copy(
                assignedPersonId = personId,
                associationUpdatedAt = now,
                lastSeenAt = maxOf(existing.lastSeenAt, now),
                revoked = false,
            ) ?: AuthorizedDeviceEntity(
                deviceId = updated.deviceId,
                householdId = updated.householdId,
                displayName = updated.displayName,
                lastSeenAt = now,
                assignedPersonId = personId,
                associationUpdatedAt = now,
            ),
        )
        syncOperationRepository.enqueue(
            householdId = updated.householdId,
            deviceId = updated.deviceId,
            entityType = "device_person",
            entityId = updated.deviceId,
            payload = HouseholdSyncPayloadCodec.encode(DevicePersonSyncPayload(updated.deviceId, updated.householdId, personId, now)),
            version = now,
            updatedAt = now,
            deleted = personId == null,
        )
    }

    fun savePairingCode() = viewModelScope.launch {
        runCatching { settingsRepository.setPairingCode(_state.value.pairingCode) }
            .onSuccess {
                pairingCode = _state.value.pairingCode
                val device = deviceManager.ensureDevice()
                _state.value = _state.value.copy(message = "Código guardado")
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
        _state.value = _state.value.copy(pairingCode = code, message = "Código regenerado")
    }

    fun revokeDevice(deviceId: String) = viewModelScope.launch {
        unlinkDevice(deviceId)
    }

    fun unlinkDevice(deviceId: String) = viewModelScope.launch {
        authorizedDeviceDao.revoke(deviceId)
        _state.value = _state.value.copy(message = "Dispositivo desvinculado", backupExported = false)
        _events.emit("Dispositivo desvinculado")
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
            _state.value = _state.value.copy(pairingCode = pairingCode, message = "Backup restaurado")
        }.onFailure { error -> _state.value = _state.value.copy(message = error.message ?: "No se pudo restaurar el backup") }
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
            val attempt = syncCoordinator.synchronizeManually(onProgress = { progress -> updateProgress(progress) })
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
