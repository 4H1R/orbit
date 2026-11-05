package dev.alializadeh.orbit.person.followups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.FollowUp
import dev.alializadeh.orbit.person.FollowUpDraft
import dev.alializadeh.orbit.person.dates.rememberFutureDateChoice
import dev.alializadeh.orbit.ui.components.OrbitSheetActionRow
import dev.alializadeh.orbit.ui.components.OrbitSheetLabel
import dev.alializadeh.orbit.ui.components.OrbitSheetTitle
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import java.time.LocalDate

/**
 * The Follow-up sheet: what to follow up on, and when. Reopened on an existing one ([initial]) it
 * also has Delete, and Mark as done while it's open.
 */
@Composable
fun FollowUpSheet(
    initial: FollowUp?,
    today: LocalDate,
    onSave: (FollowUpDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onMarkDone: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        FollowUpSheetContent(initial, today, onSave, onDelete, onMarkDone, sheet = sheet)
    }
}

/** The inside of [FollowUpSheet], drawn by itself in screenshot tests. */
@Composable
internal fun FollowUpSheetContent(
    initial: FollowUp?,
    today: LocalDate,
    onSave: (FollowUpDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onMarkDone: (() -> Unit)?,
    sheet: SheetCloser,
) {
    val text = rememberTextFieldState(initial?.text.orEmpty())
    val day = rememberFutureDateChoice(initial?.dueOn, today, allowNone = false)
    val dueOn = day.date
    val canSave = text.text.isNotBlank() && day.isValid && dueOn != null

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        OrbitSheetTitle(stringResource(if (initial == null) R.string.follow_up_sheet_new else R.string.follow_up_sheet_edit))
        // Its state first: marking it done is what reopening an open one is most often for.
        val doneOn = initial?.doneOn
        if (doneOn != null) {
            val formats = LocalOrbitFormats.current
            Text(
                text = stringResource(R.string.follow_up_done_on, formats.dates.format(doneOn, formats.primaryCalendar, today)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onMarkDone != null) {
            FilledTonalButton(onClick = { sheet.close(onMarkDone) }) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                Text(stringResource(R.string.follow_up_mark_done))
            }
        }
        OutlinedTextField(
            state = text,
            label = { Text(stringResource(R.string.follow_up_text)) },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OrbitSheetLabel(stringResource(R.string.follow_up_date), Modifier.padding(top = 4.dp))
        day.Field()
        OrbitSheetActionRow(
            confirmLabel = stringResource(R.string.common_save),
            confirmEnabled = canSave,
            onConfirm = {
                if (dueOn != null) {
                    val draft = FollowUpDraft(initial?.id, text.text.toString(), dueOn).normalised()
                    sheet.close { onSave(draft) }
                }
            },
            onCancel = { sheet.close {} },
            destructiveLabel = stringResource(R.string.common_delete),
            onDestructive = onDelete?.let { delete -> { sheet.close(delete) } },
        )
    }
}
