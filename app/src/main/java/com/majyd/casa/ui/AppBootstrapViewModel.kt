package com.majyd.casa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.repository.CalendarRepository
import com.majyd.casa.data.repository.HomeProfileRepository
import com.majyd.casa.data.repository.ShoppingRepository
import com.majyd.casa.data.repository.TaskRepository
import com.majyd.casa.data.repository.WeeklyMenuRepository
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AppBootstrapViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val homeProfileRepository: HomeProfileRepository,
    private val shoppingRepository: ShoppingRepository,
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val weeklyMenuRepository: WeeklyMenuRepository,
    private val householdRepository: HouseholdRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    init {
        viewModelScope.launch {
            deviceManager.ensureDevice()
            homeProfileRepository.ensureProfile()
            shoppingRepository.ensureDefaults()
            taskRepository.ensureDefaults()
            calendarRepository.ensureDefaults()
            weeklyMenuRepository.ensureDefaults()
            syncCoordinator.start(this@AppBootstrapViewModel.viewModelScope)
        }
    }
}
