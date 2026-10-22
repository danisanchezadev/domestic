package com.majyd.casa.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.repository.CalendarRepository
import com.majyd.casa.data.repository.ShoppingRepository
import com.majyd.casa.data.repository.TaskRepository
import com.majyd.casa.data.repository.WeeklyMenuRepository
import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class HomeSummaryState(
    val pendingShopping: Int = 0,
    val pendingTasks: Int = 0,
    val upcomingEvents: Int = 0,
    val todayMenu: String? = null,
    val lastSyncAt: Long? = null,
    val people: List<String> = emptyList(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    shoppingRepository: ShoppingRepository,
    taskRepository: TaskRepository,
    calendarRepository: CalendarRepository,
    weeklyMenuRepository: WeeklyMenuRepository,
    settingsRepository: SettingsRepository,
    householdRepository: HouseholdRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {
    private val pendingShopping = shoppingRepository.observeLists()
        .map { lists -> lists.filterNot { it.deleted } }
        .flatMapLatest { lists ->
            if (lists.isEmpty()) flowOf(0)
            else combine(lists.map { shoppingRepository.observeProducts(it.id) }) { products ->
                products.sumOf { list -> list.count { product -> !product.deleted && !product.purchased } }
            }
        }

    private val pendingTasks = taskRepository.observeTasks()
        .map { tasks -> tasks.count { !it.deleted && !it.completed } }

    private val upcomingEvents = calendarRepository.observeEvents()
        .map { events ->
            val now = System.currentTimeMillis()
            events.count { !it.deleted && it.startAt >= now }
        }

    private val todayMenu = weeklyMenuRepository.observeItems()
        .map { items ->
            val today = LocalDate.now().dayOfWeek.value
            items.firstOrNull { !it.deleted && it.dayOfWeek == today }?.title
        }

    private val lastSyncAt = settingsRepository.preferences.map { it.lastSyncAt }
    private val people = householdRepository.observeState().map { state -> state.people.map { it.name } }

    val state: StateFlow<HomeSummaryState> = combine(
        listOf(pendingShopping, pendingTasks, upcomingEvents, todayMenu, lastSyncAt, people),
    ) { values ->
        HomeSummaryState(
            pendingShopping = values[0] as Int,
            pendingTasks = values[1] as Int,
            upcomingEvents = values[2] as Int,
            todayMenu = values[3] as String?,
            lastSyncAt = values[4] as Long?,
            people = values[5] as List<String>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSummaryState())

    fun synchronizeNow() = syncCoordinator.syncNow(viewModelScope)
}
