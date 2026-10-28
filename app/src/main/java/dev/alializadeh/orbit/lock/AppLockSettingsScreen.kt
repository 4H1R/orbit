package dev.alializadeh.orbit.lock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.reminders.Footer
import dev.alializadeh.orbit.settings.OrbitSettingsScaffold
import dev.alializadeh.orbit.ui.components.ChoiceRow
import dev.alializadeh.orbit.ui.components.NavigationRow
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.components.SwitchRow
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object AppLockSettingsKey : NavKey

/** Turning App lock on or off: either needs an unlock first. */
enum class AppLockChange { TurnOn, TurnOff }

data class AppLockSettingsUiState(
    val settings: AppLockSettings = AppLockSettings(),
    /** Read again on every resume (the user may have just set one). */
    val hasScreenLock: Boolean = true,
    /** The system prompt should show for this change; the route clears it once shown. */
    val unlockFor: AppLockChange? = null,
) {
    /** On, but the phone has no screen lock. */
    val paused: Boolean get() = settings.enabled && !hasScreenLock

    /** Off and no screen lock: it can't be turned on. */
    val needsScreenLock: Boolean get() = !settings.enabled && !hasScreenLock
}

/**
 * Settings → App lock: on/off (both behind the system prompt, shown in place), the re-lock timing
 * and "Show details in notifications". Device-bound, so none of it is in a Backup. A change that
 * affects notifications re-renders them (`ReminderTriggers` watches the rendering policy).
 */
@HiltViewModel
class AppLockSettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val screenLock: ScreenLockStatus,
) : ViewModel() {

    private val hasScreenLock = MutableStateFlow(screenLock.hasSecureScreenLock())
    private val unlockFor = MutableStateFlow<AppLockChange?>(null)

    val uiState: StateFlow<AppLockSettingsUiState> =
        combine(preferences.deviceState.map { it.appLock }, hasScreenLock, unlockFor) { settings, screenLock, unlock ->
            AppLockSettingsUiState(settings, screenLock, unlock)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockSettingsUiState())

    fun refreshScreenLock() {
        hasScreenLock.value = screenLock.hasSecureScreenLock()
    }

    /** The switch: asks for the unlock first. Without a screen lock it can't be turned on. */
    fun setEnabled(enabled: Boolean) {
        if (enabled == uiState.value.settings.enabled) return
        if (enabled) {
            refreshScreenLock()
            if (!hasScreenLock.value) return
        }
        unlockFor.value = if (enabled) AppLockChange.TurnOn else AppLockChange.TurnOff
    }

    /** The route has shown the prompt for the pending change. */
    fun onPromptShown() {
        unlockFor.value = null
    }

    /** How the prompt for [change] ended: only an unlock makes the change. */
    fun onUnlockResult(change: AppLockChange, result: ScreenLockResult) {
        val passed = when (result) {
            ScreenLockResult.Unlocked -> true
            // Turning off a paused App lock: there's no screen lock to ask for.
            ScreenLockResult.NoScreenLock -> change == AppLockChange.TurnOff
            ScreenLockResult.Cancelled, ScreenLockResult.Failed -> false
        }
        if (!passed) return
        update { it.copy(enabled = change == AppLockChange.TurnOn) }
    }

    fun setRelockAfter(relockAfter: RelockAfter) = update { it.copy(relockAfter = relockAfter) }

    fun setShowDetailsInNotifications(show: Boolean) = update { it.copy(showDetailsInNotifications = show) }

    private fun update(transform: (AppLockSettings) -> AppLockSettings) {
        viewModelScope.launch { preferences.updateDeviceState { it.copy(appLock = transform(it.appLock)) } }
    }
}

@Composable
fun AppLockSettingsRoute(
    onBack: () -> Unit,
    viewModel: AppLockSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshScreenLock()
        onPauseOrDispose {}
    }
    val prompt = rememberScreenLockPrompt()
    val turnOnTitle = stringResource(R.string.app_lock_prompt_turn_on)
    val turnOffTitle = stringResource(R.string.app_lock_prompt_turn_off)
    LaunchedEffect(uiState.unlockFor) {
        val change = uiState.unlockFor ?: return@LaunchedEffect
        viewModel.onPromptShown()
        val title = if (change == AppLockChange.TurnOn) turnOnTitle else turnOffTitle
        prompt?.authenticate(title) { viewModel.onUnlockResult(change, it) }
    }
    AppLockSettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onEnabledChange = viewModel::setEnabled,
        onSetScreenLock = { openSecuritySettings(context) },
        onRelockAfterSelect = viewModel::setRelockAfter,
        onShowDetailsChange = viewModel::setShowDetailsInNotifications,
    )
}

/**
 * Settings → App lock. Without a screen lock the switch can't be turned on and says why, with
 * "Set screen lock"; on but paused, it says so (and can still be turned off).
 */
@Composable
fun AppLockSettingsScreen(
    uiState: AppLockSettingsUiState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSetScreenLock: () -> Unit,
    onRelockAfterSelect: (RelockAfter) -> Unit,
    onShowDetailsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = uiState.settings
    OrbitSettingsScaffold(title = stringResource(R.string.settings_app_lock_title), onBack = onBack, modifier = modifier) {
        val rows = if (uiState.hasScreenLock) 1 else 2
        OrbitListGroup(title = null) {
            SwitchRow(
                title = stringResource(R.string.settings_app_lock_title),
                summary = stringResource(
                    when {
                        uiState.paused -> R.string.app_lock_paused
                        uiState.needsScreenLock -> R.string.app_lock_needs_screen_lock
                        else -> R.string.app_lock_switch_summary
                    },
                ),
                checked = settings.enabled,
                enabled = !uiState.needsScreenLock,
                index = 0,
                count = rows,
                onCheckedChange = onEnabledChange,
            )
            if (!uiState.hasScreenLock) {
                NavigationRow(
                    title = stringResource(R.string.app_lock_set_screen_lock),
                    summary = null,
                    index = 1,
                    count = rows,
                    onClick = onSetScreenLock,
                )
            }
        }
        if (!settings.enabled) return@OrbitSettingsScaffold
        OrbitListGroup(title = stringResource(R.string.app_lock_relock_title)) {
            RelockAfter.entries.forEachIndexed { index, relockAfter ->
                ChoiceRow(
                    title = relockAfterName(relockAfter),
                    summary = null,
                    selected = relockAfter == settings.relockAfter,
                    index = index,
                    count = RelockAfter.entries.size,
                    onClick = { onRelockAfterSelect(relockAfter) },
                )
            }
        }
        Footer(stringResource(R.string.app_lock_relock_footer))
        OrbitListGroup(title = null) {
            SwitchRow(
                title = stringResource(R.string.app_lock_show_details),
                summary = stringResource(R.string.app_lock_show_details_summary),
                checked = settings.showDetailsInNotifications,
                index = 0,
                count = 1,
                onCheckedChange = onShowDetailsChange,
            )
        }
        Footer(stringResource(R.string.app_lock_show_details_footer))
    }
}

/** "Immediately", "After 1 minute", "After 5 minutes". */
@Composable
fun relockAfterName(relockAfter: RelockAfter): String {
    val minutes = (relockAfter.millis / 60_000).toInt()
    return if (relockAfter == RelockAfter.Immediately) {
        stringResource(R.string.app_lock_relock_immediately)
    } else {
        pluralStringResource(R.plurals.app_lock_relock_after_minutes, minutes, LocalOrbitFormats.current.numbers.format(minutes))
    }
}

/** The system's security settings, where the screen lock is set. */
fun openSecuritySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
