package dev.alializadeh.orbit.person.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.CalendarDate
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.GiftIdea
import dev.alializadeh.orbit.person.GiftIdeaDraft
import dev.alializadeh.orbit.person.dates.WheelDatePickerStateSaver
import dev.alializadeh.orbit.ui.datepicker.WheelDatePicker
import dev.alializadeh.orbit.ui.datepicker.WheelDatePickerState
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.time.LocalDate

/**
 * The Gift idea sheet: text, an optional link and an optional note. Reopened on an existing idea
 * ([initial]) it also has Delete, and Mark as given while it's still an idea.
 */
@Composable
fun GiftIdeaSheet(
    initial: GiftIdea?,
    today: LocalDate,
    onSave: (GiftIdeaDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onMarkGiven: ((GiftIdeaDraft) -> Unit)?,
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        GiftIdeaSheetContent(initial, today, onSave, onDelete, onMarkGiven, onOpenLink, sheet = sheet)
    }
}

/** The inside of [GiftIdeaSheet], drawn by itself in screenshot tests. */
@Composable
internal fun GiftIdeaSheetContent(
    initial: GiftIdea?,
    today: LocalDate,
    onSave: (GiftIdeaDraft) -> Unit,
    onDelete: (() -> Unit)?,
    /** Saves the sheet's text, then asks for the given date. Null for new or given ideas. */
    onMarkGiven: ((GiftIdeaDraft) -> Unit)?,
    onOpenLink: (String) -> Unit,
    sheet: SheetCloser,
) {
    val text = rememberTextFieldState(initial?.text.orEmpty())
    val link = rememberTextFieldState(initial?.link.orEmpty())
    val note = rememberTextFieldState(initial?.note.orEmpty())
    fun draft() = GiftIdeaDraft(initial?.id, text.text.toString(), link.text.toString(), note.text.toString()).normalised()
    val canSave = text.text.isNotBlank()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(if (initial == null) R.string.gift_idea_sheet_new else R.string.gift_idea_sheet_edit),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            state = text,
            label = { Text(stringResource(R.string.gift_idea_text)) },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state = link,
            label = { Text(stringResource(R.string.gift_idea_link)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            // Links read left to right in both languages.
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            trailingIcon = if (link.text.isNotBlank()) {
                {
                    IconButton(onClick = { onOpenLink(link.text.toString().trim()) }) {
                        Icon(OrbitIcons.OpenInNew, contentDescription = stringResource(R.string.gift_idea_open_link))
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state = note,
            label = { Text(stringResource(R.string.gift_idea_note)) },
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        val givenOn = initial?.givenOn
        if (givenOn != null) {
            val formats = LocalOrbitFormats.current
            Text(
                text = stringResource(R.string.gift_idea_given_on, formats.dates.format(givenOn, formats.primaryCalendar, today)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onMarkGiven != null) {
            OutlinedButton(
                enabled = canSave,
                onClick = {
                    val draft = draft()
                    sheet.close { onMarkGiven(draft) }
                },
            ) {
                Icon(OrbitIcons.Gift, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.gift_idea_mark_given))
            }
        }
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
                    val draft = draft()
                    sheet.close { onSave(draft) }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}

/**
 * Mark as given: when it was given, Today unless another day is picked on the wheel picker (with
 * a year, and not after today).
 */
@Composable
fun GivenDateSheet(today: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        GivenDateSheetContent(today, onConfirm, sheet = sheet)
    }
}

/** The inside of [GivenDateSheet], drawn by itself in screenshot tests. */
@Composable
internal fun GivenDateSheetContent(today: LocalDate, onConfirm: (LocalDate) -> Unit, sheet: SheetCloser) {
    val primaryCalendar = LocalOrbitFormats.current.primaryCalendar
    var picking by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable(stateSaver = WheelDatePickerStateSaver) {
        mutableStateOf(WheelDatePickerState.forNewDate(primaryCalendar, today, allowNoYear = false))
    }
    val picked = picker.date.let { CalendarDate(it.calendar, it.year!!, it.month, it.day).toLocalDate() }
    val date = if (picking) picked else today

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(stringResource(R.string.gift_idea_given_title), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !picking,
                onClick = { picking = false },
                label = { Text(stringResource(R.string.time_today)) },
            )
            FilterChip(
                selected = picking,
                onClick = { picking = true },
                label = { Text(stringResource(R.string.gift_idea_date_pick)) },
            )
        }
        if (picking) WheelDatePicker(state = picker, onStateChange = { picker = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { sheet.close {} }) { Text(stringResource(R.string.common_cancel)) }
            Button(enabled = !date.isAfter(today), onClick = { sheet.close { onConfirm(date) } }) {
                Text(stringResource(R.string.gift_idea_mark_given))
            }
        }
    }
}
