package dev.alializadeh.orbit.settings

import androidx.compose.ui.graphics.Color
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.firstrun.FirstRunUiState
import dev.alializadeh.orbit.firstrun.WelcomeScreen
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.captureScreen
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.OrbitAppearance
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Welcome and the Settings screens of slice 1, in en/LTR and fa/RTL, light and dark. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsScreenshotTest(private val variant: ScreenshotVariant) {

    private val language = if (variant.persian) OrbitLanguage.Persian else OrbitLanguage.English

    @Test
    fun welcome() = captureScreen("welcome", variant) {
        WelcomeScreen(
            uiState = FirstRunUiState(language = language),
            onLanguageSelect = {},
            onPrimaryCalendarSelect = {},
            onUseLatinDigitsChange = {},
            onContinue = {},
            onRestore = {},
        )
    }

    @Test
    fun settings() = captureScreen("settings", variant) {
        SettingsScreen(
            uiState = SettingsUiState(primaryCalendar = language.defaultCalendar),
            onOpen = {},
        )
    }

    @Test
    fun appearance() = captureScreen("appearance", variant) {
        AppearanceScreen(
            uiState = AppearanceUiState.Loaded(ThemeMode.System, ColourSource.OrbitViolet, customColour = null),
            onBack = {},
            onThemeSelect = {},
            onColourSourceSelect = {},
            onCustomColourSelect = {},
        )
    }

    /** "Your colour" with a swatch, so the whole scheme follows it. */
    @Test
    fun appearanceYourColour() {
        val teal = Color(0xFF12A594)
        captureScreen(
            "appearance_your_colour",
            variant,
            appearance = OrbitAppearance(ThemeMode.System, ColourSource.Custom, teal),
        ) {
            AppearanceScreen(
                uiState = AppearanceUiState.Loaded(ThemeMode.System, ColourSource.Custom, teal),
                onBack = {},
                onThemeSelect = {},
                onColourSourceSelect = {},
                onCustomColourSelect = {},
            )
        }
    }

    @Test
    fun languageAndDates() = captureScreen("language_dates", variant) {
        LanguageDatesScreen(
            uiState = LanguageDatesUiState.Loaded(
                primaryCalendar = language.defaultCalendar,
                useLatinDigits = false,
                today = LocalDate.of(2026, 10, 28),
            ),
            language = language,
            onBack = {},
            onLanguageSelect = {},
            onPrimaryCalendarSelect = {},
            onUseLatinDigitsChange = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}
