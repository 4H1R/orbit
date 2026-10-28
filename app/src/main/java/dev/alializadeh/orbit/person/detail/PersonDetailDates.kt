package dev.alializadeh.orbit.person.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.format.bidiIsolateLtr
import dev.alializadeh.orbit.person.GiftIdea
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.dates.DateTypeBadge
import dev.alializadeh.orbit.person.dates.ageText
import dev.alializadeh.orbit.person.dates.importantDateName
import dev.alializadeh.orbit.person.dates.yearsCountText
import dev.alializadeh.orbit.ui.components.OrbitSectionTitle
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.time.LocalDate

/**
 * Important dates, first among the sections: each date's type or label, the date in the Primary
 * calendar (plus its own Calendar when that differs), the years count when the year is known, and
 * a Birthday's current age. Tapping a date opens the date sheet. A date that falls today has Send
 * wishes, unless [onSendWishes] is null.
 */
@Composable
internal fun ImportantDatesSection(
    dates: List<ImportantDate>,
    today: LocalDate,
    onOpen: (ImportantDate) -> Unit,
    onSendWishes: ((dateId: Long) -> Unit)? = null,
) {
    if (dates.isEmpty()) return
    val formats = LocalOrbitFormats.current
    val separator = stringResource(R.string.separator)
    Column {
        OrbitSectionTitle(stringResource(R.string.person_section_important_dates), horizontalPadding = 4.dp)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            dates.forEachIndexed { index, date ->
                val dateLine = formats.dates.importantDateParts(date.date, formats.primaryCalendar, today).joinToString(separator)
                val occurrence = date.date.nextOccurrence(today)
                val years = yearsCountText(date.type, occurrence.yearsCount)
                val age = if (date.type == ImportantDateType.Birthday) ageText(date.date.completedYears(today)) else null
                val wishes = onSendWishes?.takeIf { occurrence.date == today }
                SegmentedListItem(
                    onClick = { onOpen(date) },
                    shapes = ListItemDefaults.segmentedShapes(index, dates.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    leadingContent = { DateTypeBadge(date.type) },
                    supportingContent = {
                        Column {
                            Text(dateLine)
                            if (years != null) Text(years, color = MaterialTheme.colorScheme.tertiary)
                        }
                    },
                    trailingContent = if (age == null && wishes == null) {
                        null
                    } else {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                age?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                                wishes?.let { send ->
                                    IconButton(onClick = { send(date.id) }) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.wishes_send))
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(importantDateName(date.type, date.label?.let(::bidiIsolate)))
                }
            }
        }
    }
}

/**
 * Gift ideas, always shown: the open ideas (each with Mark as given), "Given (n)" collapsed, and a
 * "+ Gift idea" row. Tapping an idea opens its sheet.
 */
@Composable
internal fun GiftIdeasSection(
    open: List<GiftIdea>,
    given: List<GiftIdea>,
    today: LocalDate,
    onAdd: () -> Unit,
    onOpen: (GiftIdea) -> Unit,
    onMarkGiven: (GiftIdea) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    var showGiven by rememberSaveable { mutableStateOf(false) }
    val formats = LocalOrbitFormats.current
    val count = open.size + (if (given.isEmpty()) 0 else 1) + (if (showGiven) given.size else 0) + 1
    var index = 0
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    Column {
        OrbitSectionTitle(stringResource(R.string.person_section_gift_ideas), horizontalPadding = 4.dp)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            open.forEach { idea ->
                SegmentedListItem(
                    onClick = { onOpen(idea) },
                    shapes = ListItemDefaults.segmentedShapes(index++, count),
                    colors = colors,
                    leadingContent = { Icon(OrbitIcons.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    supportingContent = ideaDetails(idea),
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            idea.link?.let { link ->
                                IconButton(onClick = { onOpenLink(link) }) {
                                    Icon(OrbitIcons.OpenInNew, contentDescription = stringResource(R.string.gift_idea_open_link))
                                }
                            }
                            IconButton(onClick = { onMarkGiven(idea) }) {
                                Icon(OrbitIcons.Gift, contentDescription = stringResource(R.string.gift_idea_mark_given))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(idea.text)
                }
            }
            if (given.isNotEmpty()) {
                SegmentedListItem(
                    onClick = { showGiven = !showGiven },
                    shapes = ListItemDefaults.segmentedShapes(index++, count),
                    colors = colors,
                    leadingContent = { Icon(OrbitIcons.Gift, contentDescription = null) },
                    trailingContent = {
                        Icon(if (showGiven) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.gift_idea_given_section, formats.numbers.format(given.size)))
                }
                if (showGiven) {
                    given.forEach { idea ->
                        val givenOn = idea.givenOn?.let { formats.dates.format(it, formats.primaryCalendar, today) }
                        SegmentedListItem(
                            onClick = { onOpen(idea) },
                            shapes = ListItemDefaults.segmentedShapes(index++, count),
                            colors = colors,
                            leadingContent = { Icon(OrbitIcons.Gift, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            supportingContent = givenOn?.let { { Text(stringResource(R.string.gift_idea_given_on, it)) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(idea.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            SegmentedListItem(
                onClick = onAdd,
                shapes = ListItemDefaults.segmentedShapes(index, count),
                colors = colors,
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.gift_idea_add), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** An open idea's note and link under its text, if it has them. */
private fun ideaDetails(idea: GiftIdea): (@Composable () -> Unit)? {
    if (idea.note == null && idea.link == null) return null
    return {
        Column {
            idea.note?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            idea.link?.let {
                Text(bidiIsolateLtr(it), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
