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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majyd.casa.data.model.CalendarEventItem
import com.majyd.casa.data.model.CalendarPlace
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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = { Text("Agenda") },
                actions = {
                    TextButton(onClick = viewModel::archivePast) {
                        Text("Archivar pasados")
                    }
                },
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
                CalendarDraftCard(
                    draft = state.draft,
                    onTitleChange = viewModel::updateTitle,
                    onNotesChange = viewModel::updateNotes,
                    onLocationChange = viewModel::updateLocation,
                    onManagePlaces = { managePlaces = true },
                    onStartChange = viewModel::updateStartAt,
                    onEndChange = viewModel::updateEndAt,
                    onAllDayChange = viewModel::updateAllDay,
                    onReminderChange = viewModel::updateReminder,
                    onSave = viewModel::saveEvent,
                    onClear = viewModel::clearDraft,
                )
            }
            item {
                CalendarSection(
                    title = "Eventos activos",
                    events = state.activeEvents,
                    onEdit = viewModel::editEvent,
                    onDelete = viewModel::deleteEvent,
                    restoreMode = false,
                )
            }
            item {
                CalendarSection(
                    title = "Papelera",
                    events = state.deletedEvents,
                    onEdit = viewModel::editEvent,
                    onDelete = viewModel::deleteEvent,
                    onRestore = viewModel::restoreEvent,
                    onDeletePermanently = viewModel::deleteEventPermanently,
                    restoreMode = true,
                )
            }
        }
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
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (events.isEmpty()) {
                Text("Sin eventos")
            } else {
                events.forEach { event ->
                    CalendarEventRow(
                        event = event,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        restoreMode = restoreMode,
                        onRestore = onRestore,
                        onDeletePermanently = onDeletePermanently,
                    )
                }
            }
        }
    }
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
