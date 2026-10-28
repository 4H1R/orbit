package dev.alializadeh.orbit.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.ui.components.OrbitWarningCard
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Upcoming's "App lock is paused" warning: App lock is on but the phone has no screen lock. */
@HiltViewModel
class AppLockWarningViewModel @Inject constructor(
    preferences: PreferencesRepository,
    private val screenLock: ScreenLockStatus,
) : ViewModel() {

    private val hasScreenLock = MutableStateFlow(screenLock.hasSecureScreenLock())

    val paused: StateFlow<Boolean> =
        combine(preferences.deviceState, hasScreenLock) { device, screenLock -> device.appLock.enabled && !screenLock }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** On every resume: the user may have set (or removed) the screen lock meanwhile. */
    fun refresh() {
        hasScreenLock.value = screenLock.hasSecureScreenLock()
    }
}

/** The paused warning, with "Set screen lock" (the system security settings). */
@Composable
fun AppLockPausedCard(onSetScreenLock: () -> Unit, modifier: Modifier = Modifier) {
    OrbitWarningCard(
        text = stringResource(R.string.app_lock_paused),
        actionLabel = stringResource(R.string.app_lock_set_screen_lock),
        onAction = onSetScreenLock,
        modifier = modifier,
    )
}
