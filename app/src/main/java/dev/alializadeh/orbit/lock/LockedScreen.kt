package dev.alializadeh.orbit.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.settings.MaxContentWidth
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

/**
 * Only the Orbit mark, "Orbit is locked", one line on why, and Unlock at the bottom where the thumb
 * is: no Orbit content is drawn. Laid out like Welcome, the app's other front door.
 */
@Composable
fun LockedScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            OrbitMark()
            Text(
                text = stringResource(R.string.locked_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp).semantics { heading() },
            )
            Text(
                text = stringResource(R.string.locked_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).widthIn(max = 400.dp),
            )
            Spacer(Modifier.weight(1.3f))
            Button(onClick = onUnlock, modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.locked_unlock), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
    }
}

/**
 * The Orbit mark in a large tonal circle: the app's face on its two front doors (Welcome and
 * Locked). [rotation] turns it about its centre, which sends the body round its ring.
 */
@Composable
fun OrbitMark(modifier: Modifier = Modifier, rotation: Float = 0f) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Icon(
            imageVector = OrbitIcons.Orbit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(64.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
