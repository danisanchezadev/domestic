package com.majyd.casa.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.CalendarEventItem
import com.majyd.casa.data.model.CalendarPlace
import com.majyd.casa.data.model.CalendarRecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var managePlaces by remember { mutableStateOf(false) }
    var selectedOccurrence by remember { mutableStateOf<com.majyd.casa.data.model.CalendarOccurrence?>(null) }
    var pendingCancel by remember { mutableStateOf<com.majyd.casa.data.model.CalendarOccurrence?>(null) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var draftOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = { Text("Agenda") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CalendarMonthCard(
                    state = state,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    onSelectDate = viewModel::selectDate,
                    onOpenDate = { selectedDay = it },
                    onLongPressDate = { viewModel.newEventFor(it); draftOpen = true },
                )
            }
            item {
                state.nextOccurrence?.let { occurrence ->
                    NextEventCard(occurrence = occurrence, onClick = { selectedOccurrence = occurrence })
                }
            }
        }
    }
    selectedDay?.let { date ->
        CalendarDayDialog(
            date = date,
            occurrences = state.occurrences.filter { it.occurrenceDate == date },
            onOpen = { selectedOccurrence = it; selectedDay = null },
            onDismiss = { selectedDay = null },
        )
    }
    if (draftOpen) {
        CalendarDraftDialog(
            state = state,
            viewModel = viewModel,
            onManagePlaces = { managePlaces = true },
            onSave = { viewModel.saveEvent { draftOpen = false } },
            onDismiss = { draftOpen = false; viewModel.clearDraft() },
        )
    }
    if (managePlaces) {
        PlaceManagerDialog(
            places = state.places,
            onSave = viewModel::savePlace,
            onDelete = viewModel::deletePlace,
            onSelect = { place -> viewModel.updateLocation(place.name); managePlaces = false },
            onDismiss = { managePlaces = false },
        )
    }
    selectedOccurrence?.let { occurrence ->
        CalendarEventDetailDialog(
            occurrence = occurrence,
            participantNames = state.household.people.filter { it.id in occurrence.source.participantIds }.map { it.name },
            onEdit = {
                if (occurrence.source.recurrenceType == CalendarRecurrenceType.NONE) {
                    viewModel.editEvent(occurrence.source)
                } else {
                    viewModel.editOccurrence(occurrence)
                }
                selectedOccurrence = null
                draftOpen = true
            },
            onDelete = { viewModel.deleteEventPermanently(occurrence.source); selectedOccurrence = null },
            onDismiss = { selectedOccurrence = null },
        )
    }
    pendingCancel?.let { occurrence ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("Cancelar aparicion") },
            text = { Text("Esta aparicion no volvera a mostrarse, pero la serie continuara.") },
            dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("No") } },
            confirmButton = { TextButton(onClick = { viewModel.cancelOccurrence(occurrence); pendingCancel = null }) { Text("Cancelar aparicion") } },
        )
    }
}

@Composable
private fun NextEventCard(
    occurrence: com.majyd.casa.data.model.CalendarOccurrence,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Próximo evento", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(occurrence.source.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${occurrence.occurrenceDate.dayOfMonth} de ${spanishMonth(occurrence.occurrenceDate.month.value)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CalendarDayDialog(
    date: LocalDate,
    occurrences: List<com.majyd.casa.data.model.CalendarOccurrence>,
    onOpen: (com.majyd.casa.data.model.CalendarOccurrence) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.dayOfMonth} de ${spanishMonth(date.month.value)}") },
        text = {
            if (occurrences.isEmpty()) {
                Text("Sin eventos")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(occurrences) { occurrence ->
                        Card(onClick = { onOpen(occurrence) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(occurrence.source.title, fontWeight = FontWeight.SemiBold)
                                Text(if (occurrence.source.allDay) "Todo el día" else formatOccurrenceTime(occurrence))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun CalendarDraftDialog(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onManagePlaces: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (state.draft.eventId == null) "Nuevo evento" else "Editar evento", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("Volver") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        CalendarDraftCard(
                            draft = state.draft,
                            onTitleChange = viewModel::updateTitle,
                            onNotesChange = viewModel::updateNotes,
                            onLocationChange = viewModel::updateLocation,
                            onManagePlaces = onManagePlaces,
                            onStartChange = viewModel::updateStartAt,
                            onEndChange = viewModel::updateEndAt,
                            onAllDayChange = viewModel::updateAllDay,
                            onReminderChange = viewModel::updateReminder,
                            onSave = onSave,
                            onClear = onDismiss,
                            people = state.household.people,
                            onParticipantsChange = viewModel::updateParticipants,
                            onRecurrenceChange = viewModel::updateRecurrence,
                            onRecurrenceIntervalChange = viewModel::updateRecurrenceInterval,
                            onToggleWeekday = viewModel::toggleRecurrenceWeekday,
                            onRecurrenceUntilChange = viewModel::updateRecurrenceUntil,
                            onRecurrenceCustomUnitChange = viewModel::updateRecurrenceCustomUnit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEventDetailDialog(
    occurrence: com.majyd.casa.data.model.CalendarOccurrence,
    participantNames: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(occurrence.source.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (occurrence.source.allDay) "Todo el dia" else formatOccurrenceTime(occurrence))
                if (participantNames.isNotEmpty()) Text("Participantes: ${participantNames.joinToString(", ")}")
                if (occurrence.source.location.isNotBlank()) Text("Lugar: ${occurrence.source.location}")
                if (occurrence.source.notes.isNotBlank()) Text(occurrence.source.notes)
                if (occurrence.source.recurrenceType != CalendarRecurrenceType.NONE) Text("Repeticion: ${occurrence.source.recurrenceType.label}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onDelete) { Text("Eliminar") }
            }
        },
    )
}

@Composable
private fun CalendarMonthCard(
    state: CalendarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    onLongPressDate: (LocalDate) -> Unit,
) {
    val first = state.displayedMonth.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPrevious) { Text("‹") }
                Text("${spanishMonth(state.displayedMonth.month.value)} ${state.displayedMonth.year}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNext) { Text("›") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
            }
            repeat(6) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val date = gridStart.plusDays((row * 7L) + column)
                        val count = state.occurrences.count { it.occurrenceDate == date }
                        val selected = date == state.selectedDate
                        val inMonth = date.month == state.displayedMonth.month
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .background(
                                    when {
                                        count > 0 -> MaterialTheme.colorScheme.errorContainer
                                        selected -> MaterialTheme.colorScheme.primaryContainer
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                )
                                .combinedClickable(
                                    onClick = { onSelectDate(date); onOpenDate(date) },
                                    onLongClick = { onLongPressDate(date) },
                                )
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(date.dayOfMonth.toString(), color = if (count > 0) MaterialTheme.colorScheme.onErrorContainer else if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                            Text(if (count == 0) " " else "$count", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun spanishMonth(month: Int): String = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
).getOrElse(month - 1) { "" }.replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarParticipantFilters(state: CalendarUiState, onToggle: (String) -> Unit, onClear: () -> Unit) {
    if (state.household.people.isEmpty()) return
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Filtrar por persona", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClear) { Text("Todos") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.household.people.forEach { person ->
                    FilterChip(selected = person.id in state.selectedParticipantIds, onClick = { onToggle(person.id) }, label = { Text(person.name) })
                }
            }
        }
    }
}

@Composable
private fun CalendarDraftCard(
    draft: CalendarDraft,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onManagePlaces: () -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onReminderChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    people: List<com.majyd.casa.data.model.Person>,
    onParticipantsChange: (Set<String>) -> Unit,
    onRecurrenceChange: (CalendarRecurrenceType) -> Unit,
    onRecurrenceIntervalChange: (String) -> Unit,
    onToggleWeekday: (Int) -> Unit,
    onRecurrenceUntilChange: (String) -> Unit,
    onRecurrenceCustomUnitChange: (String) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nuevo evento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Título") },
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notas") },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.location,
                    onValueChange = onLocationChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Lugar") },
                    singleLine = true,
                )
                IconButton(onClick = onManagePlaces) {
                    Icon(Icons.Filled.Place, contentDescription = "Elegir lugar")
                }
            }
            Text("Inicio", style = MaterialTheme.typography.labelLarge)
            CalendarDateWheelPicker(draft.startAt, onStartChange)
            CalendarTimeWheelPicker(draft.startAt, onStartChange)
            Text("Fin", style = MaterialTheme.typography.labelLarge)
            CalendarDateWheelPicker(draft.endAt, onEndChange)
            CalendarTimeWheelPicker(draft.endAt, onEndChange)
            OutlinedTextField(
                value = draft.reminderDaysBefore,
                onValueChange = onReminderChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Recordatorio días antes") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Todo el dia")
                Switch(checked = draft.allDay, onCheckedChange = onAllDayChange)
            }
            Text("Participantes", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                people.forEach { person ->
                    FilterChip(selected = person.id in draft.participantIds, onClick = { onParticipantsChange(if (person.id in draft.participantIds) draft.participantIds - person.id else draft.participantIds + person.id) }, label = { Text(person.name) })
                }
            }
            Text("Repetición", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CalendarRecurrenceType.values().forEach { type ->
                    FilterChip(selected = draft.recurrenceType == type, onClick = { onRecurrenceChange(type) }, label = { Text(type.label) })
                }
            }
            if (draft.recurrenceType != CalendarRecurrenceType.NONE) {
                OutlinedTextField(draft.recurrenceInterval, onRecurrenceIntervalChange, Modifier.fillMaxWidth(), label = { Text("Intervalo") }, singleLine = true)
                if (draft.recurrenceType == CalendarRecurrenceType.CUSTOM) {
                    Text("Unidad del intervalo", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DAYS" to "Días", "WEEKS" to "Semanas", "MONTHS" to "Meses", "YEARS" to "Años").forEach { (unit, label) ->
                            FilterChip(selected = draft.recurrenceCustomUnit == unit, onClick = { onRecurrenceCustomUnitChange(unit) }, label = { Text(label) })
                        }
                    }
                }
                if (draft.recurrenceType == CalendarRecurrenceType.WEEKLY) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D").forEach { (day, label) ->
                            FilterChip(selected = day in draft.recurrenceWeekdays, onClick = { onToggleWeekday(day) }, label = { Text(label) })
                        }
                    }
                }
                OutlinedTextField(draft.recurrenceUntil, onRecurrenceUntilChange, Modifier.fillMaxWidth(), label = { Text("Hasta (dd-MM-aaaa), opcional") }, singleLine = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSave) {
                    Text("Guardar")
                }
                Button(onClick = onClear) {
                    Text("Limpiar")
                }
            }
        }
    }
}

@Composable
private fun CalendarSection(
    title: String,
    events: List<CalendarEventItem>,
    onEdit: (CalendarEventItem) -> Unit,
    onDelete: (CalendarEventItem) -> Unit,
    restoreMode: Boolean,
    onRestore: ((CalendarEventItem) -> Unit)? = null,
    onDeletePermanently: ((CalendarEventItem) -> Unit)? = null,
    occurrences: List<com.majyd.casa.data.model.CalendarOccurrence> = emptyList(),
    onEditOccurrence: ((com.majyd.casa.data.model.CalendarOccurrence) -> Unit)? = null,
    onCancelOccurrence: ((com.majyd.casa.data.model.CalendarOccurrence) -> Unit)? = null,
    onOpenOccurrence: ((com.majyd.casa.data.model.CalendarOccurrence) -> Unit)? = null,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (events.isEmpty()) {
                Text("Sin eventos")
            } else {
                if (occurrences.isNotEmpty()) {
                    occurrences.forEach { occurrence ->
                        CalendarOccurrenceRow(occurrence, onEditOccurrence, onCancelOccurrence, onDelete) { onOpenOccurrence?.invoke(occurrence) }
                    }
                } else events.forEach { event ->
                    CalendarEventRow(event, onEdit, onDelete, restoreMode, onRestore, onDeletePermanently)
                }
            }
        }
    }
}

@Composable
private fun CalendarOccurrenceRow(
    occurrence: com.majyd.casa.data.model.CalendarOccurrence,
    onEdit: ((com.majyd.casa.data.model.CalendarOccurrence) -> Unit)?,
    onCancel: ((com.majyd.casa.data.model.CalendarOccurrence) -> Unit)?,
    onDelete: (CalendarEventItem) -> Unit,
    onOpen: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(occurrence.source.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(if (occurrence.source.allDay) "Todo el día" else formatOccurrenceTime(occurrence), style = MaterialTheme.typography.bodyMedium)
            if (occurrence.source.location.isNotBlank()) Text(occurrence.source.location, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (occurrence.source.recurrenceType != CalendarRecurrenceType.NONE) {
                    TextButton(onClick = { onEdit?.invoke(occurrence) }) { Text("Editar esta aparición") }
                    TextButton(onClick = { onCancel?.invoke(occurrence) }) { Text("Cancelar esta aparición") }
                } else {
                    TextButton(onClick = { onEdit?.invoke(occurrence) }) { Text("Editar") }
                    TextButton(onClick = onOpen) { Text("Eliminar") }
                }
            }
        }
    }
}

private fun formatOccurrenceTime(occurrence: com.majyd.casa.data.model.CalendarOccurrence): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val zone = java.time.ZoneId.systemDefault()
    return "${formatter.format(java.time.Instant.ofEpochMilli(occurrence.startAt).atZone(zone))} - ${formatter.format(java.time.Instant.ofEpochMilli(occurrence.endAt).atZone(zone))}"
}

@Composable
private fun CalendarEventRow(
    event: CalendarEventItem,
    onEdit: (CalendarEventItem) -> Unit,
    onDelete: (CalendarEventItem) -> Unit,
    restoreMode: Boolean,
    onRestore: ((CalendarEventItem) -> Unit)?,
    onDeletePermanently: ((CalendarEventItem) -> Unit)?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(formatEventTime(event), style = MaterialTheme.typography.bodyMedium)
            if (event.location.isNotBlank()) {
                Text(event.location, style = MaterialTheme.typography.bodySmall)
            }
            if (event.notes.isNotBlank()) {
                Text(event.notes, style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!restoreMode) {
                    AssistChip(onClick = { onEdit(event) }, label = { Text("Editar") })
                    IconButton(onClick = { onDelete(event) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(onClick = { onRestore?.invoke(event) }) {
                        Icon(Icons.Filled.Restore, contentDescription = "Restaurar")
                    }
                    IconButton(onClick = { onDeletePermanently?.invoke(event) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar definitivamente", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private val calendarDateFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT)

@Composable
private fun CalendarDateWheelPicker(value: String, onValueChange: (String) -> Unit) {
    val selectedDate = runCatching { LocalDate.parse(value.take(10), calendarDateFormatter) }.getOrElse { LocalDate.now() }
    val yearRange = 2020..2100
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        CalendarWheel("Dia", 1..selectedDate.lengthOfMonth(), selectedDate.dayOfMonth) { day ->
            onValueChange(updateCalendarDateTime(value, LocalDate.of(selectedDate.year, selectedDate.monthValue, day)))
        }
        CalendarWheel("Mes", 1..12, selectedDate.monthValue) { month ->
            val day = min(selectedDate.dayOfMonth, LocalDate.of(selectedDate.year, month, 1).lengthOfMonth())
            onValueChange(updateCalendarDateTime(value, LocalDate.of(selectedDate.year, month, day)))
        }
        CalendarWheel("Ano", yearRange, selectedDate.year.coerceIn(yearRange.first, yearRange.last)) { year ->
            val day = min(selectedDate.dayOfMonth, LocalDate.of(year, selectedDate.monthValue, 1).lengthOfMonth())
            onValueChange(updateCalendarDateTime(value, LocalDate.of(year, selectedDate.monthValue, day)))
        }
    }
}

@Composable
private fun CalendarTimeWheelPicker(value: String, onValueChange: (String) -> Unit) {
    val selectedTime = runCatching { LocalTime.parse(value.drop(11).take(5)) }.getOrElse { LocalTime.of(23, 59) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CalendarWheel("Hora", 0..23, selectedTime.hour, wrap = true, padded = true) { hour ->
            onValueChange(updateCalendarTime(value, hour, selectedTime.minute))
        }
        Text(":", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))
        CalendarWheel("Minutos", 0..59, selectedTime.minute, wrap = true, padded = true) { minute ->
            onValueChange(updateCalendarTime(value, selectedTime.hour, minute))
        }
    }
}

@Composable
private fun CalendarWheel(label: String, range: IntRange, selected: Int, wrap: Boolean = false, padded: Boolean = false, onSelected: (Int) -> Unit) {
    val latestOnSelected = rememberUpdatedState(onSelected)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        AndroidView(
            modifier = Modifier.height(120.dp),
            factory = { context ->
                android.widget.NumberPicker(context).apply {
                    minValue = range.first
                    maxValue = range.last
                    displayedValues = if (padded) (range.first..range.last).map { "%02d".format(it) }.toTypedArray() else null
                    value = selected.coerceIn(range.first, range.last)
                    wrapSelectorWheel = wrap
                    setOnValueChangedListener { _, _, newValue -> latestOnSelected.value(newValue) }
                }
            },
            update = { picker ->
                picker.minValue = range.first
                picker.maxValue = range.last
                picker.displayedValues = if (padded) (range.first..range.last).map { "%02d".format(it) }.toTypedArray() else null
                picker.wrapSelectorWheel = wrap
                val safeSelected = selected.coerceIn(range.first, range.last)
                if (picker.value != safeSelected) picker.value = safeSelected
            },
        )
    }
}

private fun updateCalendarDateTime(value: String, date: LocalDate): String {
    val time = value.drop(11).take(5).ifBlank { "23:59" }
    return "${date.format(calendarDateFormatter)} $time"
}

private fun updateCalendarTime(value: String, hour: Int, minute: Int): String {
    val date = runCatching { LocalDate.parse(value.take(10), calendarDateFormatter) }.getOrElse { LocalDate.now() }
    return "${date.format(calendarDateFormatter)} %02d:%02d".format(hour, minute)
}

@Composable
private fun PlaceManagerDialog(places: List<CalendarPlace>, onSave: (String?, String) -> Unit, onDelete: (CalendarPlace) -> Unit, onSelect: (CalendarPlace) -> Unit, onDismiss: () -> Unit) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lugares guardados") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                places.forEach { place ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSelect(place) }, modifier = Modifier.weight(1f)) { Text(place.name) }
                        TextButton(onClick = { editingId = place.id; name = place.name }) { Text("Editar") }
                        IconButton(onClick = { onDelete(place) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar lugar", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text(if (editingId == null) "Nuevo lugar" else "Editar lugar") }, singleLine = true)
                Button(onClick = { onSave(editingId, name); editingId = null; name = "" }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (editingId == null) "Guardar lugar" else "Guardar cambio") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun formatEventTime(event: CalendarEventItem): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm")
    val zone = java.time.ZoneId.systemDefault()
    val start = formatter.format(java.time.Instant.ofEpochMilli(event.startAt).atZone(zone))
    val end = formatter.format(java.time.Instant.ofEpochMilli(event.endAt).atZone(zone))
    return if (event.allDay) {
        "Todo el dia - $start"
    } else {
        "$start -> $end"
    }
}
