package dev.alializadeh.orbit

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
import dev.alializadeh.orbit.ui.navigation.OrbitApp
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import dev.alializadeh.orbit.ui.theme.isDark

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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
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
            OrbitTheme(appearance = ready.appearance, useLatinDigits = ready.useLatinDigits) {
                if (ready.firstRunFinished) OrbitApp() else FirstRunRoute()
            }
        }
    }
}
