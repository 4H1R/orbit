package dev.alializadeh.orbit.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.ApplicationScope
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.reminders.NotificationDetail
import dev.alializadeh.orbit.reminders.NotificationRendering
import dev.alializadeh.orbit.reminders.NotificationRenderingPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the phone has a secure screen lock now (Android's KeyguardManager; a fake in tests). */
fun interface ScreenLockStatus {
    fun hasSecureScreenLock(): Boolean
}

class AndroidScreenLockStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScreenLockStatus {
    override fun hasSecureScreenLock(): Boolean = context.hasSecureScreenLock()
}

/**
 * App lock for the whole process: [AppLockMachine] fed with the stored settings, the host
 * activity's stops and starts, screen-off, the screen lock's presence, and unlocks. All on the
 * main thread. `MainActivity` draws the locked screen while [status] is [AppLockState.Locked].
 */
@Singleton
class AppLock @Inject constructor(
    @ApplicationContext private val context: Context,
    preferences: PreferencesRepository,
    private val screenLock: ScreenLockStatus,
    @ApplicationScope scope: CoroutineScope,
) {
    private val machine = AppLockMachine(SystemClock::elapsedRealtime, SystemClock::uptimeMillis)
    private val mutableStatus = MutableStateFlow(machine.status)
    private var autoPromptedArrival = 0

    val status: StateFlow<AppLockStatus> = mutableStatus.asStateFlow()

    /** App lock is on (paused or not): Recents and screenshots are protected. Null until read. */
    val protectsScreen: StateFlow<Boolean?> = preferences.deviceState
        .map { it.appLock.enabled }
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        machine.onScreenLock(screenLock.hasSecureScreenLock())
        scope.launch(Dispatchers.Main.immediate) {
            preferences.deviceState.map { it.appLock }.distinctUntilChanged().collect {
                machine.onSettings(it)
                publish()
            }
        }
    }

    /** Listens for the screen going off (it ends the picker exemption). Once, from the Application. */
    fun start() {
        val power = context.getSystemService(PowerManager::class.java)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Android 14+ holds broadcasts for a cached process: the screen may be on again.
                machine.onScreenOff(screenIsOn = power?.isInteractive == true)
                publish()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** The host activity is starting: called before it draws, so a lock is in place first. */
    fun onForeground() {
        machine.onForeground(screenLock.hasSecureScreenLock())
        publish()
    }

    /** The host activity resumed: a return no picker result has claimed is decided now. */
    fun onResumed() {
        machine.onResumed()
        publish()
    }

    /** The host activity got a new intent (the launcher, a notification): not a picker's return. */
    fun onNewIntent() {
        machine.onNewIntent()
        publish()
    }

    /** The host activity stopped, not for a configuration change. */
    fun onBackground() {
        machine.onBackground()
        publish()
    }

    /** Orbit is about to launch its own picker for a result (photo, Backup Save/Open). */
    fun onPickerLaunched(requestCode: Int) {
        machine.onPickerLaunched(requestCode)
    }

    /** A result for [requestCode] reached the host activity. */
    fun onPickerResult(requestCode: Int) {
        machine.onPickerResult(requestCode)
        publish()
    }

    fun onUnlocked() {
        machine.onUnlocked()
        publish()
    }

    /** The locked screen shows the prompt by itself once per arrival; true the first time. */
    fun claimAutoPrompt(arrival: Int): Boolean {
        if (autoPromptedArrival == arrival) return false
        autoPromptedArrival = arrival
        return true
    }

    private fun publish() {
        mutableStatus.value = machine.status
    }
}

/**
 * How notifications render under App lock (ticket 10): off → detailed; on → discreet (no name,
 * photo, Gift ideas or actions), unless "Show details in notifications", which brings the
 * details back with background actions that ask for the device unlock. Paused counts as on.
 */
class AppLockRenderingPolicy @Inject constructor(
    private val preferences: PreferencesRepository,
) : NotificationRenderingPolicy {

    override suspend fun current(): NotificationRendering = renderingFor(preferences.deviceState.first().appLock)

    override fun changes(): Flow<NotificationRendering> = preferences.deviceState.map { renderingFor(it.appLock) }

    companion object {
        fun renderingFor(settings: AppLockSettings): NotificationRendering = when {
            !settings.enabled -> NotificationRendering()
            settings.showDetailsInNotifications ->
                NotificationRendering(NotificationDetail.Detailed, actionsNeedUnlock = true)
            else -> NotificationRendering(NotificationDetail.Discreet)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface AppLockModule {

    @Binds
    fun bindScreenLockStatus(status: AndroidScreenLockStatus): ScreenLockStatus

    @Binds
    fun bindRenderingPolicy(policy: AppLockRenderingPolicy): NotificationRenderingPolicy
}
