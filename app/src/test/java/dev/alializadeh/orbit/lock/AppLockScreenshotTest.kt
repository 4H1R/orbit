package dev.alializadeh.orbit.lock

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.firstrun.AppLockOfferScreen
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.captureScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The locked screen, the first-run offer and Settings → App lock, in en/fa, light and dark. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AppLockScreenshotTest(private val variant: ScreenshotVariant) {

    @Test
    fun locked() = captureScreen("app_lock_locked", variant) {
        LockedScreen(onUnlock = {})
    }

    @Test
    fun firstRunOffer() = captureScreen("app_lock_offer", variant) {
        AppLockOfferScreen(isFinishing = false, onTurnOn = {}, onNotNow = {})
    }

    @Test
    fun settingsOff() = captureScreen("app_lock_settings_off", variant) {
        Settings(AppLockSettingsUiState())
    }

    @Test
    fun settingsOn() = captureScreen("app_lock_settings_on", variant, tall = true) {
        Settings(AppLockSettingsUiState(AppLockSettings(enabled = true)))
    }

    /** No screen lock: the switch can't be turned on and says why, with "Set screen lock". */
    @Test
    fun settingsNoScreenLock() = captureScreen("app_lock_settings_no_screen_lock", variant) {
        Settings(AppLockSettingsUiState(hasScreenLock = false))
    }

    /** On, but the screen lock was removed: paused, and it can still be turned off. */
    @Test
    fun settingsPaused() = captureScreen("app_lock_settings_paused", variant, tall = true) {
        Settings(AppLockSettingsUiState(AppLockSettings(enabled = true, showDetailsInNotifications = true), hasScreenLock = false))
    }

    @androidx.compose.runtime.Composable
    private fun Settings(uiState: AppLockSettingsUiState) {
        AppLockSettingsScreen(
            uiState = uiState,
            onBack = {},
            onEnabledChange = {},
            onSetScreenLock = {},
            onRelockAfterSelect = {},
            onShowDetailsChange = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}
