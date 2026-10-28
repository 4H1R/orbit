package dev.alializadeh.orbit.person.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.CadenceSetting
import dev.alializadeh.orbit.person.ui.CadenceCountUnitFields
import dev.alializadeh.orbit.person.ui.cadenceOf
import dev.alializadeh.orbit.person.ui.cadenceText
import dev.alializadeh.orbit.person.ui.sentenceStart
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ui.components.ChoiceRow
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
    val custom = cadenceOf(count, unit)

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
            CadenceCountUnitFields(
                count = count,
                unit = unit,
                onUnitSelect = { unit = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
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

private const val DEFAULT_DAYS = 30
