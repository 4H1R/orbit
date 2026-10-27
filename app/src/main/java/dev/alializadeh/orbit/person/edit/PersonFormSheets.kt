package dev.alializadeh.orbit.person.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.Digits
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.CadenceSetting
import dev.alializadeh.orbit.person.ui.cadenceText
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.CadenceUnit
import dev.alializadeh.orbit.ui.components.ChoiceRow
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.icons.OrbitIcons

/** Choose photo, plus Remove photo when there is one. */
@Composable
internal fun PhotoSheet(hasPhoto: Boolean, onChoose: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        ListItem(onClick = { sheet.close(onChoose) }, leadingContent = { Icon(OrbitIcons.Photo, null) }) {
            Text(stringResource(R.string.photo_choose))
        }
        if (hasPhoto) {
            ListItem(onClick = { sheet.close(onRemove) }, leadingContent = { Icon(Icons.Filled.Delete, null) }) {
                Text(stringResource(R.string.photo_remove))
            }
        }
    }
}

private enum class CadenceChoice { Ring, Every, None }

/** Use ring default / Every N days, weeks or months / No cadence. */
@Composable
internal fun CadenceSheet(
    current: CadenceSetting,
    ringCadence: Cadence?,
    onPick: (CadenceSetting) -> Unit,
    onDismiss: () -> Unit,
) {
    val numbers = LocalOrbitFormats.current.numbers
    val start = (current as? CadenceSetting.Own)?.cadence ?: ringCadence ?: Cadence.days(DEFAULT_DAYS)
    var choice by rememberSaveable {
        mutableStateOf(
            when (current) {
                CadenceSetting.FromRing -> CadenceChoice.Ring
                is CadenceSetting.Own -> if (current.cadence == null) CadenceChoice.None else CadenceChoice.Every
            },
        )
    }
    var unit by rememberSaveable { mutableStateOf(start.unit) }
    val count = rememberTextFieldState(numbers.format(start.count))
    val parsed = Digits.toAscii(count.text.toString()).trim().toIntOrNull()?.takeIf { it in 1..MAX_COUNT }
    val custom = parsed?.let { Cadence(it, unit) }

    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        Text(
            text = stringResource(R.string.person_field_cadence),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        OrbitListGroup(title = null, modifier = Modifier.padding(horizontal = 16.dp)) {
            ChoiceRow(
                title = stringResource(R.string.cadence_sheet_use_ring),
                summary = cadenceText(ringCadence),
                selected = choice == CadenceChoice.Ring,
                index = 0,
                count = 3,
                onClick = { choice = CadenceChoice.Ring },
            )
            ChoiceRow(
                title = sentenceStart(cadenceText(custom ?: Cadence(1, unit))),
                summary = null,
                selected = choice == CadenceChoice.Every,
                index = 1,
                count = 3,
                onClick = { choice = CadenceChoice.Every },
            )
            ChoiceRow(
                title = stringResource(R.string.cadence_none),
                summary = null,
                selected = choice == CadenceChoice.None,
                index = 2,
                count = 3,
                onClick = { choice = CadenceChoice.None },
            )
        }
        if (choice == CadenceChoice.Every) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                OutlinedTextField(
                    state = count,
                    label = { Text(stringResource(R.string.cadence_sheet_count)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = InputTransformation.maxLength(3),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = parsed == null,
                    modifier = Modifier.width(96.dp),
                )
                OrbitConnectedChoice(
                    options = CadenceUnit.entries,
                    selected = unit,
                    onSelect = { unit = it },
                    label = { unitName(it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            TextButton(onClick = { sheet.close {} }) { Text(stringResource(R.string.common_cancel)) }
            Button(
                enabled = choice != CadenceChoice.Every || custom != null,
                onClick = {
                    val setting = when (choice) {
                        CadenceChoice.Ring -> CadenceSetting.FromRing
                        CadenceChoice.Every -> CadenceSetting.Own(custom)
                        CadenceChoice.None -> CadenceSetting.Own(null)
                    }
                    sheet.close { onPick(setting) }
                },
            ) {
                Text(stringResource(R.string.common_done))
            }
        }
    }
}

@Composable
private fun unitName(unit: CadenceUnit): String = stringResource(
    when (unit) {
        CadenceUnit.Days -> R.string.cadence_unit_days
        CadenceUnit.Weeks -> R.string.cadence_unit_weeks
        CadenceUnit.Months -> R.string.cadence_unit_months
    },
)

/** "every 3 weeks" → "Every 3 weeks" as a row title (Persian has no case, so it's unchanged). */
private fun sentenceStart(text: String): String = text.replaceFirstChar { it.titlecase() }

private const val DEFAULT_DAYS = 30
private const val MAX_COUNT = 999
