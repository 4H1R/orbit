package dev.alializadeh.orbit.occasion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.interaction.PeoplePickerSheet
import dev.alializadeh.orbit.interaction.PickablePerson
import dev.alializadeh.orbit.person.dates.RemindersChoice
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.datepicker.WheelDatePicker
import dev.alializadeh.orbit.ui.datepicker.WheelDatePickerState
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import kotlinx.serialization.Serializable

/** The Occasion editor: an existing Occasion, a new built-in one ([builtIn] by name), or a new one of the user's own. */
@Serializable
data class OccasionEditKey(val occasionId: Long? = null, val builtIn: String? = null) : NavKey

@Composable
fun OccasionEditRoute(
    key: OccasionEditKey,
    onClose: () -> Unit,
    /** Deleted: leave the Occasion's own screen too, if it's under this one. */
    onDeleted: () -> Unit = onClose,
    viewModel: OccasionEditViewModel = hiltViewModel<OccasionEditViewModel, OccasionEditViewModel.Factory>(
        creationCallback = { factory -> factory.create(key.occasionId, key.builtIn) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    LaunchedEffect(exit) {
        when (exit) {
            OccasionEditExit.Saved -> onClose()
            OccasionEditExit.Deleted -> onDeleted()
            null -> Unit
        }
    }
    OccasionEditScreen(
        uiState = uiState,
        isNew = viewModel.isNew,
        builtIn = viewModel.builtIn,
        name = viewModel.name,
        picker = viewModel.picker,
        onPicker = { viewModel.picker = it },
        forEveryone = viewModel.forEveryone,
        onForEveryone = { viewModel.forEveryone = it },
        ringIds = viewModel.ringIds,
        onRingIds = { viewModel.ringIds = it },
        tagIds = viewModel.tagIds,
        onTagIds = { viewModel.tagIds = it },
        personIds = viewModel.personIds,
        onPersonIds = { viewModel.personIds = it },
        leadTimes = viewModel.leadTimes,
        onLeadTimes = { viewModel.leadTimes = it },
        onBack = onClose,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

/**
 * Name (the user's own only), Date (the wheel picker without a year, any Calendar; a built-in
 * Occasion's day is fixed), Who it's for (Everyone, or any of Rings, Tags and People, with how many
 * that reaches), Reminders (Default or Custom), and Delete for an existing one.
 */
@Composable
fun OccasionEditScreen(
    uiState: OccasionEditUiState,
    isNew: Boolean,
    builtIn: BuiltInOccasion?,
    name: TextFieldState,
    picker: WheelDatePickerState?,
    onPicker: (WheelDatePickerState) -> Unit,
    forEveryone: Boolean,
    onForEveryone: (Boolean) -> Unit,
    ringIds: Set<Long>,
    onRingIds: (Set<Long>) -> Unit,
    tagIds: Set<Long>,
    onTagIds: (Set<Long>) -> Unit,
    personIds: Set<Long>,
    onPersonIds: (Set<Long>) -> Unit,
    leadTimes: Set<LeadTime>?,
    onLeadTimes: (Set<LeadTime>?) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val canSave = picker != null && (builtIn != null || name.text.isNotBlank())
    Scaffold(
        modifier = modifier,
        snackbarHost = { OrbitSnackbarHost() },
        topBar = {
            OrbitTopAppBar(
                title = stringResource(if (isNew) R.string.occasion_new_title else R.string.occasion_edit_title),
                onBack = onBack,
                actions = {
                    TextButton(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.common_save)) }
                },
            )
        },
    ) { innerPadding ->
        val loaded = uiState as? OccasionEditUiState.Loaded
        if (loaded == null || picker == null) return@Scaffold
        val formats = LocalOrbitFormats.current
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            ) {
                if (builtIn != null) {
                    Text(stringResource(builtIn.nameRes), style = MaterialTheme.typography.headlineSmall)
                    Text(formats.dates.format(builtIn.date), style = MaterialTheme.typography.bodyLarge)
                } else {
                    OutlinedTextField(
                        state = name,
                        label = { Text(stringResource(R.string.occasion_name)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OrbitSectionTitle(stringResource(R.string.occasion_date), horizontalPadding = 4.dp)
                    WheelDatePicker(state = picker, onStateChange = onPicker, calendars = CalendarSystem.entries, showYear = false)
                }

                OrbitSectionTitle(stringResource(R.string.occasion_who), horizontalPadding = 4.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = forEveryone, role = Role.Switch, onValueChange = onForEveryone)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.occasion_who_everyone), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = forEveryone, onCheckedChange = null)
                }
                if (!forEveryone) {
                    WhoChips(
                        loaded = loaded,
                        ringIds = ringIds,
                        onRingIds = onRingIds,
                        tagIds = tagIds,
                        onTagIds = onTagIds,
                        personIds = personIds,
                        onPersonIds = onPersonIds,
                        onAddPerson = { picking = true },
                    )
                }
                val reach = OccasionWithAudience(
                    occasion = Occasion(date = picker.date.copy(year = null), forEveryone = forEveryone),
                    ringIds = ringIds,
                    tagIds = tagIds,
                    personIds = personIds,
                ).audience(loaded.people).size
                Text(
                    if (reach == 0) {
                        stringResource(R.string.occasion_reaches_nobody)
                    } else {
                        pluralStringResource(R.plurals.occasion_reaches, reach, formats.numbers.format(reach))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                RemindersChoice(leadTimes = leadTimes, defaultLeadTimes = loaded.defaultLeadTimes, onChange = onLeadTimes)

                if (!isNew) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                }
            }
        }

        if (picking) {
            PeoplePickerSheet(
                candidates = loaded.people
                    .filter { !it.isArchived && it.id !in personIds }
                    .map { PickablePerson(it.id, it.names) },
                onPick = { id -> onPersonIds(personIds + id) },
                onDismiss = { picking = false },
            )
        }
        if (confirmDelete) {
            val title = builtIn?.let { stringResource(it.nameRes) } ?: bidiIsolate(name.text.toString())
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.occasion_delete_title, title)) },
                text = { Text(stringResource(R.string.occasion_delete_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            onDelete()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
            )
        }
    }
}

/** Rings, Tags and People to reach (any of them is enough). */
@Composable
private fun WhoChips(
    loaded: OccasionEditUiState.Loaded,
    ringIds: Set<Long>,
    onRingIds: (Set<Long>) -> Unit,
    tagIds: Set<Long>,
    onTagIds: (Set<Long>) -> Unit,
    personIds: Set<Long>,
    onPersonIds: (Set<Long>) -> Unit,
    onAddPerson: () -> Unit,
) {
    val language = LocalOrbitFormats.current.language
    if (ringIds.isEmpty() && tagIds.isEmpty() && personIds.isEmpty()) {
        Text(
            stringResource(R.string.occasion_who_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    ChipGroup(stringResource(R.string.occasion_who_rings)) {
        loaded.rings.forEach { ring ->
            val selected = ring.id in ringIds
            FilterChip(
                selected = selected,
                onClick = { onRingIds(if (selected) ringIds - ring.id else ringIds + ring.id) },
                label = { Text(ring.name) },
            )
        }
    }
    if (loaded.tags.isNotEmpty()) {
        ChipGroup(stringResource(R.string.occasion_who_tags)) {
            loaded.tags.forEach { tag ->
                val selected = tag.id in tagIds
                FilterChip(
                    selected = selected,
                    onClick = { onTagIds(if (selected) tagIds - tag.id else tagIds + tag.id) },
                    label = { Text(tag.name) },
                )
            }
        }
    }
    ChipGroup(stringResource(R.string.occasion_who_people)) {
        loaded.people.filter { it.id in personIds }.sortedBy(UpcomingPerson::id).forEach { person ->
            val name = person.names.displayName(language)
            val remove = stringResource(R.string.common_remove_item, bidiIsolate(name))
            InputChip(
                selected = false,
                onClick = { onPersonIds(personIds - person.id) },
                label = { Text(name) },
                avatar = { PersonAvatar(name = name, photo = null, size = 24.dp) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = remove, modifier = Modifier.size(18.dp)) },
            )
        }
        AssistChip(
            onClick = onAddPerson,
            label = { Text(stringResource(R.string.interaction_people_add)) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun ChipGroup(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { chips() }
    }
}
