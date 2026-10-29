package com.majyd.casa.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majyd.casa.data.model.CalendarEventItem
import com.majyd.casa.data.model.CalendarEventException
import com.majyd.casa.data.model.CalendarOccurrence
import com.majyd.casa.data.model.CalendarPlace
import com.majyd.casa.data.model.CalendarRecurrenceExpander
import com.majyd.casa.data.model.CalendarRecurrenceType
import com.majyd.casa.data.model.HouseholdState
import com.majyd.casa.data.repository.CalendarRepository
import com.majyd.casa.data.repository.HouseholdRepository
import com.majyd.casa.util.Normalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val calendarDateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm")

data class CalendarDraft(
    val eventId: String? = null,
    val title: String = "",
    val notes: String = "",
    val location: String = "",
    val startAt: String = defaultCalendarDateTime(),
    val endAt: String = defaultCalendarDateTime(),
    val allDay: Boolean = false,
    val reminderDaysBefore: String = "",
    val participantIds: Set<String> = emptySet(),
    val recurrenceType: CalendarRecurrenceType = CalendarRecurrenceType.NONE,
    val recurrenceInterval: String = "1",
    val recurrenceWeekdays: Set<Int> = emptySet(),
    val recurrenceUntil: String = "",
    val recurrenceCustomUnit: String = "DAYS",
    val occurrenceDate: LocalDate? = null,
)

private fun defaultCalendarDateTime(): String = LocalDateTime.now().withSecond(0).withNano(0).format(calendarDateTimeFormatter)

data class CalendarUiState(
    val activeEvents: List<CalendarEventItem> = emptyList(),
    val deletedEvents: List<CalendarEventItem> = emptyList(),
    val occurrences: List<CalendarOccurrence> = emptyList(),
    val nextOccurrence: CalendarOccurrence? = null,
    val nextOccurrences: List<CalendarOccurrence> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val displayedMonth: YearMonth = YearMonth.now(),
    val selectedParticipantIds: Set<String> = emptySet(),
    val household: HouseholdState = HouseholdState(),
    val draft: CalendarDraft = CalendarDraft(),
    val places: List<CalendarPlace> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val householdRepository: HouseholdRepository,
) : ViewModel() {
    private val draft = MutableStateFlow(CalendarDraft())
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val displayedMonth = MutableStateFlow(YearMonth.now())
    private val participantFilter = MutableStateFlow<Set<String>>(emptySet())
    private val error = MutableStateFlow<String?>(null)
    private val events = calendarRepository.observeEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val places = calendarRepository.observePlaces().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val exceptions = calendarRepository.observeExceptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val household = householdRepository.observeState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseholdState())

    val uiState: StateFlow<CalendarUiState> = combine(events, draft, places, exceptions, household, selectedDate, displayedMonth, participantFilter, error) { values ->
        val items = values[0] as List<CalendarEventItem>
        val draftValue = values[1] as CalendarDraft
        val placeItems = values[2] as List<CalendarPlace>
        val exceptionItems = values[3] as List<com.majyd.casa.data.model.CalendarEventException>
        val householdValue = values[4] as HouseholdState
        val day = values[5] as LocalDate
        val month = values[6] as YearMonth
        val filter = values[7] as Set<String>
        val errorMessage = values[8] as String?
        val visible = items.filterNot { it.deleted }.filter { filter.isEmpty() || it.participantIds.any(filter::contains) }
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val nextItems = CalendarRecurrenceExpander
            .expand(visible, exceptionItems, today, today.plusYears(1))
            .filter { it.startAt >= now }
            .sortedBy { it.startAt }
            .take(2)
        CalendarUiState(
            activeEvents = visible,
            deletedEvents = items.filter { it.deleted },
            occurrences = CalendarRecurrenceExpander.expand(visible, exceptionItems, month.atDay(1), month.atEndOfMonth()),
            nextOccurrence = nextItems.firstOrNull(),
            nextOccurrences = nextItems,
            selectedDate = day,
            displayedMonth = month,
            selectedParticipantIds = filter,
            household = householdValue,
            draft = draftValue,
            places = placeItems,
            error = errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    init { viewModelScope.launch { calendarRepository.ensureDefaults(); householdRepository.ensureDefaults() } }

    fun selectDate(date: LocalDate) { selectedDate.value = date; displayedMonth.value = YearMonth.from(date) }
    fun previousMonth() { displayedMonth.update { it.minusMonths(1) } }
    fun nextMonth() { displayedMonth.update { it.plusMonths(1) } }
    fun today() { selectDate(LocalDate.now()) }
    fun toggleParticipantFilter(id: String) { participantFilter.update { if (id in it) it - id else it + id } }
    fun clearParticipantFilter() { participantFilter.value = emptySet() }

    fun editEvent(event: CalendarEventItem) {
        draft.value = CalendarDraft(eventId = event.id, title = event.title, notes = event.notes, location = event.location, startAt = formatEpoch(event.startAt), endAt = formatEpoch(event.endAt), allDay = event.allDay, reminderDaysBefore = event.reminderDaysBefore?.toString().orEmpty(), participantIds = event.participantIds, recurrenceType = event.recurrenceType, recurrenceInterval = event.recurrenceInterval.toString(), recurrenceWeekdays = event.recurrenceWeekdays, recurrenceUntil = event.recurrenceUntil?.let(::formatDate).orEmpty(), recurrenceCustomUnit = event.recurrenceCustomUnit)
    }
    fun editOccurrence(occurrence: CalendarOccurrence) {
        editEvent(occurrence.source)
        draft.update { it.copy(startAt = formatEpoch(occurrence.startAt), endAt = formatEpoch(occurrence.endAt), occurrenceDate = occurrence.occurrenceDate) }
    }
    fun newEvent() { draft.value = CalendarDraft(startAt = dateTimeFor(selectedDate.value, LocalTime.of(23, 59)), endAt = dateTimeFor(selectedDate.value, LocalTime.of(23, 59))) }
    fun newEventFor(date: LocalDate) {
        if (date.isBefore(LocalDate.now())) return
        selectedDate.value = date
        displayedMonth.value = YearMonth.from(date)
        newEvent()
    }
    fun clearDraft() { draft.value = CalendarDraft(); error.value = null }
    fun clearError() { error.value = null }
    fun updateTitle(value: String) = draft.update { it.copy(title = value) }
    fun updateNotes(value: String) = draft.update { it.copy(notes = value) }
    fun updateLocation(value: String) = draft.update { it.copy(location = value) }
    fun updateStartAt(value: String) = draft.update { current -> val start = parseEpoch(value); val end = parseEpoch(current.endAt); if (start != null && (end == null || end < start)) current.copy(startAt = value, endAt = value) else current.copy(startAt = value) }
    fun updateEndAt(value: String) = draft.update { current -> val start = parseEpoch(current.startAt); val end = parseEpoch(value); if (start != null && (end == null || end < start)) current.copy(endAt = current.startAt) else current.copy(endAt = value) }
    fun updateAllDay(value: Boolean) = draft.update { it.copy(allDay = value) }
    fun updateReminder(value: String) = draft.update { it.copy(reminderDaysBefore = value) }
    fun updateParticipants(ids: Set<String>) = draft.update { it.copy(participantIds = ids) }
    fun updateRecurrence(type: CalendarRecurrenceType) = draft.update { it.copy(recurrenceType = type) }
    fun updateRecurrenceInterval(value: String) = draft.update { it.copy(recurrenceInterval = value.filter(Char::isDigit).take(3)) }
    fun toggleRecurrenceWeekday(day: Int) = draft.update { it.copy(recurrenceWeekdays = if (day in it.recurrenceWeekdays) it.recurrenceWeekdays - day else it.recurrenceWeekdays + day) }
    fun updateRecurrenceUntil(value: String) = draft.update { it.copy(recurrenceUntil = value) }
    fun updateRecurrenceCustomUnit(value: String) = draft.update { it.copy(recurrenceCustomUnit = value) }

    fun saveEvent(onSaved: () -> Unit = {}) = viewModelScope.launch {
        val current = draft.value
        if (Normalizer.normalizeText(current.title).isBlank()) { error.value = "El titulo es obligatorio."; return@launch }
        val start = parseEpoch(current.startAt) ?: run { error.value = "La fecha y hora de inicio no son validas."; return@launch }
        val endValue = parseEpoch(current.endAt)
        val end = endValue ?: start
        val startDate = java.time.Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
        if (current.eventId == null && startDate.isBefore(LocalDate.now())) {
            error.value = "No puedes crear eventos en fechas pasadas."
            return@launch
        }
        if (end < start) {
            error.value = "La fecha y hora final no puede ser anterior al inicio."
            return@launch
        }
        if (current.occurrenceDate != null && current.eventId != null) {
            calendarRepository.saveException(CalendarEventException(current.eventId, current.occurrenceDate, current.title.trim(), current.notes.trim(), current.location.trim(), start, end, current.allDay, current.participantIds, deleted = false))
            draft.value = CalendarDraft()
            onSaved()
            return@launch
        }
        val untilDate = current.recurrenceUntil.trim().takeIf { it.isNotBlank() }?.let(::parseDate)
        if (current.recurrenceUntil.isNotBlank() && untilDate == null) { error.value = "La fecha final debe usar dd-MM-aaaa."; return@launch }
        if (untilDate != null && untilDate.isBefore(java.time.Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate())) { error.value = "La fecha final no puede ser anterior al inicio."; return@launch }
        val until = untilDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        calendarRepository.upsertEvent(current.eventId, current.title, current.notes, current.location, start, end, current.allDay, current.reminderDaysBefore.toIntOrNull()?.takeIf { it >= 0 }, current.participantIds, current.recurrenceType.name, current.recurrenceInterval.toIntOrNull()?.coerceAtLeast(1) ?: 1, current.recurrenceWeekdays, until, current.recurrenceCustomUnit)
        if (current.location.isNotBlank()) calendarRepository.savePlace(null, current.location)
        draft.value = CalendarDraft()
        error.value = null
        onSaved()
    }
    fun deleteEvent(event: CalendarEventItem) = viewModelScope.launch { calendarRepository.deleteEvent(event.id) }
    fun cancelOccurrence(occurrence: CalendarOccurrence) = viewModelScope.launch {
        calendarRepository.saveException(CalendarEventException(occurrence.source.id, occurrence.occurrenceDate, occurrence.source.title, occurrence.source.notes, occurrence.source.location, occurrence.startAt, occurrence.endAt, occurrence.source.allDay, occurrence.source.participantIds, deleted = true))
    }
    fun deleteEventPermanently(event: CalendarEventItem) = viewModelScope.launch { calendarRepository.deleteEventPermanently(event.id) }
    fun restoreEvent(event: CalendarEventItem) = viewModelScope.launch { calendarRepository.restoreEvent(event.id) }
    fun archivePast() = viewModelScope.launch { calendarRepository.archivePast() }
    fun savePlace(placeId: String?, name: String) = viewModelScope.launch { calendarRepository.savePlace(placeId, name) }
    fun deletePlace(place: CalendarPlace) = viewModelScope.launch { calendarRepository.deletePlace(place.id) }

    private fun formatEpoch(value: Long): String = calendarDateTimeFormatter.format(java.time.Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
    private fun formatDate(value: Long): String = DateTimeFormatter.ofPattern("dd-MM-uuuu").format(java.time.Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
    private fun parseEpoch(value: String): Long? = runCatching { LocalDateTime.parse(value.trim(), calendarDateTimeFormatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern("dd-MM-uuuu")) }.getOrNull()
    private fun dateTimeFor(date: LocalDate, time: LocalTime): String = LocalDateTime.of(date, time).format(calendarDateTimeFormatter)
}
