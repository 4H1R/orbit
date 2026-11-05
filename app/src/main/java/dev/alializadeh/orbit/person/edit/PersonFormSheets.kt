package dev.alializadeh.orbit.person.edit

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
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
import dev.alializadeh.orbit.ui.components.OrbitSheetActionRow
import dev.alializadeh.orbit.ui.components.OrbitSheetTitle
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.icons.OrbitIcons

/** Choose photo, plus Remove photo when there is one. */
@Composable
internal fun PhotoSheet(hasPhoto: Boolean, onChoose: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        OrbitSheetTitle(stringResource(R.string.person_field_photo), Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
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
        OrbitSheetTitle(stringResource(R.string.person_field_cadence), Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp))
        OrbitListGroup(title = null, modifier = Modifier.padding(horizontal = 16.dp)) {
            ChoiceRow(
                title = stringResource(R.string.cadence_sheet_use_ring),
                summary = sentenceStart(cadenceText(ringCadence)),
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
        // Always there, so the sheet keeps its height; dimmed until Every is chosen, and choosing a
        // unit or typing a number chooses it.
        CadenceCountUnitFields(
            count = count,
            unit = unit,
            onUnitSelect = {
                unit = it
                choice = CadenceChoice.Every
            },
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .alpha(if (choice == CadenceChoice.Every) 1f else DIMMED)
                .onFocusChanged { if (it.hasFocus) choice = CadenceChoice.Every },
        )
        OrbitSheetActionRow(
            confirmLabel = stringResource(R.string.common_done),
            confirmEnabled = choice != CadenceChoice.Every || custom != null,
            onConfirm = {
                val setting = when (choice) {
                    CadenceChoice.Ring -> CadenceSetting.FromRing
                    CadenceChoice.Every -> CadenceSetting.Own(custom)
                    CadenceChoice.None -> CadenceSetting.Own(null)
                }
                sheet.close { onPick(setting) }
            },
            onCancel = { sheet.close {} },
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        )
    }
}

/** The opacity of the Cadence fields while another choice is selected. */
private const val DIMMED = 0.38f

private const val DEFAULT_DAYS = 30
