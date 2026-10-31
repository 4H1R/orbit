package dev.alializadeh.orbit.interaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.CalendarDate
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.calendar.inCalendar
import dev.alializadeh.orbit.due.SnoozeDuration
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.FollowUpDraft
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.dates.WheelDatePickerStateSaver
import dev.alializadeh.orbit.person.dates.rememberFutureDateChoice
import dev.alializadeh.orbit.person.sortByDisplayName
import dev.alializadeh.orbit.person.ui.PersonAvatar
import dev.alializadeh.orbit.person.ui.icon
import dev.alializadeh.orbit.person.ui.label
import dev.alializadeh.orbit.search.SearchText
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import dev.alializadeh.orbit.ui.datepicker.WheelDatePicker
import dev.alializadeh.orbit.ui.datepicker.WheelDatePickerState
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.io.File
import java.time.LocalDate

/**
 * Draws [viewModel]'s open sheet: Log interaction (new or edit) or Snooze. Snoozing shows "Snoozed
 * Sara for 1 week · Undo" through the app-wide snackbars.
 */
@Composable
fun KeepInTouchSheets(viewModel: KeepInTouchViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state.log?.let { log ->
        LogInteractionSheet(
            state = log,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = viewModel::dismiss,
            onSaveFollowUp = viewModel::saveFollowUp,
        )
    }
    state.snooze?.let { snooze ->
        val snackbars = LocalOrbitSnackbars.current
        val resources = LocalResources.current
        val name = bidiIsolate(snooze.names.displayName(LocalOrbitFormats.current.language))
        val durations = SnoozeDuration.entries.associateWith { it.text() }
        val undoLabel = stringResource(R.string.common_undo)
        SnoozeSheet(
            state = snooze,
            onPick = { duration ->
                viewModel.snooze(snooze.personId, duration) { undo ->
                    snackbars?.show(resources.getString(R.string.snoozed_snackbar, name, durations[duration]), undoLabel, undo)
                }
            },
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
fun LogInteractionSheet(
    state: LogSheetState,
    onSave: (InteractionDraft) -> Unit,
    onDelete: (interactionId: Long) -> Unit,
    onDismiss: () -> Unit,
    onSaveFollowUp: (personId: Long, draft: FollowUpDraft) -> Unit = { _, _ -> },
) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        LogInteractionSheetContent(state, onSave, onDelete, sheet = sheet, onSaveFollowUp = onSaveFollowUp)
    }
}

private enum class DateChoice { Today, Yesterday, Pick }

/**
 * The inside of [LogInteractionSheet]: Type (defaulting to the last type used), Date (Today,
 * Yesterday or a picked day, never in the future), People (the starting Person, "+ Add" for more,
 * never Archived ones), Note, then, for a new one, an optional Follow-up with one of them, and
 * Save; Delete too when editing. Drawn by itself in screenshot tests.
 */
@Composable
internal fun LogInteractionSheetContent(
    state: LogSheetState,
    onSave: (InteractionDraft) -> Unit,
    onDelete: (interactionId: Long) -> Unit,
    sheet: SheetCloser,
    /** Screenshot tests open the picker straight away. */
    startPicking: Boolean = false,
    onSaveFollowUp: (personId: Long, draft: FollowUpDraft) -> Unit = { _, _ -> },
) {
    val formats = LocalOrbitFormats.current
    val today = state.today
    var type by rememberSaveable { mutableStateOf(state.type) }
    var dateChoice by rememberSaveable {
        mutableStateOf(
            when (state.date) {
                today -> DateChoice.Today
                today.minusDays(1) -> DateChoice.Yesterday
                else -> DateChoice.Pick
            },
        )
    }
    var picker by rememberSaveable(stateSaver = WheelDatePickerStateSaver) {
        mutableStateOf(pickerOn(state.date, formats.primaryCalendar, today))
    }
    var personIds by rememberSaveable(stateSaver = IdListSaver) { mutableStateOf(state.people.map(PickablePerson::id)) }
    var picking by rememberSaveable { mutableStateOf(startPicking) }
    val note = rememberTextFieldState(state.note)
    val known = remember(state) { (state.people + state.candidates).associateBy(PickablePerson::id) }
    val date = when (dateChoice) {
        DateChoice.Today -> today
        DateChoice.Yesterday -> today.minusDays(1)
        DateChoice.Pick -> picker.date.let { CalendarDate(it.calendar, it.year!!, it.month, it.day).toLocalDate() }
    }
    val inFuture = date.isAfter(today)
    var followingUp by rememberSaveable { mutableStateOf(false) }
    var followUpChosen by rememberSaveable { mutableStateOf<Long?>(null) }
    val followUpText = rememberTextFieldState()
    val followUpDay = rememberFutureDateChoice(initial = null, today = today, allowNone = false)
    // Whoever was chosen, while they're still in it; else the first.
    val followUpPerson = followUpChosen?.takeIf { it in personIds } ?: personIds.firstOrNull()
    val followUpReady = !followingUp || (followUpText.text.isNotBlank() && followUpDay.isValid)
    val canSave = personIds.isNotEmpty() && !inFuture && followUpReady

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(if (state.interactionId == null) R.string.interaction_log else R.string.interaction_sheet_edit),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        val typeLabel = stringResource(R.string.interaction_type_label)
        OrbitConnectedChoice(
            options = InteractionType.entries,
            selected = type,
            onSelect = { type = it },
            label = { it.displayName() },
            modifier = Modifier.semantics { contentDescription = typeLabel },
        )
        FieldLabel(stringResource(R.string.interaction_date))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateChoice.entries.forEach { choice ->
                FilterChip(
                    selected = dateChoice == choice,
                    onClick = { dateChoice = choice },
                    label = {
                        Text(
                            stringResource(
                                when (choice) {
                                    DateChoice.Today -> R.string.time_today
                                    DateChoice.Yesterday -> R.string.interaction_date_yesterday
                                    DateChoice.Pick -> R.string.interaction_date_pick
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (dateChoice == DateChoice.Pick) {
            WheelDatePicker(state = picker, onStateChange = { picker = it })
            // Always laid out, so the sheet's height doesn't change after a wheel scroll.
            Text(
                text = if (inFuture) stringResource(R.string.interaction_date_future) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FieldLabel(stringResource(R.string.interaction_people))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            personIds.mapNotNull(known::get).forEach { person ->
                val name = person.names.displayName(formats.language)
                val remove = stringResource(R.string.common_remove_item, bidiIsolate(name))
                InputChip(
                    selected = false,
                    onClick = { personIds = personIds - person.id },
                    label = { Text(name) },
                    avatar = { PersonAvatar(name = name, photo = person.photo, size = 24.dp) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = remove, modifier = Modifier.size(18.dp)) },
                )
            }
            AssistChip(
                onClick = { picking = true },
                label = { Text(stringResource(R.string.interaction_people_add)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        OutlinedTextField(
            state = note,
            label = { Text(stringResource(R.string.interaction_note)) },
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.interactionId == null) {
            if (followingUp) {
                if (personIds.size > 1) {
                    FieldLabel(stringResource(R.string.follow_up_log_person))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        personIds.mapNotNull(known::get).forEach { person ->
                            FilterChip(
                                selected = person.id == followUpPerson,
                                onClick = { followUpChosen = person.id },
                                label = { Text(person.names.displayName(formats.language)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    state = followUpText,
                    label = { Text(stringResource(R.string.follow_up_text)) },
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldLabel(stringResource(R.string.follow_up_date))
                followUpDay.Field()
            } else {
                AssistChip(
                    onClick = { followingUp = true },
                    label = { Text(stringResource(R.string.follow_up_log_add)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val id = state.interactionId
            if (id != null) {
                TextButton(
                    onClick = { sheet.close { onDelete(id) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { sheet.close {} }) { Text(stringResource(R.string.common_cancel)) }
            Button(
                enabled = canSave,
                onClick = {
                    val draft = InteractionDraft(
                        id = state.interactionId,
                        type = type,
                        date = date,
                        note = note.text.toString(),
                        personIds = personIds,
                    ).normalised()
                    val followUpFor = followUpPerson?.takeIf { followingUp }
                    val followUp = followUpDay.date?.let { FollowUpDraft(text = followUpText.text.toString(), dueOn = it).normalised() }
                    sheet.close {
                        onSave(draft)
                        if (followUpFor != null && followUp != null) onSaveFollowUp(followUpFor, followUp)
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }

    if (picking) {
        PeoplePickerSheet(
            candidates = state.candidates.filter { it.id !in personIds },
            onPick = { id -> personIds = personIds + id },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A picker on [date] in the Primary calendar, with a year and no "No year". */
private fun pickerOn(date: LocalDate, calendar: CalendarSystem, today: LocalDate): WheelDatePickerState {
    val inCalendar = date.inCalendar(calendar)
    return WheelDatePickerState.forDate(
        RecurringDate(calendar, inCalendar.month, inCalendar.day, inCalendar.year),
        today,
        allowNoYear = false,
    )
}

private val IdListSaver: Saver<List<Long>, LongArray> = Saver(save = { it.toLongArray() }, restore = { it.toList() })

/** "+ Add": everyone who isn't Archived and isn't in the Interaction yet, searchable by name. */
@Composable
internal fun PeoplePickerSheet(candidates: List<PickablePerson>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        PeoplePickerContent(candidates, onPick = { id -> sheet.close { onPick(id) } })
    }
}

/**
 * The picker's inside: a search field over a fixed-height list (so typing and scrolling never
 * change the sheet's height). Tapping someone adds them and closes it.
 */
@Composable
internal fun PeoplePickerContent(candidates: List<PickablePerson>, onPick: (Long) -> Unit) {
    val language = LocalOrbitFormats.current.language
    val query = rememberTextFieldState()
    val sorted = remember(candidates, language) {
        sortByDisplayName(candidates, language, names = PickablePerson::names, tieBreak = PickablePerson::id)
    }
    val key = SearchText.normalise(query.text.toString()).trim()
    val shown = if (key.isEmpty()) sorted else sorted.filter { it.names.matches(key) }
    Column(Modifier.fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.interaction_people_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).semantics { heading() },
        )
        OutlinedTextField(
            state = query,
            label = { Text(stringResource(R.string.interaction_people_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = PickablePerson::id) { person ->
                val name = person.names.displayName(language)
                ListItem(
                    onClick = { onPick(person.id) },
                    leadingContent = { PersonAvatar(name = name, photo = person.photo) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                ) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.search_no_matches),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

/** Whether any of the names contains [key] (already through [SearchText.normalise]). */
private fun PersonNames.matches(key: String): Boolean =
    listOfNotNull(name, otherScriptName, nickname).any { SearchText.normalise(it).contains(key) }

@Composable
fun SnoozeSheet(state: SnoozeSheetState, onPick: (SnoozeDuration) -> Unit, onDismiss: () -> Unit) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        SnoozeSheetContent(state, onPick = { duration -> sheet.close { onPick(duration) } })
    }
}

/** Snooze: 3 days, 1 week or 1 month, each with the day it ends. Drawn by itself in screenshot tests. */
@Composable
internal fun SnoozeSheetContent(state: SnoozeSheetState, onPick: (SnoozeDuration) -> Unit) {
    val formats = LocalOrbitFormats.current
    val name = state.names.displayName(formats.language)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.snooze_title, bidiIsolate(name)),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp).semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SnoozeDuration.entries.forEachIndexed { index, duration ->
                val until = formats.dates.format(duration.endFrom(state.today), formats.primaryCalendar, state.today)
                SegmentedListItem(
                    onClick = { onPick(duration) },
                    shapes = ListItemDefaults.segmentedShapes(index, SnoozeDuration.entries.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    leadingContent = { Icon(OrbitIcons.Snooze, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(stringResource(R.string.snoozed_until, until)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(duration.text())
                }
            }
        }
    }
}

/** Who a quick-action sheet is for: what long-pressing them offers. */
data class QuickActionTarget(
    val personId: Long,
    val names: PersonNames,
    val photo: File?,
    val primaryContactMethod: ContactMethod?,
    val isDue: Boolean,
)

/**
 * The quick-action sheet, opened by long-pressing a Person anywhere (the People list now, the
 * Orbit view in slice 9): their primary contact action if they have one, Log interaction, and
 * Snooze only while they're Due.
 */
@Composable
fun QuickActionSheet(
    target: QuickActionTarget,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: (personId: Long) -> Unit,
    onSnooze: (personId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        QuickActionSheetContent(
            target = target,
            onContactAction = { action -> sheet.close { onContactAction(action) } },
            onLogInteraction = { sheet.close { onLogInteraction(target.personId) } },
            onSnooze = { sheet.close { onSnooze(target.personId) } },
        )
    }
}

/** The inside of [QuickActionSheet], drawn by itself in screenshot tests. */
@Composable
internal fun QuickActionSheetContent(
    target: QuickActionTarget,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: () -> Unit,
    onSnooze: () -> Unit,
) {
    val name = target.names.displayName(LocalOrbitFormats.current.language)
    val action = target.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
    Column(Modifier.padding(bottom = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        ) {
            PersonAvatar(name = name, photo = target.photo, size = 48.dp)
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (action != null) {
            QuickActionRow(action.kind.icon, action.kind.label()) { onContactAction(action) }
        }
        QuickActionRow(OrbitIcons.LogInteraction, stringResource(R.string.interaction_log), onLogInteraction)
        if (target.isDue) QuickActionRow(OrbitIcons.Snooze, stringResource(R.string.snooze_action), onSnooze)
    }
}

@Composable
private fun QuickActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(label)
    }
}
