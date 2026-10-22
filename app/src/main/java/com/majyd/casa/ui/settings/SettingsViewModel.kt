package com.majyd.casa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.AppPreferences
import com.majyd.casa.data.model.DashboardCard
import com.majyd.casa.data.model.ColorTheme
import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.data.model.HouseholdState
import com.majyd.casa.sync.DeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import com.majyd.casa.data.model.PersonDeletionResult
import com.majyd.casa.notifications.NotificationScheduler
import com.majyd.casa.notifications.TaskNotificationPolicy
import com.majyd.casa.data.repository.TaskRepository
import com.majyd.casa.sync.SyncChangeNotifier

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val householdRepository: HouseholdRepository,
    private val deviceManager: DeviceManager,
    private val taskRepository: TaskRepository,
    private val notificationScheduler: NotificationScheduler,
    private val syncChangeNotifier: SyncChangeNotifier,
) : ViewModel() {
    private val _personActionMessage = MutableStateFlow<String?>(null)
    val personActionMessage: StateFlow<String?> = _personActionMessage
    private val _pendingPersonDeletion = MutableStateFlow<String?>(null)
    val pendingPersonDeletion: StateFlow<String?> = _pendingPersonDeletion

    val preferences: StateFlow<AppPreferences> = settingsRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferences(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.update { current ->
                current.copy(dashboardCards = AppPreferences.normalizeDashboardCards(current.dashboardCards))
            }
        }
    }

    val household: StateFlow<HouseholdState> = householdRepository.observeState().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HouseholdState(),
    )

    val device = deviceManager.observeDevice().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun updateHomeName(value: String) = update { it.copy(homeName = value.take(MAX_HOME_NAME_LENGTH)) }
    fun updatePersonOne(value: String) = update { it.copy(personOneName = value) }
    fun updatePersonTwo(value: String) = update { it.copy(personTwoName = value) }
    fun updateDomesticWifiSsid(value: String) = update { it.copy(domesticWifiSsid = value) }
    fun updateDarkMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(darkMode = enabled) }
        syncChangeNotifier.request()
    }
    fun updateColorTheme(theme: ColorTheme) = update { it.copy(colorTheme = theme) }

    fun addPerson(name: String) = viewModelScope.launch { householdRepository.addPerson(name) }
    fun renamePerson(personId: String, name: String) = viewModelScope.launch { householdRepository.renamePerson(personId, name) }
    fun deletePerson(personId: String) = viewModelScope.launch {
        val result = householdRepository.deletePerson(personId)
        _personActionMessage.value = if (result.deleted) {
            "Persona eliminada"
        } else {
            _pendingPersonDeletion.value = personId
            "Tiene ${result.activeTaskCount} tarea(s) activa(s) asignada(s)"
        }
    }
    fun resolvePersonTasks(replacementPersonId: String?) = viewModelScope.launch {
        val personId = _pendingPersonDeletion.value ?: return@launch
        val result = householdRepository.resolvePersonTasks(personId, replacementPersonId)
        _pendingPersonDeletion.value = null
        _personActionMessage.value = if (result.deleted) "Persona eliminada y tareas actualizadas" else "No se pudo eliminar la persona"
    }
    fun cancelPersonDeletion() { _pendingPersonDeletion.value = null }
    fun assignDevice(personId: String?) = viewModelScope.launch {
        deviceManager.assignPerson(personId)
        val currentHousehold = householdRepository.observeState().first()
        taskRepository.observeTasks().first().forEach { task ->
            if (task.deleted || task.completed || !TaskNotificationPolicy.isRelevant(task, currentHousehold)) {
                notificationScheduler.cancel(task.id)
            } else {
                notificationScheduler.schedule(task)
            }
        }
    }
    fun addGroup(name: String, memberIds: Set<String> = emptySet()) = viewModelScope.launch { householdRepository.addGroup(name.take(MAX_GROUP_NAME_LENGTH), memberIds) }
    fun renameGroup(groupId: String, name: String) = viewModelScope.launch { householdRepository.renameGroup(groupId, name.take(MAX_GROUP_NAME_LENGTH)) }
    fun setGroupMembers(groupId: String, memberIds: Set<String>) = viewModelScope.launch { householdRepository.setGroupMembers(groupId, memberIds) }
    fun deleteGroup(groupId: String) = viewModelScope.launch { householdRepository.deleteGroup(groupId) }

    fun moveDashboardCardUp(card: DashboardCard) = update {
        it.copy(dashboardCards = moveCard(AppPreferences.normalizeDashboardCards(it.dashboardCards), card, -1))
    }

    fun moveDashboardCardDown(card: DashboardCard) = update {
        it.copy(dashboardCards = moveCard(AppPreferences.normalizeDashboardCards(it.dashboardCards), card, +1))
    }

    fun toggleDashboardCard(card: DashboardCard) = update {
        if (card == DashboardCard.SYNC || card == DashboardCard.SETTINGS) return@update it.copy(
            dashboardCards = AppPreferences.normalizeDashboardCards(it.dashboardCards),
        )
        val cards = if (it.dashboardCards.contains(card)) {
            it.dashboardCards.filterNot { existing -> existing == card }
        } else {
            it.dashboardCards + card
        }
        it.copy(dashboardCards = AppPreferences.normalizeDashboardCards(cards))
    }

    private fun update(block: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch {
            settingsRepository.update(block)
            syncChangeNotifier.request()
        }
    }

    private fun moveCard(cards: List<DashboardCard>, card: DashboardCard, delta: Int): List<DashboardCard> {
        val index = cards.indexOf(card)
        if (index == -1) return cards
        val newIndex = (index + delta).coerceIn(0, cards.lastIndex)
        if (newIndex == index) return cards
        return cards.toMutableList().apply {
            removeAt(index)
            add(newIndex, card)
        }
    }
}

private const val MAX_GROUP_NAME_LENGTH = 9
private const val MAX_HOME_NAME_LENGTH = 18
