package dev.alializadeh.orbit.person.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.Digits
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.CadenceUnit
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice

/** "Every [count] [Days | Weeks | Months]": the number field and the unit group of a Cadence. */
@Composable
fun CadenceCountUnitFields(
    count: TextFieldState,
    unit: CadenceUnit,
    onUnitSelect: (CadenceUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        OutlinedTextField(
            state = count,
            label = { Text(stringResource(R.string.cadence_sheet_count)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation.maxLength(3),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = cadenceCount(count) == null,
            modifier = Modifier.width(96.dp),
        )
        OrbitConnectedChoice(
            options = CadenceUnit.entries,
            selected = unit,
            onSelect = onUnitSelect,
            label = { cadenceUnitName(it) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** The count typed in [count] (Persian digits too), or null unless it's 1 to 999. */
fun cadenceCount(count: TextFieldState): Int? =
    Digits.toAscii(count.text.toString()).trim().toIntOrNull()?.takeIf { it in 1..MAX_CADENCE_COUNT }

/** The Cadence typed in [count] with [unit], or null while the count isn't valid. */
fun cadenceOf(count: TextFieldState, unit: CadenceUnit): Cadence? = cadenceCount(count)?.let { Cadence(it, unit) }

@Composable
fun cadenceUnitName(unit: CadenceUnit): String = stringResource(
    when (unit) {
        CadenceUnit.Days -> R.string.cadence_unit_days
        CadenceUnit.Weeks -> R.string.cadence_unit_weeks
        CadenceUnit.Months -> R.string.cadence_unit_months
    },
)

/** "every 3 weeks" → "Every 3 weeks" as a row title (Persian has no case, so it's unchanged). */
fun sentenceStart(text: String): String = text.replaceFirstChar { it.titlecase() }

private const val MAX_CADENCE_COUNT = 999
