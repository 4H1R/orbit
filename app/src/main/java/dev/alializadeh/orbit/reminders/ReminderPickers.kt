package dev.alializadeh.orbit.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.dates.leadTimeChipLabel
import dev.alializadeh.orbit.ui.datepicker.Wheel
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import java.time.LocalTime

/**
 * Multi-select lead-time chips, shortest first: On the day, 1 day, 3 days, 1 week, 2 weeks, 1
 * month, and with [onNoReminders] a last "No reminders" chip, selected exactly when nothing else
 * is. Disabled chips show [selected] without changing it (the date sheet's Default).
 */
@Composable
fun LeadTimeChips(
    selected: Set<LeadTime>,
    onToggle: (LeadTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onNoReminders: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LeadTime.entries.forEach { leadTime ->
            LeadChip(
                label = leadTimeChipLabel(leadTime),
                selected = leadTime in selected,
                enabled = enabled,
                onClick = { onToggle(leadTime) },
            )
        }
        if (onNoReminders != null) {
            LeadChip(
                label = stringResource(R.string.important_date_reminders_none),
                selected = selected.isEmpty(),
                enabled = enabled,
                onClick = onNoReminders,
            )
        }
    }
}

@Composable
private fun LeadChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/** The Reminder time sheet: hour and minute wheels (24-hour, Orbit's digits), Cancel and Done. */
@Composable
fun ReminderTimeSheet(initial: LocalTime, onDone: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        ReminderTimeSheetContent(initial, onDone, sheet)
    }
}

/** The inside of [ReminderTimeSheet], drawn by itself in screenshot tests. */
@Composable
internal fun ReminderTimeSheetContent(initial: LocalTime, onDone: (LocalTime) -> Unit, sheet: SheetCloser) {
    val numbers = LocalOrbitFormats.current.numbers
    var hour by rememberSaveable { mutableIntStateOf(initial.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.minute) }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(stringResource(R.string.reminders_time), style = MaterialTheme.typography.titleLarge)
        // A time reads hours then minutes left to right in Persian too.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(0.5f))
                Wheel(
                    count = HOURS,
                    selectedIndex = hour,
                    onSelect = { hour = it },
                    label = { numbers.twoDigits(it) },
                    description = stringResource(R.string.time_picker_hour),
                    modifier = Modifier.weight(1f),
                )
                Text(":", style = MaterialTheme.typography.titleLarge)
                Wheel(
                    count = MINUTES,
                    selectedIndex = minute,
                    onSelect = { minute = it },
                    label = { numbers.twoDigits(it) },
                    description = stringResource(R.string.time_picker_minute),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(0.5f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { sheet.close {} }) { Text(stringResource(R.string.common_cancel)) }
            Button(
                onClick = {
                    val time = LocalTime.of(hour, minute)
                    sheet.close { onDone(time) }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.common_done))
            }
        }
    }
}

private const val HOURS = 24
private const val MINUTES = 60
