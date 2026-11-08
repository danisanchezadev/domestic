package com.majyd.casa.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.majyd.casa.data.model.AppPreferences
import com.majyd.casa.data.model.ColorTheme
import com.majyd.casa.data.model.DashboardCard
import com.majyd.casa.ui.navigation.toDestination

private const val MAX_GROUP_NAME_LENGTH = 9

private enum class SettingsSection(val title: String) {
    MENU("Ajustes"),
    GROUPS("Grupos"),
    HOUSEHOLD("Hogar"),
    HOME_NAME("Nombre de la casa"),
    APPEARANCE("Apariencia"),
    DASHBOARD("Tarjetas resumen"),
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onBack: () -> Unit,
    onOpenSync: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val household by viewModel.household.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val authorizedDevices by viewModel.authorizedDevices.collectAsStateWithLifecycle()
    val personActionMessage by viewModel.personActionMessage.collectAsStateWithLifecycle()
    val pendingPersonDeletion by viewModel.pendingPersonDeletion.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(SettingsSection.MENU) }
    var newPersonName by remember { mutableStateOf("") }
    var newGroupName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(section.title) },
            navigationIcon = {
                IconButton(onClick = { if (section == SettingsSection.MENU) onBack() else section = SettingsSection.MENU }) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (section) {
            SettingsSection.MENU -> SettingsMenu(
                onSelect = { section = it },
                onOpenSync = onOpenSync,
            )
                SettingsSection.GROUPS -> GroupsSection(
                    household = household,
                    newGroupName = newGroupName,
                    onNewGroupNameChange = { newGroupName = it.take(MAX_GROUP_NAME_LENGTH) },
                    onAddGroup = { viewModel.addGroup(newGroupName); newGroupName = "" },
                    onRenameGroup = viewModel::renameGroup,
                    onSetGroupMembers = viewModel::setGroupMembers,
                    onDeleteGroup = viewModel::deleteGroup,
                )
                SettingsSection.HOUSEHOLD -> HouseholdSection(
                    household = household,
                    devicePersonId = device?.assignedPersonId,
                    deviceId = device?.deviceId,
                    deviceDisplayName = device?.displayName ?: "Este móvil",
                    authorizedDevices = authorizedDevices,
                    newPersonName = newPersonName,
                    personActionMessage = personActionMessage,
                    onNewPersonNameChange = { newPersonName = it },
                    onAddPerson = { viewModel.addPerson(newPersonName); newPersonName = "" },
                    onRenamePerson = viewModel::renamePerson,
                    onAssignDevice = viewModel::assignDevice,
                    onDeletePerson = viewModel::deletePerson,
                )
                SettingsSection.HOME_NAME -> HomeNameSection(preferences.homeName, viewModel::updateHomeName)
                SettingsSection.APPEARANCE -> AppearanceSection(
                    darkMode = preferences.darkMode,
                    colorTheme = preferences.colorTheme,
                    onDarkModeChange = viewModel::updateDarkMode,
                    onColorThemeChange = viewModel::updateColorTheme,
                )
                SettingsSection.DASHBOARD -> DashboardSection(
                    cards = preferences.dashboardCards,
                    onMoveUp = viewModel::moveDashboardCardUp,
                    onMoveDown = viewModel::moveDashboardCardDown,
                    onToggle = viewModel::toggleDashboardCard,
                )
            }
        }
    }

    pendingPersonDeletion?.let { personId ->
        val person = household.people.firstOrNull { it.id == personId }
        AlertDialog(
            onDismissRequest = viewModel::cancelPersonDeletion,
            title = { Text("Tareas activas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("La persona tiene tareas activas. Elige que hacer antes de eliminarla.")
                    household.people.filterNot { it.id == person?.id }.forEach { replacement ->
                        TextButton(onClick = { viewModel.resolvePersonTasks(replacement.id) }) { Text("Reasignar a ${replacement.name}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.resolvePersonTasks(null) }) { Text("Dejar sin asignar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelPersonDeletion) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsMenu(onSelect: (SettingsSection) -> Unit, onOpenSync: () -> Unit) {
    FlowRow(
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingMenuCard("Grupos", "Organizar personas", Icons.Filled.Group) { onSelect(SettingsSection.GROUPS) }
        SettingMenuCard("Hogar", "Gestionar personas", Icons.Filled.Home) { onSelect(SettingsSection.HOUSEHOLD) }
        SettingMenuCard("Union de dispositivos", "Wi-Fi y vinculacion", Icons.Filled.Wifi) { onOpenSync() }
        SettingMenuCard("Nombre de la casa", "Identidad del hogar", Icons.Filled.Home) { onSelect(SettingsSection.HOME_NAME) }
        SettingMenuCard("Apariencia", "Colores y modo oscuro", Icons.Filled.Palette) { onSelect(SettingsSection.APPEARANCE) }
        SettingMenuCard("Tarjetas resumen", "Orden y visibilidad", Icons.Filled.Tune) { onSelect(SettingsSection.DASHBOARD) }
    }
}

@Composable
private fun SettingMenuCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.48f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupsSection(
    household: com.majyd.casa.data.model.HouseholdState,
    newGroupName: String,
    onNewGroupNameChange: (String) -> Unit,
    onAddGroup: () -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onSetGroupMembers: (String, Set<String>) -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    household.groups.forEach { group ->
        var groupName by rememberSaveable(group.id) { mutableStateOf(group.name) }
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(groupName, { groupName = it.take(MAX_GROUP_NAME_LENGTH) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nombre del grupo") })
                TextButton(onClick = { onRenameGroup(group.id, groupName) }, enabled = groupName.isNotBlank() && groupName != group.name) { Text("Guardar nombre") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    household.people.forEach { person ->
                        FilterChip(
                            selected = group.memberIds.contains(person.id),
                            onClick = {
                                val members = if (group.memberIds.contains(person.id)) group.memberIds - person.id else group.memberIds + person.id
                                onSetGroupMembers(group.id, members)
                            },
                            label = { Text(person.name) },
                        )
                    }
                }
                TextButton(onClick = { onDeleteGroup(group.id) }) { Text("Eliminar grupo") }
            }
        }
    }
    OutlinedTextField(newGroupName, onNewGroupNameChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nuevo grupo") })
    Button(onClick = onAddGroup, enabled = newGroupName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Crear grupo") }
}

@Composable
private fun HouseholdSection(
    household: com.majyd.casa.data.model.HouseholdState,
    devicePersonId: String?,
    deviceId: String?,
    deviceDisplayName: String,
    authorizedDevices: List<com.majyd.casa.data.local.entities.AuthorizedDeviceEntity>,
    newPersonName: String,
    personActionMessage: String?,
    onNewPersonNameChange: (String) -> Unit,
    onAddPerson: () -> Unit,
    onRenamePerson: (String, String) -> Unit,
    onAssignDevice: (String?) -> Unit,
    onDeletePerson: (String) -> Unit,
) {
    var showAddPerson by rememberSaveable { mutableStateOf(false) }
    var editingPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeletePersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var visibleDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    Text("Personas del hogar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Gestiona los perfiles y el móvil que utiliza cada persona.", style = MaterialTheme.typography.bodyMedium)
    Button(onClick = { showAddPerson = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Añadir persona")
    }
    household.people.forEach { person ->
        var personName by rememberSaveable(person.id) { mutableStateOf(person.name) }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (person.id == devicePersonId) {
                    Text("Este móvil", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { editingPersonId = person.id }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar ${person.name}")
                    }
                    IconButton(onClick = { onAssignDevice(person.id) }) {
                        Icon(Icons.Filled.Link, contentDescription = "Asociar este móvil a ${person.name}")
                    }
                    IconButton(onClick = { pendingDeletePersonId = person.id }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${person.name}", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (editingPersonId == person.id) {
            AlertDialog(
                onDismissRequest = { editingPersonId = null },
                title = { Text("Editar persona") },
                text = {
                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { onRenamePerson(person.id, personName); editingPersonId = null },
                        enabled = personName.isNotBlank() && personName != person.name,
                    ) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { editingPersonId = null }) { Text("Cancelar") } },
            )
        }
    }
    personActionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

    Spacer(Modifier.height(8.dp))
    Text("Dispositivos vinculados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    DeviceCard(
        displayName = deviceDisplayName,
        personName = household.people.firstOrNull { it.id == devicePersonId }?.name,
        isCurrent = true,
        onShowId = { deviceId?.let { visibleDeviceId = it } },
    )
    authorizedDevices.filter { it.deviceId != deviceId && !it.revoked }.forEach { remote ->
        DeviceCard(
            displayName = remote.displayName,
            personName = household.people.firstOrNull { it.id == remote.assignedPersonId }?.name,
            isCurrent = false,
            onShowId = { visibleDeviceId = remote.deviceId },
        )
    }

    if (showAddPerson) {
        AlertDialog(
            onDismissRequest = { showAddPerson = false },
            title = { Text("Añadir persona") },
            text = {
                OutlinedTextField(
                    value = newPersonName,
                    onValueChange = onNewPersonNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onAddPerson(); showAddPerson = false }, enabled = newPersonName.isNotBlank()) { Text("Añadir") }
            },
            dismissButton = { TextButton(onClick = { showAddPerson = false }) { Text("Cancelar") } },
        )
    }
    pendingDeletePersonId?.let { personId ->
        val person = household.people.firstOrNull { it.id == personId }
        AlertDialog(
            onDismissRequest = { pendingDeletePersonId = null },
            title = { Text("Eliminar persona") },
            text = { Text("¿Quieres eliminar a ${person?.name.orEmpty()}? Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { onDeletePerson(personId); pendingDeletePersonId = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { pendingDeletePersonId = null }) { Text("Cancelar") } },
        )
    }
    visibleDeviceId?.let { id ->
        AlertDialog(
            onDismissRequest = { visibleDeviceId = null },
            title = { Text("Identificador del dispositivo") },
            text = { Text(id) },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(id)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar identificador")
                    Text("Copiar", modifier = Modifier.padding(start = 6.dp))
                }
            },
            dismissButton = { TextButton(onClick = { visibleDeviceId = null }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun DeviceCard(
    displayName: String,
    personName: String?,
    isCurrent: Boolean,
    onShowId: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (isCurrent) "Este dispositivo" else "Dispositivo vinculado", color = MaterialTheme.colorScheme.primary)
            Text("Persona: ${personName ?: "sin asociar"}")
            TextButton(onClick = onShowId, modifier = Modifier.align(Alignment.End)) { Text("Ver ID") }
        }
    }
}

@Composable
private fun HomeNameSection(homeName: String, onHomeNameChange: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(homeName) }
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("El nombre se muestra en la cabecera principal.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(18) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Maximo 18 caracteres") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Button(
                onClick = { onHomeNameChange(draft) },
                enabled = draft != homeName,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar nombre") }
        }
    }
}

@Composable
private fun WifiSection(
    ssid: String,
    onSsidChange: (String) -> Unit,
    needsPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Indica el nombre de la red Wi-Fi permitida para este hogar. Android puede no permitir leer el SSID automaticamente.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(ssid, onSsidChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nombre de la red Wi-Fi (SSID)") })
            if (needsPermission) Button(onClick = onRequestPermission) { Text("Permitir identificar la red Wi-Fi") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    darkMode: Boolean,
    colorTheme: ColorTheme,
    onDarkModeChange: (Boolean) -> Unit,
    onColorThemeChange: (ColorTheme) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onDarkModeChange(!darkMode) }, modifier = Modifier.fillMaxWidth()) { Text(if (darkMode) "Usar tema claro" else "Usar tema oscuro") }
            Text("Color principal", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorTheme.entries.forEach { theme ->
                    FilterChip(selected = colorTheme == theme, onClick = { onColorThemeChange(theme) }, label = { Text(theme.label) })
                }
            }
        }
    }
}

@Composable
private fun DashboardSection(
    cards: List<DashboardCard>,
    onMoveUp: (DashboardCard) -> Unit,
    onMoveDown: (DashboardCard) -> Unit,
    onToggle: (DashboardCard) -> Unit,
) {
    Text("Ordena y muestra las tarjetas de la portada.", style = MaterialTheme.typography.bodyMedium)
    val visibleCards = AppPreferences.normalizeDashboardCards(cards)
    val allCards = visibleCards + DashboardCard.values()
        .filterNot { it == DashboardCard.SYNC || it == DashboardCard.SETTINGS || it in visibleCards }
    allCards.forEach { card ->
        val visible = card in visibleCards
        DashboardCardRow(card, visible, { onMoveUp(card) }, { onMoveDown(card) }, { onToggle(card) })
    }
}

@Composable
private fun DashboardCardRow(
    card: DashboardCard,
    visible: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = visible, onCheckedChange = { onToggle() })
            Text(card.toDestination().title, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onMoveUp, enabled = visible) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir") }
            IconButton(onClick = onMoveDown, enabled = visible) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Bajar") }
        }
    }
}
