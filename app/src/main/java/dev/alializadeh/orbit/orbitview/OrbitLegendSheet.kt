package dev.alializadeh.orbit.orbitview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.DateSoon
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import dev.alializadeh.orbit.ui.theme.isDarkScheme
import kotlin.math.cos
import kotlin.math.sin

/** The ⓘ sheet: how to read the Orbit view. */
@Composable
fun OrbitLegendSheet(onDismiss: () -> Unit) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet -> OrbitLegendSheetContent(sheet) }
}

/**
 * The legend's rows (Rings, drift and glow, the date badge, the angle), each with a small drawing
 * of what it explains. The text never names a colour.
 */
@Composable
fun OrbitLegendSheetContent(sheet: SheetCloser) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.orbit_legend_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp).semantics { heading() },
        )
        LegendRow(stringResource(R.string.orbit_legend_rings)) { drawRingsExample(it) }
        LegendRow(stringResource(R.string.orbit_legend_drift)) { drawDriftExample(it) }
        val soon = DateSoon.DATE_SOON_DAYS
        LegendRow(pluralStringResource(R.plurals.orbit_legend_badge, soon, LocalOrbitFormats.current.numbers.format(soon))) {
            drawBadgeExample(it)
        }
        LegendRow(stringResource(R.string.orbit_legend_angle)) { drawAngleExample(it) }
        TextButton(onClick = { sheet.close {} }, modifier = Modifier.align(Alignment.End).padding(end = 16.dp)) {
            Text(stringResource(R.string.common_done))
        }
    }
}

/** The colours the examples are drawn in. */
private class LegendColours(
    val primary: androidx.compose.ui.graphics.Color,
    val onPrimary: androidx.compose.ui.graphics.Color,
    val tertiary: androidx.compose.ui.graphics.Color,
    val tertiaryContainer: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val person: androidx.compose.ui.graphics.Color,
    /** As on the Orbit view: the primary colour in dark, a lighter tone of it in light. */
    val glow: androidx.compose.ui.graphics.Color,
)

@Composable
private fun LegendRow(text: String, example: DrawScope.(LegendColours) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colours = LegendColours(
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        tertiary = scheme.tertiary,
        tertiaryContainer = scheme.tertiaryContainer,
        surface = scheme.surfaceContainerLow,
        person = scheme.secondary,
        glow = if (scheme.isDarkScheme()) scheme.primary else scheme.primaryFixedDim,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Canvas(Modifier.size(56.dp).clipToBounds()) { example(colours) }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** "You" in the middle of three Rings. */
private fun DrawScope.drawRingsExample(c: LegendColours) {
    val centre = center
    val d = density
    for (r in listOf(26f, 18f, 11f)) drawCircle(c.primary.copy(alpha = 0.07f), radius = r * d, center = centre)
    for (r in listOf(26f, 18f, 11f)) drawCircle(c.primary.copy(alpha = 0.35f), radius = r * d, center = centre, style = Stroke(1f * d))
    drawCircle(c.primary, radius = 5f * d, center = centre)
}

/** Someone pushed out past their Ring, glowing. */
private fun DrawScope.drawDriftExample(c: LegendColours) {
    val d = density
    // A quarter of a Ring around the bottom corner, and someone half a gap outside it.
    val centre = Offset(0f, size.height)
    drawCircle(c.primary.copy(alpha = 0.35f), radius = 32f * d, center = centre, style = Stroke(1f * d))
    val angle = Math.toRadians(-45.0)
    val at = Offset(centre.x + (cos(angle) * 46f * d).toFloat(), centre.y + (sin(angle) * 46f * d).toFloat())
    drawCircle(
        Brush.radialGradient(
            0f to c.glow.copy(alpha = 0.75f),
            0.45f to c.glow.copy(alpha = 0.75f),
            1f to c.glow.copy(alpha = 0f),
            center = at,
            radius = 17f * d,
        ),
        radius = 17f * d,
        center = at,
    )
    drawCircle(c.surface, radius = 9f * d, center = at)
    drawCircle(c.person, radius = 7.5f * d, center = at)
    drawCircle(c.primary, radius = 8f * d, center = at, style = Stroke(2f * d))
}

/** One person with a date soon (outlined badge), one with it today (filled). */
private fun DrawScope.drawBadgeExample(c: LegendColours) {
    val d = density
    fun person(at: Offset, today: Boolean) {
        drawCircle(c.person, radius = 11f * d, center = at)
        val badge = Offset(at.x + 7.8f * d, at.y - 7.8f * d)
        drawCircle(c.surface, radius = 6.5f * d, center = badge)
        if (today) {
            drawCircle(c.tertiary, radius = 5f * d, center = badge)
        } else {
            drawCircle(c.tertiaryContainer, radius = 5f * d, center = badge)
            drawCircle(c.tertiary, radius = 4f * d, center = badge, style = Stroke(2f * d))
        }
    }
    person(Offset(size.width * 0.3f, size.height * 0.62f), today = false)
    person(Offset(size.width * 0.72f, size.height * 0.38f), today = true)
}

/** A Ring with People scattered round it. */
private fun DrawScope.drawAngleExample(c: LegendColours) {
    val d = density
    val r = 21f * d
    drawCircle(c.primary.copy(alpha = 0.35f), radius = r, center = center, style = Stroke(1f * d))
    drawCircle(c.primary, radius = 4f * d, center = center)
    for (degrees in listOf(-70.0, 25.0, 150.0, 205.0)) {
        val a = Math.toRadians(degrees)
        val at = Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat())
        drawCircle(c.surface, radius = 6f * d, center = at)
        drawCircle(c.person, radius = 4.5f * d, center = at)
    }
}
