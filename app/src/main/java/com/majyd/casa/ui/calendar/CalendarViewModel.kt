package com.majyd.casa.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.CalendarEventItem
import com.majyd.casa.data.model.CalendarPlace
import com.majyd.casa.data.repository.CalendarRepository
import com.majyd.casa.util.Normalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarDraft(
    val eventId: String? = null,
    val title: String = "",
    val notes: String = "",
    val location: String = "",
    val startAt: String = defaultCalendarDateTime(),
    val endAt: String = defaultCalendarDateTime(),
    val allDay: Boolean = false,
    val reminderDaysBefore: String = "",
)

private fun defaultCalendarDateTime(): String = java.time.LocalDateTime.now()
    .withSecond(0)
    .withNano(0)
    .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm"))

data class CalendarUiState(
    val activeEvents: List<CalendarEventItem> = emptyList(),
    val deletedEvents: List<CalendarEventItem> = emptyList(),
    val draft: CalendarDraft = CalendarDraft(),
    val places: List<CalendarPlace> = emptyList(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    private val draft = MutableStateFlow(CalendarDraft())
    private val events = calendarRepository.observeEvents().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val places = calendarRepository.observePlaces().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<CalendarUiState> = combine(events, draft, places) { items, draftValue, placeItems ->
        CalendarUiState(
            activeEvents = items.filterNot { it.deleted },
            deletedEvents = items.filter { it.deleted },
            draft = draftValue,
            places = placeItems,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    init {
        viewModelScope.launch {
            calendarRepository.ensureDefaults()
        }
    }

    fun editEvent(event: CalendarEventItem) {
        draft.value = CalendarDraft(
            eventId = event.id,
            title = event.title,
            notes = event.notes,
            location = event.location,
            startAt = formatEpoch(event.startAt),
            endAt = formatEpoch(event.endAt),
            allDay = event.allDay,
            reminderDaysBefore = event.reminderDaysBefore?.toString().orEmpty(),
        )
    }

    fun clearDraft() {
        draft.value = CalendarDraft()
    }

    fun updateTitle(value: String) = draft.update { it.copy(title = value) }
    fun updateNotes(value: String) = draft.update { it.copy(notes = value) }
    fun updateLocation(value: String) = draft.update { it.copy(location = value) }
    fun updateStartAt(value: String) = draft.update { current ->
        val end = parseEpoch(current.endAt)
        val start = parseEpoch(value)
        if (start != null && (end == null || end < start)) {
            current.copy(startAt = value, endAt = value)
        } else {
            current.copy(startAt = value)
        }
    }

    fun updateEndAt(value: String) = draft.update { current ->
        val start = parseEpoch(current.startAt)
        val end = parseEpoch(value)
        if (start != null && (end == null || end < start)) {
            current.copy(endAt = current.startAt)
        } else {
            current.copy(endAt = value)
        }
    }
    fun updateAllDay(value: Boolean) = draft.update { it.copy(allDay = value) }
    fun updateReminder(value: String) = draft.update { it.copy(reminderDaysBefore = value) }

    fun saveEvent() = viewModelScope.launch {
        val current = draft.value
        if (Normalizer.normalizeText(current.title).isBlank()) return@launch
        val startAt = parseEpoch(current.startAt) ?: System.currentTimeMillis()
        val endAt = (parseEpoch(current.endAt) ?: (startAt + 3_600_000L)).coerceAtLeast(startAt)
        val reminder = current.reminderDaysBefore.toIntOrNull()?.takeIf { it >= 0 }
        calendarRepository.upsertEvent(
            eventId = current.eventId,
            title = current.title,
            notes = current.notes,
            location = current.location,
            startAt = startAt,
            endAt = endAt,
            allDay = current.allDay,
            reminderDaysBefore = reminder,
        )
        if (current.location.isNotBlank()) {
            calendarRepository.savePlace(null, current.location)
        }
        draft.value = CalendarDraft()
    }

    fun deleteEvent(event: CalendarEventItem) = viewModelScope.launch {
        calendarRepository.deleteEvent(event.id)
    }

    fun deleteEventPermanently(event: CalendarEventItem) = viewModelScope.launch {
        calendarRepository.deleteEventPermanently(event.id)
    }

    fun restoreEvent(event: CalendarEventItem) = viewModelScope.launch {
        calendarRepository.restoreEvent(event.id)
    }

    fun archivePast() = viewModelScope.launch {
        calendarRepository.archivePast()
    }

    fun savePlace(placeId: String?, name: String) = viewModelScope.launch { calendarRepository.savePlace(placeId, name) }
    fun deletePlace(place: CalendarPlace) = viewModelScope.launch { calendarRepository.deletePlace(place.id) }
    fun deletePlacePermanently(place: CalendarPlace) = viewModelScope.launch { calendarRepository.deletePlacePermanently(place.id) }

    private fun formatEpoch(value: Long): String {
        return java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(value))
    }

    private fun parseEpoch(value: String): Long? {
        return runCatching {
            java.time.LocalDateTime.parse(
                value.trim(),
                java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm")
            ).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
