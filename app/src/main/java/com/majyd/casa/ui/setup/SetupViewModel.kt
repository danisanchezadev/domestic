package com.majyd.casa.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.data.model.HouseholdState
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.SyncSession
import com.majyd.casa.sync.transport.NearbyDevice
import com.majyd.casa.sync.transport.NsdDeviceDiscovery
import com.majyd.casa.sync.transport.WifiNetworkPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val householdRepository: HouseholdRepository,
    private val deviceManager: DeviceManager,
    private val discovery: NsdDeviceDiscovery,
    private val wifiNetworkPolicy: WifiNetworkPolicy,
    private val syncSession: SyncSession,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()
    val household: StateFlow<HouseholdState> = householdRepository.observeState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HouseholdState(),
    )
    private val _awaitingProfile = MutableStateFlow(false)
    val awaitingProfile: StateFlow<Boolean> = _awaitingProfile.asStateFlow()

    fun complete(homeName: String, firstPersonName: String, otherPeople: String, domesticWifiSsid: String) =
        viewModelScope.launch {
            val state = householdRepository.ensureDefaults()
            val first = state.people.firstOrNull() ?: return@launch
            householdRepository.renamePerson(first.id, firstPersonName)
            state.people.drop(1).forEach { householdRepository.deletePerson(it.id) }
            otherPeople.split(',').map(String::trim).filter(String::isNotBlank).forEach {
                householdRepository.addPerson(it)
            }
            deviceManager.assignPerson(first.id)
            settingsRepository.update {
                it.copy(
                    homeName = homeName.trim().take(18).ifBlank { "Casa" },
                    domesticWifiSsid = domesticWifiSsid.trim(),
                    setupCompleted = true,
                )
            }
        }

    fun discoverNearby() = viewModelScope.launch {
        if (!wifiNetworkPolicy.isConnectedToWifi()) {
            _message.value = "Conecta este telefono a una red Wi-Fi para buscar hogares cercanos."
            return@launch
        }
        _nearbyDevices.value = emptyList()
        _message.value = "Buscando hogares en la Wi-Fi actual (${wifiNetworkPolicy.currentSsid() ?: "detectada"})..."
        discovery.discover()
            .catch { _message.value = "No se pudieron buscar hogares en esta Wi-Fi." }
            .collect { devices ->
                _nearbyDevices.value = devices
                _message.value = if (devices.isEmpty()) {
                    "No se han encontrado hogares en esta Wi-Fi."
                } else {
                    "Hogar encontrado. Introduce el codigo y pulsa Unirme."
                }
            }
    }

    fun joinNearby(device: NearbyDevice, pairingCode: String) {
        val householdId = device.householdId
        val code = pairingCode.trim()
        when {
            householdId.isNullOrBlank() -> _message.value = "El dispositivo encontrado no publica un hogar valido."
            code.length != 6 || !code.all(Char::isDigit) -> _message.value = "Introduce un codigo numerico de 6 cifras."
            else -> viewModelScope.launch { joinHousehold(householdId, code, device) }
        }
    }

    private suspend fun joinHousehold(householdId: String, code: String, device: NearbyDevice) {
        runCatching {
            settingsRepository.setPairingCode(code)
            deviceManager.joinHousehold(householdId)
            syncSession.synchronize(device.host, device.port, householdId, code)
        }.onSuccess {
            _awaitingProfile.value = true
            _message.value = "Hogar vinculado. Elige un perfil o crea uno nuevo."
        }.onFailure { error ->
            _message.value = "No se pudo vincular el hogar: ${error.message ?: "comprueba el codigo"}."
        }
    }

    fun selectProfile(personId: String) = viewModelScope.launch {
        deviceManager.assignPerson(personId)
        settingsRepository.update { it.copy(setupCompleted = true) }
        _awaitingProfile.value = false
    }

    fun createProfile(name: String) = viewModelScope.launch {
        val person = householdRepository.addPerson(name)
        if (person == null) {
            _message.value = "Escribe un nombre para crear el perfil."
            return@launch
        }
        deviceManager.assignPerson(person.id)
        settingsRepository.update { it.copy(setupCompleted = true) }
        _awaitingProfile.value = false
    }
}
