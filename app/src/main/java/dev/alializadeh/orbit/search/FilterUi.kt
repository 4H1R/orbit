package dev.alializadeh.orbit.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.nameComparator
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser

/**
 * The filter chips, scrolling sideways: [leading] chips (the People list's Sort), Ring ▾, Tags ▾,
 * Due, Date soon, then [trailing] ones (Archived). The Orbit view (slice 9) uses it without the
 * extras, over its own [PeopleFilterState].
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
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val resources = LocalResources.current
    val numbers = LocalOrbitFormats.current.numbers
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
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
        trailing()
    }
}

/** A filter chip that opens a sheet (Ring ▾, Tags ▾). */
@Composable
private fun MenuFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

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

/** "Filtered · Clear", between the chips and the list while any filter is on. */
@Composable
fun FilteredBar(onClear: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.filter_bar_filtered),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.filter_bar_clear)) }
        }
    }
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
 * A multi-select sheet: a title with Clear, the options with checkboxes (a fixed maximum height, so
 * a long list scrolls inside), and Done.
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                TextButton(onClick = { onChange(emptySet()) }) { Text(stringResource(R.string.filter_bar_clear)) }
            }
        }
        if (options.isEmpty() && empty != null) {
            Text(
                text = empty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
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
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        TextButton(
            onClick = { sheet.close {} },
            modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
        ) {
            Text(stringResource(R.string.common_done))
        }
    }
}
