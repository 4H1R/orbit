package dev.alializadeh.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single choice between a few short options as a connected button group (Expressive): the
 * Theme, the language, the Calendar. Announced as radio buttons.
 */
@Composable
fun <T> OrbitConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label(option), maxLines = 1)
            }
        }
    }
}

/** A titled group of segmented list items with the Expressive gaps between them. */
@Composable
fun OrbitListGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable OrbitListGroupScope.() -> Unit,
) {
    Column(modifier) {
        if (title != null) OrbitSectionTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            OrbitListGroupScope.content()
        }
    }
}

/** Marks [OrbitListGroup]'s content; items take their shape from their index and the count. */
object OrbitListGroupScope

/** A row of an [OrbitListGroup] that opens something: a title and a one-line summary. */
@Composable
fun OrbitListGroupScope.NavigationRow(
    title: String,
    summary: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        supportingContent = summary?.let { { Text(it, maxLines = 1) } },
    ) {
        Text(title)
    }
}

/** A row of an [OrbitListGroup] that is one option of a single choice, with a [summary] line. */
@Composable
fun OrbitListGroupScope.ChoiceRow(
    title: String,
    summary: String?,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.semantics { role = Role.RadioButton },
        leadingContent = leadingContent,
        supportingContent = summary?.let { { Text(it) } },
    ) {
        Text(title)
    }
}

/** A row of an [OrbitListGroup] with a switch. */
@Composable
fun OrbitListGroupScope.SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    index: Int,
    count: Int,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
) {
    SegmentedListItem(
        checked = checked,
        colors = colors,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) {
        Text(title)
    }
}

/** A row of an [OrbitListGroup] holding arbitrary content (a button group, swatches). */
@Composable
fun OrbitListGroupScope.ContentRow(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index, count).shape,
        color = ListItemDefaults.segmentedColors().containerColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

/** The small primary-coloured title above a group. */
@Composable
fun OrbitSectionTitle(text: String, modifier: Modifier = Modifier, horizontalPadding: Dp = 16.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = horizontalPadding, end = horizontalPadding, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}
