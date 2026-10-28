package dev.alializadeh.orbit.person.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.due.DueState
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.interaction.InteractionListItem
import dev.alializadeh.orbit.interaction.InteractionWithPeople
import dev.alializadeh.orbit.interaction.cadenceLine
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.expressive.OrbitWavyProgress
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.time.LocalDate

/** What Person detail's Cadence line, button group and Interactions section do. */
interface PersonDetailKeepInTouch {
    fun logInteraction()
    fun openInteraction(interactionId: Long)
    fun allInteractions()
    fun snooze()
    fun cancelSnooze()
}

/**
 * The header's Cadence line: wavy progress through the Cadence (when there is one), then "Last
 * talked 34 days ago · every 30 days"; when Due, **Due** and Snooze; when Snoozed, "Snoozed until
 * 4 Nov" and Cancel snooze.
 */
@Composable
internal fun CadenceLine(
    due: DueState,
    today: LocalDate,
    onSnooze: () -> Unit,
    onCancelSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalOrbitFormats.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (due.cadence != null) {
            OrbitWavyProgress(
                progress = if (due.isDue) 1f else due.progress,
                modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 4.dp),
            )
        }
        Text(
            text = cadenceLine(due, today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val snoozedUntil = due.snoozedUntil
        when {
            due.isDue -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DueBadge()
                FilledTonalButton(onClick = onSnooze) {
                    Icon(OrbitIcons.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.snooze_action), modifier = Modifier.padding(start = 8.dp))
                }
            }
            snoozedUntil != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.snoozed_until,
                        formats.dates.format(snoozedUntil, formats.primaryCalendar, today),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onCancelSnooze) { Text(stringResource(R.string.snooze_cancel)) }
            }
        }
    }
}

/** "Due" in the primary colour: the People list's chip and Person detail's header. */
@Composable
fun DueBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.due_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Interactions, last among the sections and always shown: the latest few, then "All interactions
 * (n)" when there are more; with none, "No interactions yet" and Log interaction.
 */
@Composable
internal fun InteractionsSection(
    personId: Long,
    interactions: List<InteractionWithPeople>,
    today: LocalDate,
    onOpen: (interactionId: Long) -> Unit,
    onLog: () -> Unit,
    onAll: () -> Unit,
) {
    val numbers = LocalOrbitFormats.current.numbers
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    Column {
        OrbitSectionTitle(stringResource(R.string.person_section_interactions), horizontalPadding = 4.dp)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            if (interactions.isEmpty()) {
                SegmentedListItem(
                    onClick = onLog,
                    shapes = ListItemDefaults.segmentedShapes(0, 2),
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.no_interactions_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SegmentedListItem(
                    onClick = onLog,
                    shapes = ListItemDefaults.segmentedShapes(1, 2),
                    colors = colors,
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.interaction_log), color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val latest = interactions.take(PersonDetailUiState.LATEST_INTERACTIONS)
                val hasMore = interactions.size > latest.size
                val count = latest.size + if (hasMore) 1 else 0
                latest.forEachIndexed { index, item ->
                    InteractionListItem(
                        item = item,
                        personId = personId,
                        today = today,
                        index = index,
                        count = count,
                        onClick = { onOpen(item.interaction.id) },
                    )
                }
                if (hasMore) {
                    SegmentedListItem(
                        onClick = onAll,
                        shapes = ListItemDefaults.segmentedShapes(count - 1, count),
                        colors = colors,
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.interactions_all, numbers.format(interactions.size)),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
