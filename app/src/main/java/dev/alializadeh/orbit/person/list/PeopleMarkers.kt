package dev.alializadeh.orbit.person.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.person.DateSoon
import dev.alializadeh.orbit.ui.expressive.OrbitShapes
import dev.alializadeh.orbit.ui.icons.OrbitIcons

/*
 * The status markers at the end of a People row: one height, tonal, so a list with many Due People
 * stays calm. The date marker keeps the Important-date shape and apricot; it fills only on the day
 * (the Orbit view's badge follows the same rule).
 */

/** "Due": a small tonal pill. */
@Composable
fun DueMarker(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = MarkerSize)
            .clip(MaterialTheme.shapes.small)
            .background(colors.primaryContainer)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.due_label),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

/** An Important date in the next 30 days (tonal) or today (filled), read out as such. */
@Composable
fun DateSoonMarker(soon: DateSoon, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val today = soon == DateSoon.Today
    val description = stringResource(if (today) R.string.people_date_today else R.string.people_date_soon)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(MarkerSize)
            .clip(OrbitShapes.dateBadge())
            .background(if (today) colors.tertiary else colors.tertiaryContainer)
            .semantics { contentDescription = description },
    ) {
        Icon(
            OrbitIcons.Cake,
            contentDescription = null,
            tint = if (today) colors.onTertiary else colors.onTertiaryContainer,
            modifier = Modifier.size(14.dp),
        )
    }
}

private val MarkerSize = 24.dp
