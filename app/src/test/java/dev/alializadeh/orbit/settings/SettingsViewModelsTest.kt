package dev.alializadeh.orbit.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.cash.turbine.test
import dev.alializadeh.orbit.MainUiState
import dev.alializadeh.orbit.MainViewModel
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.data.BackedUpPreferences
import dev.alializadeh.orbit.data.DeviceState
import dev.alializadeh.orbit.language.AppLanguage
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.OrbitAppearance
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()

    @Test
    fun theHostIsReadyWithTheThemeAndFirstRunStateOnceRead() = runTest {
        preferences.update {
            it.copy(theme = ThemeMode.Dark, colourSource = ColourSource.Custom, customSeedArgb = 0xFF12A594.toInt())
        }
        preferences.updateDeviceState { DeviceState(firstRunFinished = true) }

        val ready = MainViewModel(preferences).uiState.value

        assertEquals(
            MainUiState.Ready(
                appearance = OrbitAppearance(ThemeMode.Dark, ColourSource.Custom, Color(0xFF12A594)),
                useLatinDigits = false,
                firstRunFinished = true,
            ),
            ready,
        )
    }

    @Test
    fun appearanceChangesAreStored() = runTest {
        val viewModel = AppearanceViewModel(preferences)
        viewModel.uiState.test {
            assertEquals(
                AppearanceUiState.Loaded(ThemeMode.System, ColourSource.OrbitViolet, customColour = null),
                awaitItem(),
            )
            viewModel.selectTheme(ThemeMode.Light)
            assertEquals(ThemeMode.Light, (awaitItem() as AppearanceUiState.Loaded).theme)
            viewModel.selectCustomColour(Color(0xFFD6409F))
            val custom = awaitItem() as AppearanceUiState.Loaded
            assertEquals(ColourSource.Custom, custom.colourSource)
            assertEquals(Color(0xFFD6409F).toArgb(), custom.customColour?.toArgb())
            viewModel.selectColourSource(ColourSource.Wallpaper)
            assertEquals(ColourSource.Wallpaper, (awaitItem() as AppearanceUiState.Loaded).colourSource)
        }
    }

    @Test
    fun languageAndDatesChangesAreStoredAndTheLanguageApplied() = runTest {
        val appLanguage = object : AppLanguage {
            var language = OrbitLanguage.English
            override fun current() = language
            override fun set(language: OrbitLanguage) {
                this.language = language
            }
        }
        val clock = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneId.of("Asia/Tehran"))
        val viewModel = LanguageDatesViewModel(preferences, appLanguage, clock)

        viewModel.uiState.test {
            assertEquals(
                LanguageDatesUiState.Loaded(CalendarSystem.Gregorian, useLatinDigits = false, today = LocalDate.of(2026, 10, 28)),
                awaitItem(),
            )
            viewModel.selectPrimaryCalendar(CalendarSystem.SolarHijri)
            assertEquals(CalendarSystem.SolarHijri, (awaitItem() as LanguageDatesUiState.Loaded).primaryCalendar)
            viewModel.setUseLatinDigits(true)
            assertEquals(true, (awaitItem() as LanguageDatesUiState.Loaded).useLatinDigits)
        }
        viewModel.selectLanguage(OrbitLanguage.Persian)
        assertEquals(OrbitLanguage.Persian, appLanguage.language)
        // Changing the language doesn't touch the Primary calendar.
        assertEquals(BackedUpPreferences().copy(primaryCalendar = CalendarSystem.SolarHijri, useLatinDigits = true), preferences.backedUp.value)
    }
}
