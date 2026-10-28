package dev.alializadeh.orbit.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import dev.alializadeh.orbit.ui.navigation.NavEntryStates
import dev.alializadeh.orbit.ui.navigation.rememberNavEntryStates

/**
 * App lock's gate. While [state] is Locked only [locked] is composed, and while Deciding only a
 * blank surface: [content] (all of Orbit, dialogs and sheets included, as those are its own
 * windows) is out of composition, so nothing of it is drawn, in Recents or in the accessibility
 * tree. Keeping it composed but hidden would leave those windows on screen.
 *
 * What [content] holds survives: the gate keeps, above itself, the saved state of everything
 * behind it (back stacks, `rememberSaveable`) and every navigation entry's state ([NavEntryStates]:
 * each entry's ViewModels and saved UI state), which [content] must give to its navigation. After
 * unlocking, [content] comes back as it was, like after a configuration change; plain `remember`
 * state doesn't survive either.
 */
@Composable
fun AppLockGate(
    state: AppLockState,
    locked: @Composable () -> Unit,
    content: @Composable (NavEntryStates) -> Unit,
) {
    val savedState = rememberSaveableStateHolder()
    val entryStates = rememberNavEntryStates()
    when (state) {
        AppLockState.Locked -> locked()
        // Until the picker's result says whether that's why Orbit is back.
        AppLockState.Deciding -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        else -> savedState.SaveableStateProvider(GATED_CONTENT) { content(entryStates) }
    }
}

private const val GATED_CONTENT = "orbit"
