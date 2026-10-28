package dev.alializadeh.orbit.ui.expressive

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    // The sheet is its own window: without a holder out here, its content's saveable state (a typed
    // note) would be lost when the screen under it leaves composition (App lock).
    val saved = rememberSaveableStateHolder()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        saved.SaveableStateProvider(SHEET_CONTENT) { content(closer) }
    }
}

private const val SHEET_CONTENT = "sheet"

/**
 * A modal bottom sheet the user can't swipe, tap or back away: for blocking progress (a Backup
 * being written). It goes when the caller stops showing it; offer a Cancel button inside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitBlockingSheet(content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden })
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = state,
        sheetGesturesEnabled = false,
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false, shouldDismissOnClickOutside = false),
        content = content,
    )
}
