package dev.alializadeh.orbit.person.loans

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.Loan
import dev.alializadeh.orbit.person.LoanDirection
import dev.alializadeh.orbit.person.LoanDraft
import dev.alializadeh.orbit.person.dates.rememberFutureDateChoice
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import java.time.LocalDate

/** "I lent" or "I borrowed". */
@Composable
internal fun LoanDirection.displayName(): String = stringResource(
    when (this) {
        LoanDirection.Lent -> R.string.loan_direction_lent
        LoanDirection.Borrowed -> R.string.loan_direction_borrowed
    },
)

/**
 * The Loan sheet: which way it went, what it is, and an optional day to return it by. Reopened on an
 * existing one ([initial]) it also has Delete, and Mark as returned while it's open.
 */
@Composable
fun LoanSheet(
    initial: Loan?,
    today: LocalDate,
    onSave: (LoanDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onMarkReturned: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        LoanSheetContent(initial, today, onSave, onDelete, onMarkReturned, sheet = sheet)
    }
}

/** The inside of [LoanSheet], drawn by itself in screenshot tests. */
@Composable
internal fun LoanSheetContent(
    initial: Loan?,
    today: LocalDate,
    onSave: (LoanDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onMarkReturned: (() -> Unit)?,
    sheet: SheetCloser,
) {
    var direction by rememberSaveable { mutableStateOf(initial?.direction ?: LoanDirection.Lent) }
    val text = rememberTextFieldState(initial?.text.orEmpty())
    val day = rememberFutureDateChoice(initial?.returnBy, today, allowNone = true)
    val canSave = text.text.isNotBlank() && day.isValid

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(if (initial == null) R.string.loan_sheet_new else R.string.loan_sheet_edit),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        OrbitConnectedChoice(
            options = LoanDirection.entries,
            selected = direction,
            onSelect = { direction = it },
            label = { it.displayName() },
        )
        OutlinedTextField(
            state = text,
            label = { Text(stringResource(R.string.loan_text)) },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.loan_return_by),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        day.Field()
        val returnedOn = initial?.returnedOn
        if (returnedOn != null) {
            val formats = LocalOrbitFormats.current
            Text(
                text = stringResource(R.string.loan_returned_on, formats.dates.format(returnedOn, formats.primaryCalendar, today)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onMarkReturned != null) {
            OutlinedButton(onClick = { sheet.close(onMarkReturned) }) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.loan_mark_returned))
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
                    val draft = LoanDraft(initial?.id, direction, text.text.toString(), day.date).normalised()
                    sheet.close { onSave(draft) }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}
