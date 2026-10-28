package dev.alializadeh.orbit.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.ui.icons.OrbitIcons

/**
 * The locked screen, in place of the whole UI (tabs included) and on no back stack: Back leaves
 * the app. It shows the system prompt by itself on each arrival; cancelling it stays here.
 */
@Composable
fun LockedRoute(appLock: AppLock, arrival: Int) {
    val prompt = rememberScreenLockPrompt()
    val title = stringResource(R.string.locked_prompt_title)
    val unlock = {
        prompt?.authenticate(title) { result ->
            // No screen lock any more: App lock is paused, so Orbit opens.
            if (result == ScreenLockResult.Unlocked || result == ScreenLockResult.NoScreenLock) appLock.onUnlocked()
        }
        Unit
    }
    LaunchedEffect(arrival) {
        if (appLock.claimAutoPrompt(arrival)) unlock()
    }
    LockedScreen(onUnlock = unlock)
}

/** Only the Orbit mark, "Orbit is locked" and Unlock: no Orbit content is drawn. */
@Composable
fun LockedScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = OrbitIcons.Orbit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = stringResource(R.string.locked_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Button(onClick = onUnlock) {
                Text(stringResource(R.string.locked_unlock))
            }
        }
    }
}

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
