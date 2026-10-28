package dev.alializadeh.orbit.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.IntentCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.settings.OrbitSettingsScaffold
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.components.OrbitListGroupScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object ReminderHealthKey : NavKey

data class ReminderHealthUiState(
    /** Null until the first read. */
    val health: ReminderHealth? = null,
    /** Orbit has asked for notifications before (for "ask again" vs "link to settings"). */
    val askedForNotifications: Boolean = false,
)

@HiltViewModel
class ReminderHealthViewModel @Inject constructor(
    private val checker: ReminderHealthChecker,
    private val preferences: PreferencesRepository,
    private val triggers: ReminderTriggers,
) : ViewModel() {

    private val health = MutableStateFlow<ReminderHealth?>(null)

    val uiState: StateFlow<ReminderHealthUiState> =
        combine(health, preferences.deviceState.map { it.notificationPermissionAsked }) { health, asked ->
            ReminderHealthUiState(health, asked)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderHealthUiState())

    /** On every resume: the user may be back from a system screen with something fixed. */
    fun refresh() {
        viewModelScope.launch {
            health.value = checker.check()
            triggers.request()
        }
    }

    fun notificationsAsked() {
        viewModelScope.launch {
            if (!preferences.deviceState.first().notificationPermissionAsked) {
                preferences.updateDeviceState { it.copy(notificationPermissionAsked = true) }
            }
        }
    }
}

@Composable
fun ReminderHealthRoute(onBack: () -> Unit, viewModel: ReminderHealthViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    val manageUnused = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refresh() }
    ReminderHealthScreen(
        uiState = uiState,
        onBack = onBack,
        onFixNotifications = fix@{
            val health = uiState.health ?: return@fix
            when {
                !health.notificationsAllowed -> {
                    // Ask while Android still shows the prompt; once denied for good, the settings.
                    val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
                        (!uiState.askedForNotifications ||
                            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))
                    if (canAsk) {
                        viewModel.notificationsAsked()
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        ReminderSettingsIntents.appNotifications(context)
                    }
                }
                else -> health.channelsOff.firstOrNull()?.let { ReminderSettingsIntents.channel(context, it) }
            }
        },
        onFixExactAlarms = { ReminderSettingsIntents.exactAlarms(context) },
        onFixBattery = { ReminderSettingsIntents.requestBatteryExemption(context) },
        onFixPausedIfUnused = {
            try {
                manageUnused.launch(IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName))
            } catch (_: RuntimeException) {
                // No such screen on this phone.
            }
        },
        onPhoneMakerSteps = { ReminderSettingsIntents.phoneMakerSteps(context, uiState.health?.phoneMaker.orEmpty()) },
    )
}

/**
 * Reminder health: a checklist of what keeps reminders on time, each with its state and a fix.
 * Notifications (the app and both channels), exact alarms (only on Android 12/12L, where they can
 * be taken away), the battery exemption, "Pause app activity if unused", and the phone maker's
 * own steps on dontkillmyapp.com.
 */
@Composable
fun ReminderHealthScreen(
    uiState: ReminderHealthUiState,
    onBack: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixExactAlarms: () -> Unit,
    onFixBattery: () -> Unit,
    onFixPausedIfUnused: () -> Unit,
    onPhoneMakerSteps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitSettingsScaffold(title = stringResource(R.string.reminders_health), onBack = onBack, modifier = modifier) {
        Footer(stringResource(R.string.reminder_health_intro))
        val health = uiState.health ?: return@OrbitSettingsScaffold
        val rows = buildList<@Composable OrbitListGroupScope.(Int, Int) -> Unit> {
            add { index, count ->
                val summary = when {
                    !health.notificationsAllowed -> stringResource(R.string.reminder_health_notifications_off)
                    health.channelsOff.isNotEmpty() -> stringResource(
                        R.string.reminder_health_notifications_channels_off,
                        health.channelsOff.sortedBy { it.ordinal }.map { channelName(it) }
                            .joinToString(stringResource(R.string.list_separator)),
                    )
                    else -> stringResource(R.string.reminder_health_notifications_on)
                }
                HealthRow(
                    title = stringResource(R.string.reminder_health_notifications),
                    summary = summary,
                    ok = health.notificationsOk,
                    fix = stringResource(R.string.reminder_health_turn_on),
                    onFix = onFixNotifications,
                    index = index,
                    count = count,
                )
            }
            health.exactAlarms?.let { allowed ->
                add { index, count ->
                    HealthRow(
                        title = stringResource(R.string.reminder_health_exact_alarms),
                        summary = stringResource(
                            if (allowed) R.string.reminder_health_exact_alarms_on else R.string.reminder_health_exact_alarms_off,
                        ),
                        ok = allowed,
                        fix = stringResource(R.string.reminder_health_allow),
                        onFix = onFixExactAlarms,
                        index = index,
                        count = count,
                    )
                }
            }
            add { index, count ->
                HealthRow(
                    title = stringResource(R.string.reminder_health_battery),
                    summary = stringResource(
                        if (health.batteryUnrestricted) R.string.reminder_health_battery_on else R.string.reminder_health_battery_off,
                    ),
                    ok = health.batteryUnrestricted,
                    fix = stringResource(R.string.reminder_health_allow),
                    onFix = onFixBattery,
                    index = index,
                    count = count,
                )
            }
            health.pausedIfUnused?.let { paused ->
                add { index, count ->
                    HealthRow(
                        title = stringResource(R.string.reminder_health_unused),
                        summary = stringResource(if (paused) R.string.reminder_health_unused_on else R.string.reminder_health_unused_off),
                        ok = !paused,
                        fix = stringResource(R.string.reminder_health_turn_off),
                        onFix = onFixPausedIfUnused,
                        index = index,
                        count = count,
                    )
                }
            }
            add { index, count ->
                HealthRow(
                    title = stringResource(R.string.reminder_health_maker),
                    summary = stringResource(R.string.reminder_health_maker_summary, DontKillMyApp.displayName(health.phoneMaker)),
                    ok = null,
                    fix = stringResource(R.string.reminder_health_see_steps),
                    onFix = onPhoneMakerSteps,
                    index = index,
                    count = count,
                )
            }
        }
        OrbitListGroup(title = null) {
            rows.forEachIndexed { index, row -> row(index, rows.size) }
        }
    }
}

/**
 * One checklist item: a tick or a warning, the title and state, and the fix while it's needed.
 * [ok] null is advice with no state (the phone maker's steps), which always offers its button.
 */
@Composable
private fun OrbitListGroupScope.HealthRow(
    title: String,
    summary: String,
    ok: Boolean?,
    fix: String,
    onFix: () -> Unit,
    index: Int,
    count: Int,
) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onFix,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(),
        leadingContent = {
            when (ok) {
                true -> Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.reminder_health_ok), tint = colors.primary)
                false -> Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.reminder_health_problem), tint = colors.error)
                null -> Icon(Icons.Filled.Info, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        },
        supportingContent = { Text(summary) },
        trailingContent = if (ok != true) {
            { TextButton(onClick = onFix) { Text(fix) } }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(title)
    }
}
