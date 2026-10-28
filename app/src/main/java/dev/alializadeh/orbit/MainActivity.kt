package dev.alializadeh.orbit

import android.content.Intent
import android.content.IntentSender
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.alializadeh.orbit.firstrun.FirstRunRoute
import dev.alializadeh.orbit.lock.AppLock
import dev.alializadeh.orbit.lock.AppLockState
import dev.alializadeh.orbit.lock.LockedRoute
import dev.alializadeh.orbit.lock.OwnActivityHost
import dev.alializadeh.orbit.reminders.NotificationEntries
import dev.alializadeh.orbit.reminders.NotificationEntry
import dev.alializadeh.orbit.reminders.ReminderTriggers
import dev.alializadeh.orbit.ui.navigation.OrbitApp
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import dev.alializadeh.orbit.ui.theme.isDark
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The single host activity. It's an [AppCompatActivity] because AppCompat per-app locales and
 * biometric 1.1.0 (App lock) both need one.
 *
 * The splash screen stays up until the first preferences read (ADR 0006: the theme lives in the
 * database) and App lock's settings are known, so the wrong theme never flashes and nothing shows
 * before the gate. Then it shows the locked screen while App lock is locked, first run until that's
 * finished once, and the tabs after. Everything behind the gate keeps its state while locked.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity(), OwnActivityHost {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var notificationEntries: NotificationEntries

    @Inject lateinit var reminderTriggers: ReminderTriggers

    @Inject lateinit var appLock: AppLock

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            viewModel.uiState.value is MainUiState.Loading || appLock.status.value.state == AppLockState.Unknown
        }
        // Opened from a notification (not when recreated, or relaunched from Recents with the old intent).
        if (savedInstanceState == null) offerEntry(intent)
        protectRecentsWhileAppLockIsOn()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val lock by appLock.status.collectAsStateWithLifecycle()
            val ready = uiState as? MainUiState.Ready ?: return@setContent
            if (lock.state == AppLockState.Unknown) return@setContent
            val darkTheme = ready.appearance.themeMode.isDark()
            // System bar icons follow Orbit's theme, which may differ from the phone's.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            // What's behind the gate leaves composition while locked; this keeps its saved state
            // (back stacks, scroll positions), and its ViewModels stay with the activity.
            val behindTheGate = rememberSaveableStateHolder()
            OrbitTheme(
                appearance = ready.appearance,
                useLatinDigits = ready.useLatinDigits,
                // First run shows dates (if any) in the calendar it's offering.
                primaryCalendar = ready.primaryCalendar.takeIf { ready.firstRunFinished },
            ) {
                if (lock.state == AppLockState.Locked) {
                    LockedRoute(appLock, lock.arrival)
                } else {
                    behindTheGate.SaveableStateProvider(GATED_CONTENT) {
                        // A notification tap waits in NotificationEntries until OrbitApp takes it.
                        if (ready.firstRunFinished) OrbitApp() else FirstRunRoute()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        offerEntry(intent)
    }

    override fun onStart() {
        // Before anything draws again: lock first if Orbit was away too long.
        appLock.onForeground()
        super.onStart()
        // Back in Orbit: the user may have changed notification settings or the time meanwhile.
        reminderTriggers.request()
    }

    override fun onStop() {
        super.onStop()
        // Screen off, Home, another app; not a rotation or language change.
        if (!isChangingConfigurations) appLock.onBackground()
    }

    override fun onOwnActivity() {
        appLock.onOwnActivity()
    }

    // Everything Orbit launches for a result is its own picker (photo, Backup Save/Open) or a
    // system ask; App lock doesn't count those as leaving. Contact actions use startActivity.
    @Deprecated("Overrides a deprecated framework method; ActivityResultRegistry calls it.")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (requestCode >= 0) onOwnActivity()
        super.startActivityForResult(intent, requestCode, options)
    }

    @Deprecated("Overrides a deprecated framework method; ActivityResultRegistry calls it.")
    @Suppress("DEPRECATION")
    override fun startIntentSenderForResult(
        intent: IntentSender,
        requestCode: Int,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int,
        options: Bundle?,
    ) {
        if (requestCode >= 0) onOwnActivity()
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options)
    }

    /**
     * While App lock is on, Recents shows no thumbnail: `setRecentsScreenshotEnabled(false)` on
     * API 33+ (the user's own screenshots still work), `FLAG_SECURE` on 31/32 (the only option
     * there). Off: neither. Applied live as the setting changes.
     */
    private fun protectRecentsWhileAppLockIsOn() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                appLock.protectsScreen.filterNotNull().collect { protect ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setRecentsScreenshotEnabled(!protect)
                    } else if (protect) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    private fun offerEntry(intent: Intent?) {
        if (intent == null || (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        NotificationEntry.of(intent)?.let(notificationEntries::offer)
    }

    private companion object {
        const val GATED_CONTENT = "orbit"
    }
}
