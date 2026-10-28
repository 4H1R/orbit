package dev.alializadeh.orbit.firstrun

import androidx.lifecycle.SavedStateHandle
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.language.AppLanguage
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.lock.ScreenLockResult
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.SeedRing
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Finishing first run (Room on the JVM) and the Welcome ViewModel's rules. */
@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: OrbitDatabase
    private val preferences = FakePreferencesRepository()
    private val names = SeedRingNames { ring, language -> "${language.tag}:${ring.name}" }
    private lateinit var repository: FirstRunRepository

    @Before
    fun setUp() {
        database = TestDatabases.inMemory()
        repository = FirstRunRepository(database, preferences, names)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun finishingCreatesTheSeedRingsInTheChosenLanguageInnermostFirst() = runTest {
        repository.finish(WelcomeChoices(OrbitLanguage.Persian, CalendarSystem.SolarHijri, useLatinDigits = true))

        val rings = database.ringDao().observeAll().first()
        assertEquals(listOf("fa:Inner", "fa:Close", "fa:Friends", "fa:Acquaintances"), rings.map(Ring::name))
        assertEquals(listOf(0, 1, 2, 3), rings.map(Ring::position))
        assertEquals(listOf(Cadence.days(14), Cadence.days(30), Cadence.days(90), null), rings.map(Ring::cadence))
        assertTrue(preferences.device.value.firstRunFinished)
    }

    @Test
    fun finishingStoresTheWelcomeChoices() = runTest {
        repository.finish(WelcomeChoices(OrbitLanguage.Persian, CalendarSystem.SolarHijri, useLatinDigits = true))

        val stored = database.backedUpPreferencesDao().get()
        assertEquals(CalendarSystem.SolarHijri, stored.primaryCalendar)
        assertTrue(stored.useLatinDigits)
    }

    @Test
    fun finishingAgainNeverAddsASecondSetOfRings() = runTest {
        repository.finish(WelcomeChoices(OrbitLanguage.English, CalendarSystem.Gregorian, useLatinDigits = false))
        repository.finish(WelcomeChoices(OrbitLanguage.Persian, CalendarSystem.SolarHijri, useLatinDigits = false))

        val rings = database.ringDao().observeAll().first()
        assertEquals(SeedRing.entries.size, rings.size)
        assertEquals("en:Inner", rings.first().name)
    }

    @Test
    fun theSeedsMatchTheCopyDeck() {
        assertEquals(4, SeedRing.entries.size)
        assertTrue(SeedRing.entries.size <= Ring.MAX_COUNT)
        assertNull(SeedRing.Acquaintances.cadence)
    }

    @Test
    fun cadenceDueDates() {
        val lastContact = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 14), Cadence.days(14).dueDate(lastContact))
        assertEquals(LocalDate.of(2026, 2, 14), Cadence.weeks(2).dueDate(lastContact))
        assertEquals(LocalDate.of(2026, 2, 28), Cadence.months(1).dueDate(lastContact))
    }

    // --- Welcome ViewModel -------------------------------------------------------------------

    private class FakeAppLanguage(var language: OrbitLanguage) : AppLanguage {
        override fun current() = language
        override fun set(language: OrbitLanguage) {
            this.language = language
        }
    }

    /** Without a screen lock unless asked, so the App lock offer is skipped. */
    private fun viewModel(language: OrbitLanguage = OrbitLanguage.English, hasScreenLock: Boolean = false) =
        FirstRunViewModel(FakeAppLanguage(language), repository, { hasScreenLock }, SavedStateHandle())

    @Test
    fun thePrimaryCalendarFollowsTheLanguageUntilTouched() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(CalendarSystem.Gregorian, viewModel.uiState.value.primaryCalendar)
        viewModel.selectLanguage(OrbitLanguage.Persian)
        assertEquals(CalendarSystem.SolarHijri, viewModel.uiState.value.primaryCalendar)

        viewModel.selectPrimaryCalendar(CalendarSystem.Gregorian)
        viewModel.selectLanguage(OrbitLanguage.English)
        viewModel.selectLanguage(OrbitLanguage.Persian)
        assertEquals(CalendarSystem.Gregorian, viewModel.uiState.value.primaryCalendar)
    }

    @Test
    fun choosingALanguageAppliesItAtOnce() {
        val appLanguage = FakeAppLanguage(OrbitLanguage.English)
        FirstRunViewModel(appLanguage, repository, { false }, SavedStateHandle()).selectLanguage(OrbitLanguage.Persian)
        assertEquals(OrbitLanguage.Persian, appLanguage.language)
    }

    @Test
    fun latinDigitsAreOfferedAndStoredForPersianOnly() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertFalse(viewModel.uiState.value.showsLatinDigits)
        viewModel.selectLanguage(OrbitLanguage.Persian)
        assertTrue(viewModel.uiState.value.showsLatinDigits)

        viewModel.setUseLatinDigits(true)
        viewModel.selectLanguage(OrbitLanguage.English)
        viewModel.continueFromStep()
        preferences.device.first { it.firstRunFinished } // finishing runs on Room's IO dispatcher
        assertFalse(database.backedUpPreferencesDao().get().useLatinDigits)
    }

    @Test
    fun continueOnTheLastStepFinishesFirstRun() = runTest {
        val viewModel = viewModel(OrbitLanguage.Persian)
        viewModel.setUseLatinDigits(true)

        viewModel.continueFromStep()

        preferences.device.first { it.firstRunFinished } // finishing runs on Room's IO dispatcher
        val stored = database.backedUpPreferencesDao().get()
        assertEquals(CalendarSystem.SolarHijri, stored.primaryCalendar)
        assertTrue(stored.useLatinDigits)
        assertEquals("fa:Inner", database.ringDao().observeAll().first().first().name)
    }

    // --- The App lock offer ------------------------------------------------------------------

    @Test
    fun withAScreenLockTheAppLockOfferFollowsWelcome() = runTest {
        val viewModel = viewModel(hasScreenLock = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.continueFromStep()

        assertEquals(FirstRunStep.AppLockOffer, viewModel.uiState.value.step)
        assertFalse(preferences.device.value.firstRunFinished)
    }

    @Test
    fun withoutAScreenLockTheOfferIsSkippedSilently() = runTest {
        val viewModel = viewModel(hasScreenLock = false)

        viewModel.continueFromStep()

        preferences.device.first { it.firstRunFinished }
        assertFalse(preferences.device.value.appLock.enabled)
    }

    @Test
    fun notNowFinishesWithAppLockOff() = runTest {
        val viewModel = viewModel(hasScreenLock = true)
        viewModel.continueFromStep()

        viewModel.continueFromStep()

        preferences.device.first { it.firstRunFinished }
        assertFalse(preferences.device.value.appLock.enabled)
    }

    @Test
    fun turnOnNeedsTheUnlockThenTurnsAppLockOnAndFinishes() = runTest {
        val viewModel = viewModel(hasScreenLock = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.continueFromStep()

        viewModel.onTurnOnAppLock(ScreenLockResult.Cancelled)
        viewModel.onTurnOnAppLock(ScreenLockResult.Failed)
        assertEquals(FirstRunStep.AppLockOffer, viewModel.uiState.value.step)
        assertFalse(preferences.device.value.appLock.enabled)

        viewModel.onTurnOnAppLock(ScreenLockResult.Unlocked)

        preferences.device.first { it.firstRunFinished }
        assertTrue(preferences.device.value.appLock.enabled)
    }

    @Test
    fun afterARestoreOnWelcomeTheOfferStillComes() = runTest {
        val viewModel = viewModel(hasScreenLock = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.startRestore("content://backup")

        viewModel.onRestored()

        assertEquals(FirstRunStep.AppLockOffer, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.restoreUri)
    }
}
