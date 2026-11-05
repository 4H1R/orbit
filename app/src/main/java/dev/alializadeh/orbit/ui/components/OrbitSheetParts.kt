package dev.alializadeh.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R

/*
 * The sheet anatomy every Orbit sheet follows: [OrbitSheetTitle] at the top start, the content,
 * then [OrbitSheetActions]: [destructive text button] … [Cancel] [the filled primary action].
 * Sheet content is padded 24dp at the sides (16dp for a sheet that is a list of rows).
 * [OrbitSheetActionRow] is the common Delete / Cancel / Save case of [OrbitSheetActions].
 */

/** A sheet's title: headlineSmall, a heading, at the top start. */
@Composable
fun OrbitSheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.semantics { heading() },
    )
}

/** The small label over a part of a sheet ("When", "Return by", "Reminders"). */
@Composable
fun OrbitSheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * A sheet's bottom action row: [destructive] (an [OrbitSheetDestructiveButton]) at the start, then
 * [actions] at the end, Cancel before the filled primary action. Wraps onto a second line, never
 * clipping a label, when a large font size makes them too wide.
 */
@Composable
fun OrbitSheetActions(
    modifier: Modifier = Modifier,
    destructive: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    FlowRow(
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        if (destructive != null) destructive()
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            content = actions,
        )
    }
}

/** The error-coloured text button at the start of [OrbitSheetActions] (Delete, Remove ring). */
@Composable
fun OrbitSheetDestructiveButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(text)
    }
}

/**
 * The usual edit-sheet row: [destructiveLabel] (Delete) at the start when given, then Cancel and
 * the filled [confirmLabel]. Built on [OrbitSheetActions], so it wraps the same way.
 */
@Composable
fun OrbitSheetActionRow(
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    destructiveLabel: String? = null,
    onDestructive: (() -> Unit)? = null,
) {
    OrbitSheetActions(
        modifier = modifier,
        destructive = if (destructiveLabel != null && onDestructive != null) {
            { OrbitSheetDestructiveButton(destructiveLabel, onDestructive) }
        } else {
            null
        },
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
        Button(enabled = confirmEnabled, onClick = onConfirm) { Text(confirmLabel) }
    }
}
