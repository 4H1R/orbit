package dev.alializadeh.orbit.ui.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.alializadeh.orbit.R

// Every still-experimental Material 3 Expressive API (LoadingIndicator, MaterialShapes...) is used
// only through this package, so churn in the material3 1.5 betas stays in one place.

/**
 * True draws endless animations as a still frame and turns off the Orbit view's motion. `MainActivity`
 * sets it while the system's "Remove animations" is on; screenshot tests set it because Roborazzi
 * waits for Compose to go idle, which an endless animation never does.
 */
val LocalStillMotion = staticCompositionLocalOf { false }

/** The Expressive loading indicator, announced as "Loading". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrbitLoadingIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.common_loading)
    val semantics = modifier.semantics { contentDescription = description }
    if (LocalStillMotion.current) {
        LoadingIndicator(progress = { STILL_PROGRESS }, modifier = semantics)
    } else {
        LoadingIndicator(modifier = semantics)
    }
}

private const val STILL_PROGRESS = 0.6f
