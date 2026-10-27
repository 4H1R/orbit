package dev.alializadeh.orbit.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-wide snackbars. They outlive the screen that shows them: Archive shows "Sara archived ·
 * Undo" and leaves Person detail, so the snackbar appears on the screen underneath, and its Undo
 * still runs after Person detail's ViewModel is gone. Each screen's Scaffold draws them with
 * [OrbitSnackbarHost], so they sit above that screen's FAB.
 */
@Stable
class OrbitSnackbars(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /** Shows [message]; when the user taps [actionLabel], [onAction] runs in the app's scope. */
    fun show(message: String, actionLabel: String? = null, onAction: suspend () -> Unit = {}) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

@Composable
fun rememberOrbitSnackbars(): OrbitSnackbars {
    val scope = rememberCoroutineScope()
    return remember(scope) { OrbitSnackbars(SnackbarHostState(), scope) }
}

/** Null outside the app shell (screenshot tests), where nothing shows snackbars. */
val LocalOrbitSnackbars = staticCompositionLocalOf<OrbitSnackbars?> { null }

/** The app-wide snackbars, for a screen's `Scaffold(snackbarHost = …)`. */
@Composable
fun OrbitSnackbarHost() {
    val snackbars = LocalOrbitSnackbars.current ?: return
    SnackbarHost(snackbars.hostState)
}
