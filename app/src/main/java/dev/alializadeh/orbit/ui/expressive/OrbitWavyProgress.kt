package dev.alializadeh.orbit.ui.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The Expressive wavy linear progress (still experimental): how far through their Cadence a Person
 * is, on Person detail's Cadence line. Full in [color] once they're Due. The wave keeps its shape
 * at 100% and doesn't travel.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrbitWavyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LinearWavyProgressIndicator(
        progress = { progress },
        modifier = modifier,
        color = color,
        trackColor = WavyProgressIndicatorDefaults.trackColor,
        // The default flattens the wave near 100%; a Due Person's line stays wavy.
        amplitude = { if (it <= 0f) 0f else 1f },
        // A still wave: calm, and an endless animation would never let the UI go idle (tests).
        waveSpeed = 0.dp,
    )
}
