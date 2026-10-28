package dev.alializadeh.orbit.settings

import androidx.compose.ui.graphics.Color
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.about.AboutScreen
import dev.alializadeh.orbit.about.AboutUiState
import dev.alializadeh.orbit.about.Licence
import dev.alializadeh.orbit.about.LicenceTextKey
import dev.alializadeh.orbit.about.LicenceTextScreen
import dev.alializadeh.orbit.about.LicencesScreen
import dev.alializadeh.orbit.about.reflowed
import dev.alializadeh.orbit.firstrun.FirstRunUiState
import dev.alializadeh.orbit.firstrun.WelcomeScreen
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.captureScreen
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.OrbitAppearance
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.io.File
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Welcome, the Settings root and its slice 1 and slice 10 screens, in en/LTR and fa/RTL, light and dark. */
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
            uiState = SettingsUiState(primaryCalendar = language.defaultCalendar, version = AppVersion("1.0.0", 1)),
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

    @Test
    fun about() = captureScreen("about", variant) {
        AboutScreen(AboutUiState(AppVersion("1.0.0", 1), crashes = 0), onBack = {}, onOpenLicences = {}, onShareCrashLog = {}, onClearCrashLog = {})
    }

    @Test
    fun aboutWithCrashes() = captureScreen("about_crashes", variant) {
        AboutScreen(AboutUiState(AppVersion("1.0.0", 1), crashes = 2), onBack = {}, onOpenLicences = {}, onShareCrashLog = {}, onClearCrashLog = {})
    }

    @Test
    fun licences() = captureScreen("licences", variant, tall = true) {
        LicencesScreen(onBack = {}, onOpen = {})
    }

    @Test
    fun licenceText() = captureScreen("licence_text", variant) {
        val key = LicenceTextKey("Outfit", Licence.OflOutfit)
        LicenceTextScreen(key, reflowed(File("src/main/assets/${key.licence.asset}").readText()), onBack = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}
