package dev.alializadeh.orbit.search

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.nameComparator
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser

/**
 * The filter row the People list and the Orbit view share, each over its own [PeopleFilterState]:
 * Ring ▾, Tags ▾, Due, Date soon. A picked Ring or Tag names itself in its chip ("Close", "2 tags").
 * The row scrolls sideways when it doesn't fit, fading at the edge that has more.
 */
@Composable
fun PeopleFilterChips(
    filters: PeopleFilters,
    rings: List<Ring>,
    tags: List<Tag>,
    onOpenRings: () -> Unit,
    onOpenTags: () -> Unit,
    onToggleDue: () -> Unit,
    onToggleDateSoon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val numbers = LocalOrbitFormats.current.numbers
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fadingEdges(scroll)
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ringLabel = when (filters.ringIds.size) {
            0 -> stringResource(R.string.people_filter_ring)
            1 -> rings.firstOrNull { it.id in filters.ringIds }?.name.orEmpty()
            else -> resources.getQuantityString(R.plurals.people_filter_rings_count, filters.ringIds.size, numbers.format(filters.ringIds.size))
        }
        MenuFilterChip(label = ringLabel, selected = filters.ringIds.isNotEmpty(), onClick = onOpenRings)
        val tagLabel = when (filters.tagIds.size) {
            0 -> stringResource(R.string.people_filter_tags)
            1 -> tags.firstOrNull { it.id in filters.tagIds }?.name.orEmpty()
            else -> resources.getQuantityString(R.plurals.people_filter_tags_count, filters.tagIds.size, numbers.format(filters.tagIds.size))
        }
        MenuFilterChip(label = tagLabel, selected = filters.tagIds.isNotEmpty(), onClick = onOpenTags)
        ToggleFilterChip(stringResource(R.string.due_label), filters.due, onToggleDue)
        ToggleFilterChip(stringResource(R.string.people_filter_date_soon), filters.dateSoon, onToggleDateSoon)
    }
}

/** Fades the content out towards whichever side of [scroll] has more to scroll to. */
private fun Modifier.fadingEdges(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FadeWidth.toPx().coerceAtMost(size.width / 4)
        // The scroll runs from the start edge: the left in LTR, the right in RTL.
        val ltr = layoutDirection == LayoutDirection.Ltr
        val towardsStart = scroll.value > 0
        val towardsEnd = scroll.value < scroll.maxValue
        val left = if (ltr) towardsStart else towardsEnd
        val right = if (ltr) towardsEnd else towardsStart
        if (left) {
            drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = fade),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (right) {
            drawRect(
                Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - fade, endX = size.width),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private val FadeWidth = 32.dp

/** A filter chip that opens a sheet (Ring ▾, Tags ▾). */
@Composable
private fun MenuFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = ChipLabelMaxWidth)) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

/** A Ring or Tag name in a chip may be long; the sheet shows it in full. */
private val ChipLabelMaxWidth = 160.dp

/** An on/off filter chip (Due, Date soon), with a check while on. */
@Composable
private fun ToggleFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/**
 * The line between the filter row and what it filters: [summary] ("24 people", "3 of 24 people",
 * "6 results", "No one matches"), Clear while [onClear] is given (any filter on), and the screen's
 * own [trailing] control (the People list's Sort). It keeps its height whatever it says, so the
 * list or drawing under it doesn't jump, and TalkBack hears the new count as filters change.
 */
@Composable
fun FilterSummaryRow(
    summary: String,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 20.dp, end = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (onClear != null) {
                val description = stringResource(R.string.search_clear_filters)
                TextButton(onClick = onClear, modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = description }) {
                    Text(stringResource(R.string.filter_bar_clear))
                }
            }
        }
        trailing()
    }
}

/** "24 people", or "3 of 24 people" while filtering: what the filter row leaves in. */
@Composable
fun filterSummary(shown: Int, total: Int, filtering: Boolean): String {
    val resources = LocalResources.current
    val numbers = LocalOrbitFormats.current.numbers
    val everyone = resources.getQuantityString(R.plurals.people_count, total, numbers.format(total))
    return if (filtering) resources.getQuantityString(R.plurals.people_count_of, shown, numbers.format(shown), everyone) else everyone
}

/** The Ring filter: tick any Rings, innermost first; the list follows each tick. */
@Composable
fun RingFilterSheet(rings: List<Ring>, selected: Set<Long>, onChange: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        RingFilterSheetContent(rings, selected, onChange, sheet)
    }
}

@Composable
fun RingFilterSheetContent(rings: List<Ring>, selected: Set<Long>, onChange: (Set<Long>) -> Unit, sheet: SheetCloser) {
    FilterSheetContent(
        title = stringResource(R.string.people_filter_ring_title),
        options = rings.sortedBy(Ring::position).map { it.id to it.name },
        selected = selected,
        empty = null,
        onChange = onChange,
        sheet = sheet,
    )
}

/** The Tags filter: tick any Tags (People with any of them), by name. */
@Composable
fun TagFilterSheet(tags: List<Tag>, selected: Set<Long>, onChange: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        TagFilterSheetContent(tags, selected, onChange, sheet)
    }
}

@Composable
fun TagFilterSheetContent(tags: List<Tag>, selected: Set<Long>, onChange: (Set<Long>) -> Unit, sheet: SheetCloser) {
    val language = LocalOrbitFormats.current.language
    val sorted = remember(tags, language) {
        val comparator = nameComparator(language)
        tags.sortedWith { a, b -> comparator.compare(a.name, b.name) }
    }
    FilterSheetContent(
        title = stringResource(R.string.people_filter_tags_title),
        options = sorted.map { it.id to it.name },
        selected = selected,
        empty = stringResource(R.string.tags_empty),
        onChange = onChange,
        sheet = sheet,
    )
}

/**
 * A multi-select sheet: the title, the options with checkboxes (a fixed maximum height, so a long
 * list scrolls inside and the sheet keeps its height), then Clear … Done.
 */
@Composable
private fun FilterSheetContent(
    title: String,
    options: List<Pair<Long, String>>,
    selected: Set<Long>,
    empty: String?,
    onChange: (Set<Long>) -> Unit,
    sheet: SheetCloser,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp).semantics { heading() },
        )
        if (options.isEmpty() && empty != null) {
            Text(
                text = empty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(options, key = { it.first }) { (id, name) ->
                val checked = id in selected
                val toggle = { onChange(if (checked) selected - id else selected + id) }
                ListItem(
                    onClick = toggle,
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(name)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp),
        ) {
            // Kept in place (only disabled) with nothing ticked, so the buttons don't move.
            TextButton(onClick = { onChange(emptySet()) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.filter_bar_clear))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { sheet.close {} }) { Text(stringResource(R.string.common_done)) }
        }
    }
}
