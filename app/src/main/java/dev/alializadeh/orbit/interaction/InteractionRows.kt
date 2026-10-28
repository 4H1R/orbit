package dev.alializadeh.orbit.interaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.alializadeh.orbit.format.LocalOrbitFormats
import java.time.LocalDate

/**
 * One Interaction in a list of [personId]'s (Person detail, All interactions): the type icon, the
 * date, "with Reza, Mina" for the others in it, and the start of the note. Tapping reopens it.
 */
@Composable
fun InteractionListItem(
    item: InteractionWithPeople,
    personId: Long,
    today: LocalDate,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalOrbitFormats.current
    val interaction = item.interaction
    val withOthers = withOthersText(item.people, exceptId = personId, language = formats.language)
    val note = interaction.note?.lineSequence()?.firstOrNull { it.isNotBlank() }
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            Icon(interaction.type.icon, contentDescription = interaction.type.displayName(), tint = MaterialTheme.colorScheme.primary)
        },
        supportingContent = if (withOthers == null && note == null) {
            null
        } else {
            {
                Column {
                    withOthers?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    note?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(formats.dates.format(interaction.date, formats.primaryCalendar, today))
    }
}
