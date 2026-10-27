package dev.alializadeh.orbit.ui.expressive

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** What an [OrbitBottomSheet]'s content can do: close the sheet with its slide-out animation. */
fun interface SheetCloser {
    /** Animates the sheet away, then runs [then] (for example, an action needing the screen). */
    fun close(then: () -> Unit)
}

/**
 * A modal bottom sheet that skips the half-expanded state. [onDismiss] runs once it's gone, however
 * it was closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(SheetCloser) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closer = SheetCloser { then ->
        scope.launch { state.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        content(closer)
    }
}
