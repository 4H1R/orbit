package dev.alializadeh.orbit.person.dates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.ImportantDateDraft
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.reminders.LeadTimeChips
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import dev.alializadeh.orbit.ui.datepicker.WheelDatePicker
import dev.alializadeh.orbit.ui.datepicker.WheelDatePickerState
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.OrbitShapes
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import java.time.LocalDate

/**
 * The Important date sheet, from the Add/Edit form and Person detail: Type (Birthday hidden once
 * the Person has another date that's their Birthday; Custom adds a label), the wheel picker with
 * its Calendar toggle, the Reminders line, Done, and Delete when editing ([onDelete]).
 *
 * The picker starts on the Primary calendar for a new date and on the date's own Calendar when
 * editing; whatever Calendar it shows on Done is the date's Calendar. Reminders are Default (the
 * default lead times, shown as disabled chips) or Custom: the lead-time chips plus No reminders.
 */
@Composable
fun ImportantDateSheet(
    initial: ImportantDateDraft?,
    newType: ImportantDateType,
    birthdayTaken: Boolean,
    defaultLeadTimes: Set<LeadTime>,
    today: LocalDate,
    onDone: (ImportantDateDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        ImportantDateSheetContent(initial, newType, birthdayTaken, defaultLeadTimes, today, onDone, onDelete, sheet = sheet)
    }
}

/** The inside of [ImportantDateSheet], drawn by itself in screenshot tests. */
@Composable
internal fun ImportantDateSheetContent(
    /** The date being edited, or null for a new one of [newType]. */
    initial: ImportantDateDraft?,
    newType: ImportantDateType,
    /** Another date of this Person is their Birthday, so Birthday isn't offered. */
    birthdayTaken: Boolean,
    defaultLeadTimes: Set<LeadTime>,
    today: LocalDate,
    onDone: (ImportantDateDraft) -> Unit,
    onDelete: (() -> Unit)?,
    sheet: SheetCloser,
) {
    val primaryCalendar = LocalOrbitFormats.current.primaryCalendar
    val types = ImportantDateType.entries.filter { it != ImportantDateType.Birthday || !birthdayTaken }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: newType) }
    val label = rememberTextFieldState(initial?.label.orEmpty())
    var picker by rememberSaveable(stateSaver = WheelDatePickerStateSaver) {
        mutableStateOf(
            if (initial != null) {
                WheelDatePickerState.forDate(initial.date, today, allowNoYear = true)
            } else {
                WheelDatePickerState.forNewDate(primaryCalendar, today, allowNoYear = true)
            },
        )
    }
    // Null: the default lead times; empty: No reminders.
    var leadTimes by rememberSaveable(stateSaver = LeadTimesSaver) { mutableStateOf(initial?.leadTimes) }
    val canSave = type != ImportantDateType.Custom || label.text.isNotBlank()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(if (initial == null) R.string.important_date_sheet_new else R.string.important_date_sheet_edit),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        val typeLabel = stringResource(R.string.important_date_type)
        OrbitConnectedChoice(
            options = types,
            selected = type,
            onSelect = { type = it },
            label = { it.displayName() },
            modifier = Modifier.semantics { contentDescription = typeLabel },
        )
        if (type == ImportantDateType.Custom) {
            OutlinedTextField(
                state = label,
                label = { Text(stringResource(R.string.important_date_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        WheelDatePicker(state = picker, onStateChange = { picker = it }, calendars = CalendarSystem.entries)
        RemindersChoice(leadTimes = leadTimes, defaultLeadTimes = defaultLeadTimes, onChange = { leadTimes = it })
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (onDelete != null) {
                TextButton(
                    onClick = { sheet.close(onDelete) },
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
                    val draft = ImportantDateDraft(
                        id = initial?.id,
                        type = type,
                        label = label.text.toString(),
                        date = picker.date,
                        leadTimes = leadTimes,
                    ).normalised()
                    sheet.close { onDone(draft) }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.common_done))
            }
        }
    }
}

/**
 * Reminders: Default or Custom. Default shows the default lead times as disabled chips; Custom
 * starts from them and makes the chips, with No reminders, editable. The chips are always there,
 * so the sheet never changes height (a sheet that resizes after a wheel drag gets stuck).
 */
@Composable
private fun RemindersChoice(leadTimes: Set<LeadTime>?, defaultLeadTimes: Set<LeadTime>, onChange: (Set<LeadTime>?) -> Unit) {
    val label = stringResource(R.string.important_date_reminders)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            OrbitConnectedChoice(
                options = listOf(false, true),
                selected = leadTimes != null,
                onSelect = { custom -> onChange(if (custom) leadTimes ?: defaultLeadTimes else null) },
                label = { custom ->
                    stringResource(
                        if (custom) R.string.important_date_reminders_mode_custom else R.string.important_date_reminders_mode_default,
                    )
                },
                modifier = Modifier.semantics { contentDescription = label },
            )
            LeadTimeChips(
                selected = leadTimes ?: defaultLeadTimes,
                enabled = leadTimes != null,
                onToggle = { lead ->
                    val current = leadTimes.orEmpty()
                    onChange(if (lead in current) current - lead else current + lead)
                },
                onNoReminders = { onChange(emptySet()) },
            )
        }
    }
}

/** A date's lead times across configuration changes: "default" for null, else the names. */
private val LeadTimesSaver: Saver<Set<LeadTime>?, String> = Saver(
    save = { leadTimes -> leadTimes?.joinToString(",") { it.name } ?: DEFAULT_LEAD_TIMES },
    restore = { saved ->
        if (saved == DEFAULT_LEAD_TIMES) null else saved.split(',').filter(String::isNotEmpty).map(LeadTime::valueOf).toSet()
    },
)

private const val DEFAULT_LEAD_TIMES = "default"
/** Keeps a picker's selection across configuration changes and process death. */
val WheelDatePickerStateSaver: Saver<WheelDatePickerState, Any> = listSaver(
    save = {
        listOf(it.calendar.name, it.month, it.day, it.year ?: NO_YEAR, it.today.toEpochDay(), it.allowNoYear, it.convertedViaYear ?: NO_YEAR)
    },
    restore = {
        WheelDatePickerState(
            calendar = CalendarSystem.valueOf(it[0] as String),
            month = it[1] as Int,
            day = it[2] as Int,
            year = (it[3] as Int).takeIf { year -> year != NO_YEAR },
            today = LocalDate.ofEpochDay(it[4] as Long),
            allowNoYear = it[5] as Boolean,
            convertedViaYear = (it[6] as Int).takeIf { year -> year != NO_YEAR },
        )
    },
)

private const val NO_YEAR = -1

/**
 * An apricot badge in a MaterialShapes cookie: a date's day over its month (Coming up), or with
 * [content] anything else (a type icon on Person detail).
 */
@Composable
fun DateBadge(day: String, month: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    DateBadgeBox(modifier, size) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            BasicText(
                text = month,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** The badge with a type icon (cake, heart, calendar). */
@Composable
fun DateTypeBadge(type: ImportantDateType, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    DateBadgeBox(modifier, size) {
        Icon(type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(size / 2))
    }
}

@Composable
private fun DateBadgeBox(modifier: Modifier, size: Dp, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(OrbitShapes.dateBadge())
            .background(MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        content()
    }
}
