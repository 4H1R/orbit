package dev.alializadeh.orbit.person.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.due.DueState
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.interaction.InteractionListItem
import dev.alializadeh.orbit.interaction.InteractionWithPeople
import dev.alializadeh.orbit.interaction.lastTalkedText
import dev.alializadeh.orbit.person.PersonProfile
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.ui.cadenceText
import dev.alializadeh.orbit.person.ui.icon
import dev.alializadeh.orbit.person.ui.label
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.expressive.OrbitWavyProgress
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.time.LocalDate

/** What Person detail's keep-in-touch card and Interactions section do. */
interface PersonDetailKeepInTouch {
    fun logInteraction()
    fun openInteraction(interactionId: Long)
    fun allInteractions()
    fun snooze()
    fun cancelSnooze()
}

/**
 * The keep-in-touch card, the page's one answer to "how are we doing": "Last talked 12 days ago"
 * (or "No interactions yet"), their Ring and Cadence, the wavy progress through the Cadence, a
 * state line when Due (**Due** and Snooze) or Snoozed ("Snoozed until 4 Nov" and Cancel snooze),
 * then the core loop's buttons: [primary contact action] [Log interaction] [More contact ▾].
 */
@Composable
internal fun KeepInTouchCard(
    profile: PersonProfile,
    due: DueState?,
    lastInteraction: LocalDate?,
    today: LocalDate,
    keepInTouch: PersonDetailKeepInTouch,
    onContactAction: (ContactAction) -> Unit,
    onMoreContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(R.string.separator)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(lastTalkedText(lastInteraction, today), style = MaterialTheme.typography.titleMedium)
                val ringLine = listOfNotNull(
                    profile.ring?.name?.let(::bidiIsolate),
                    due?.let { cadenceText(it.cadence, inline = profile.ring != null) },
                ).joinToString(separator)
                if (ringLine.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            OrbitIcons.Orbit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 6.dp).size(16.dp),
                        )
                        Text(ringLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (due?.cadence != null) {
                OrbitWavyProgress(
                    progress = if (due.isDue) 1f else due.progress,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            if (due != null) DueStateLine(due, today, keepInTouch)
            ContactButtons(profile, onContactAction, keepInTouch::logInteraction, onMoreContact)
        }
    }
}

/** When Due: **Due** … Snooze. When Snoozed: "Snoozed until 4 Nov" … Cancel snooze. Otherwise nothing. */
@Composable
private fun DueStateLine(due: DueState, today: LocalDate, keepInTouch: PersonDetailKeepInTouch) {
    val formats = LocalOrbitFormats.current
    val snoozedUntil = due.snoozedUntil
    when {
        due.isDue -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
            DueBadge()
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = keepInTouch::snooze) {
                    Icon(OrbitIcons.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.snooze_action), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        snoozedUntil != null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
            Icon(
                OrbitIcons.Snooze,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp).size(18.dp),
            )
            Text(
                text = stringResource(R.string.snoozed_until, formats.dates.format(snoozedUntil, formats.primaryCalendar, today)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = keepInTouch::cancelSnooze) { Text(stringResource(R.string.snooze_cancel)) }
        }
    }
}

/**
 * The connected button group: [primary contact action] [Log interaction] [More contact ▾]. The
 * first is there when there's a primary Contact method, More contact when there are others. With
 * all three, More contact shrinks to its arrow; when a label still doesn't fit (a large font, a
 * long translation), Log interaction moves under the others, then every button gets its own row,
 * rather than clip.
 */
@Composable
private fun ContactButtons(
    profile: PersonProfile,
    onContactAction: (ContactAction) -> Unit,
    onLogInteraction: () -> Unit,
    onMoreContact: () -> Unit,
) {
    val primaryAction = profile.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
    val hasMore = profile.contactMethods.size > 1 || (primaryAction == null && profile.contactMethods.isNotEmpty())
    val buttons = listOfNotNull(
        primaryAction?.let { action ->
            ButtonSpec(action.kind.label(), action.kind.icon, filled = true) { onContactAction(action) }
        },
        ButtonSpec(
            label = stringResource(R.string.interaction_log),
            icon = OrbitIcons.LogInteraction,
            filled = primaryAction == null,
            isLog = true,
            onClick = onLogInteraction,
        ),
        if (hasMore) {
            ButtonSpec(stringResource(R.string.person_detail_more_contact), Icons.Filled.ArrowDropDown, filled = false, iconAfter = true, onClick = onMoreContact)
        } else {
            null
        },
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        // 0: one connected row. 1: the contact buttons in a row, Log interaction under them.
        // 2: every button on its own row. Each step is taken only when a label doesn't fit.
        var layout by remember(buttons.map { it.label }, maxWidth, fontScale) { mutableIntStateOf(0) }
        val next = { layout = (layout + 1).coerceAtMost(2) }
        val log = buttons.first { it.isLog }
        when {
            layout == 0 || buttons.size == 1 -> ConnectedButtons(buttons, onClipped = next)
            layout == 1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectedButtons(buttons - log, onClipped = next)
                GroupButton(log, ButtonDefaults.shape, iconOnly = false, Modifier.fillMaxWidth(), onClipped = next)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEach { button ->
                    GroupButton(button, ButtonDefaults.shape, iconOnly = false, Modifier.fillMaxWidth(), onClipped = {})
                }
            }
        }
    }
}

/** [buttons] as one connected group; More contact shrinks to its arrow when it's one of three. */
@Composable
private fun ConnectedButtons(buttons: List<ButtonSpec>, onClipped: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        val compactMore = buttons.size == 3 || (buttons.size == 2 && buttons.none { it.isLog })
        buttons.forEachIndexed { index, button ->
            val shape: Shape = when {
                buttons.size == 1 -> ButtonDefaults.shape
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShape
                index == buttons.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShape
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes().shape
            }
            val iconOnly = compactMore && button.iconAfter
            GroupButton(
                button = button,
                shape = shape,
                iconOnly = iconOnly,
                modifier = if (iconOnly) Modifier else Modifier.weight(1f),
                onClipped = onClipped,
            )
        }
    }
}

@Composable
private fun GroupButton(button: ButtonSpec, shape: Shape, iconOnly: Boolean, modifier: Modifier, onClipped: () -> Unit) {
    val content: @Composable () -> Unit = {
        if (iconOnly) {
            Icon(button.icon, contentDescription = button.label)
        } else {
            if (!button.iconAfter) Icon(button.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = button.label,
                maxLines = 1,
                onTextLayout = { if (it.hasVisualOverflow) onClipped() },
                modifier = Modifier.padding(start = if (button.iconAfter) 0.dp else 6.dp, end = if (button.iconAfter) 4.dp else 0.dp),
            )
            if (button.iconAfter) Icon(button.icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
    val padding = PaddingValues(horizontal = if (iconOnly) 16.dp else 10.dp)
    if (button.filled) {
        Button(onClick = button.onClick, shape = shape, contentPadding = padding, modifier = modifier) { content() }
    } else {
        FilledTonalButton(onClick = button.onClick, shape = shape, contentPadding = padding, modifier = modifier) { content() }
    }
}

private class ButtonSpec(
    val label: String,
    val icon: ImageVector,
    val filled: Boolean,
    val iconAfter: Boolean = false,
    val isLog: Boolean = false,
    val onClick: () -> Unit,
)

/** "Due" in the primary colour: the People list's chip and Person detail's keep-in-touch card. */
@Composable
fun DueBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
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
 * Interactions, last among the sections and only when there are any (the keep-in-touch card says
 * "No interactions yet" and has Log interaction): the latest few, then "All interactions (n)"
 * when there are more.
 */
@Composable
internal fun InteractionsSection(
    personId: Long,
    interactions: List<InteractionWithPeople>,
    today: LocalDate,
    onOpen: (interactionId: Long) -> Unit,
    onAll: () -> Unit,
) {
    if (interactions.isEmpty()) return
    val numbers = LocalOrbitFormats.current.numbers
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    Column {
        OrbitSectionTitle(stringResource(R.string.person_section_interactions))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
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
