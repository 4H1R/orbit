package dev.alializadeh.orbit.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.ApplicationScope
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.data.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * When to ask for notifications (ticket 07): the first time the user saves an Important date or a
 * Person with a Cadence, never on first launch. Screens report those saves here.
 */
fun interface ReminderPermissionAsks {
    /** Something was saved that reminders are for. */
    fun remindersNeeded()

    companion object {
        val None = ReminderPermissionAsks {}
    }
}

/** Raises the one-time ask for [ReminderPermissionHost] to make. */
@Singleton
class DefaultReminderPermissionAsks @Inject constructor(
    private val preferences: PreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : ReminderPermissionAsks {

    private val pending = MutableStateFlow(false)

    /** An ask is waiting for the activity. */
    val requested: StateFlow<Boolean> = pending.asStateFlow()

    override fun remindersNeeded() {
        scope.launch {
            if (!preferences.deviceState.first().notificationPermissionAsked) pending.value = true
        }
    }

    fun taken() {
        pending.value = false
    }
}

@HiltViewModel
class ReminderPermissionViewModel @Inject constructor(
    private val asks: DefaultReminderPermissionAsks,
    private val preferences: PreferencesRepository,
    private val triggers: ReminderTriggers,
) : ViewModel() {

    val askNotifications: StateFlow<Boolean> = asks.requested

    private val batteryReason = MutableStateFlow(false)

    /** The one-line reason before the battery-exemption prompt is showing. */
    val showBatteryReason: StateFlow<Boolean> = batteryReason.asStateFlow()

    /** The ask is being made (or isn't needed): it's never made again. */
    fun notificationsAsked() {
        asks.taken()
        viewModelScope.launch { preferences.updateDeviceState { it.copy(notificationPermissionAsked = true) } }
    }

    /** Notifications are (now) allowed or not; when they are, ask once for the battery exemption. */
    fun notificationsResult(allowed: Boolean, batteryExempt: Boolean) {
        triggers.request()
        if (!allowed || batteryExempt) return
        viewModelScope.launch {
            if (!preferences.deviceState.first().batteryExemptionAsked) batteryReason.value = true
        }
    }

    /** Continue or Not now on the reason: either way it's not asked again (Reminder health has it). */
    fun batteryAsked() {
        batteryReason.value = false
        viewModelScope.launch { preferences.updateDeviceState { it.copy(batteryExemptionAsked = true) } }
    }
}

/**
 * Makes the asks from the activity: the notification permission (Android 13+), then, once
 * notifications are allowed, the battery exemption with a one-line reason and the direct system
 * prompt (allowed for a sideloaded app). Sits in the app shell, above every screen.
 */
@Composable
fun ReminderPermissionHost(viewModel: ReminderPermissionViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val askNotifications by viewModel.askNotifications.collectAsStateWithLifecycle()
    val showBatteryReason by viewModel.showBatteryReason.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.notificationsResult(allowed = granted, batteryExempt = isBatteryExempt(context))
    }
    LaunchedEffect(askNotifications) {
        if (!askNotifications) return@LaunchedEffect
        viewModel.notificationsAsked()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AndroidReminderNotifier.canPost(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.notificationsResult(AndroidReminderNotifier.canPost(context), isBatteryExempt(context))
        }
    }
    if (showBatteryReason) {
        AlertDialog(
            onDismissRequest = viewModel::batteryAsked,
            text = { Text(stringResource(R.string.battery_ask_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batteryAsked()
                        ReminderSettingsIntents.requestBatteryExemption(context)
                    },
                ) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::batteryAsked) { Text(stringResource(R.string.common_not_now)) }
            },
        )
    }
}

fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/** The system screens Reminder health and the asks open. Each fails quietly on a phone without it. */
object ReminderSettingsIntents {

    /** The direct "Let Orbit always run in the background?" prompt. */
    @SuppressLint("BatteryLife") // Orbit is sideloaded (ticket 02); no Play policy applies.
    fun requestBatteryExemption(context: Context) =
        start(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()))

    fun appNotifications(context: Context) = start(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun channel(context: Context, channel: ReminderChannel) = start(
        context,
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id),
    )

    fun exactAlarms(context: Context) =
        start(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))

    /** The phone maker's page on dontkillmyapp.com, in the browser (Orbit itself has no INTERNET). */
    fun phoneMakerSteps(context: Context, manufacturer: String) =
        start(context, Intent(Intent.ACTION_VIEW, DontKillMyApp.url(manufacturer).toUri()))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
