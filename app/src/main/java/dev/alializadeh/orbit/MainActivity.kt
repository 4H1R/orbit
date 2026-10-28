package dev.alializadeh.orbit

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.alializadeh.orbit.firstrun.FirstRunRoute
import dev.alializadeh.orbit.reminders.NotificationEntries
import dev.alializadeh.orbit.reminders.NotificationEntry
import dev.alializadeh.orbit.reminders.ReminderTriggers
import dev.alializadeh.orbit.ui.navigation.OrbitApp
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import dev.alializadeh.orbit.ui.theme.isDark
import javax.inject.Inject

/**
 * The single host activity. It's an [AppCompatActivity] because AppCompat per-app locales and
 * biometric 1.1.0 (App lock) both need one.
 *
 * The splash screen stays up until the first preferences read (ADR 0006: the theme lives in the
 * database), so the wrong theme never flashes. Then it shows first run until that's finished once,
 * and the tabs after.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var notificationEntries: NotificationEntries

    @Inject lateinit var reminderTriggers: ReminderTriggers

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        // Opened from a notification (not when recreated, or relaunched from Recents with the old intent).
        if (savedInstanceState == null) offerEntry(intent)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = uiState as? MainUiState.Ready ?: return@setContent
            val darkTheme = ready.appearance.themeMode.isDark()
            // System bar icons follow Orbit's theme, which may differ from the phone's.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            OrbitTheme(
                appearance = ready.appearance,
                useLatinDigits = ready.useLatinDigits,
                // First run shows dates (if any) in the calendar it's offering.
                primaryCalendar = ready.primaryCalendar.takeIf { ready.firstRunFinished },
            ) {
                if (ready.firstRunFinished) OrbitApp() else FirstRunRoute()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        offerEntry(intent)
    }

    override fun onStart() {
        super.onStart()
        // Back in Orbit: the user may have changed notification settings or the time meanwhile.
        reminderTriggers.request()
    }

    private fun offerEntry(intent: Intent?) {
        if (intent == null || (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        NotificationEntry.of(intent)?.let(notificationEntries::offer)
    }
}
