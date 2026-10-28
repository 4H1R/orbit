package dev.alializadeh.orbit.lock

import dev.alializadeh.orbit.data.DeviceState
import dev.alializadeh.orbit.reminders.NotificationDetail
import dev.alializadeh.orbit.reminders.NotificationRendering
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings → App lock, Upcoming's paused warning, and how notifications render under App lock. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLockSettingsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()
    private var hasScreenLock = true
    private val screenLock = ScreenLockStatus { hasScreenLock }

    private fun kotlinx.coroutines.test.TestScope.viewModel(): AppLockSettingsViewModel {
        val viewModel = AppLockSettingsViewModel(preferences, screenLock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val appLock get() = preferences.device.value.appLock

    @Test
    fun offByDefault() {
        assertEquals(AppLockSettings(false, RelockAfter.OneMinute, showDetailsInNotifications = false), DeviceState().appLock)
    }

    @Test
    fun turningOnNeedsAnUnlock() = runTest {
        val viewModel = viewModel()

        viewModel.setEnabled(true)
        assertEquals(AppLockChange.TurnOn, viewModel.uiState.value.unlockFor)
        assertFalse(appLock.enabled)

        viewModel.onPromptShown()
        assertNull(viewModel.uiState.value.unlockFor)
        viewModel.onUnlockResult(AppLockChange.TurnOn, ScreenLockResult.Cancelled)
        viewModel.onUnlockResult(AppLockChange.TurnOn, ScreenLockResult.Failed)
        viewModel.onUnlockResult(AppLockChange.TurnOn, ScreenLockResult.NoScreenLock)
        assertFalse(appLock.enabled)

        viewModel.onUnlockResult(AppLockChange.TurnOn, ScreenLockResult.Unlocked)
        assertTrue(appLock.enabled)
    }

    @Test
    fun turningOffNeedsAnUnlockToo() = runTest {
        preferences.device.value = DeviceState(appLock = AppLockSettings(enabled = true))
        val viewModel = viewModel()

        viewModel.setEnabled(false)
        assertEquals(AppLockChange.TurnOff, viewModel.uiState.value.unlockFor)
        viewModel.onPromptShown()
        viewModel.onUnlockResult(AppLockChange.TurnOff, ScreenLockResult.Cancelled)
        assertTrue(appLock.enabled)

        viewModel.onUnlockResult(AppLockChange.TurnOff, ScreenLockResult.Unlocked)
        assertFalse(appLock.enabled)
    }

    @Test
    fun withoutAScreenLockItCantBeTurnedOn() = runTest {
        hasScreenLock = false
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.needsScreenLock)
        viewModel.setEnabled(true)

        assertNull(viewModel.uiState.value.unlockFor)
        assertFalse(appLock.enabled)
    }

    @Test
    fun aScreenLockSetMeanwhileIsSeenOnResume() = runTest {
        hasScreenLock = false
        val viewModel = viewModel()
        hasScreenLock = true

        viewModel.refreshScreenLock()

        assertFalse(viewModel.uiState.value.needsScreenLock)
        viewModel.setEnabled(true)
        assertEquals(AppLockChange.TurnOn, viewModel.uiState.value.unlockFor)
    }

    @Test
    fun pausedItCanStillBeTurnedOffWithNothingToAsk() = runTest {
        hasScreenLock = false
        preferences.device.value = DeviceState(appLock = AppLockSettings(enabled = true))
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.paused)

        viewModel.setEnabled(false)
        viewModel.onUnlockResult(AppLockChange.TurnOff, ScreenLockResult.NoScreenLock)

        assertFalse(appLock.enabled)
    }

    @Test
    fun timingAndDetailsAreStoredWithoutAnUnlock() = runTest {
        preferences.device.value = DeviceState(appLock = AppLockSettings(enabled = true))
        val viewModel = viewModel()

        viewModel.setRelockAfter(RelockAfter.FiveMinutes)
        viewModel.setShowDetailsInNotifications(true)

        assertEquals(AppLockSettings(true, RelockAfter.FiveMinutes, showDetailsInNotifications = true), appLock)
        assertNull(viewModel.uiState.value.unlockFor)
    }

    @Test
    fun upcomingWarnsOnlyWhileOnWithoutAScreenLock() = runTest {
        val warning = AppLockWarningViewModel(preferences, screenLock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { warning.paused.collect {} }
        assertFalse(warning.paused.value)

        preferences.device.value = DeviceState(appLock = AppLockSettings(enabled = true))
        assertFalse(warning.paused.value)
        hasScreenLock = false
        warning.refresh()
        assertTrue(warning.paused.value)

        preferences.device.value = DeviceState(appLock = AppLockSettings(enabled = false))
        assertFalse(warning.paused.value)
    }

    // --- Notification rendering ---------------------------------------------------------------

    @Test
    fun theRenderingMatrix() {
        fun rendering(enabled: Boolean, details: Boolean) =
            AppLockRenderingPolicy.renderingFor(AppLockSettings(enabled = enabled, showDetailsInNotifications = details))

        // Off: detailed, as before App lock (Show details doesn't matter).
        assertEquals(NotificationRendering(NotificationDetail.Detailed, actionsNeedUnlock = false), rendering(false, false))
        assertEquals(NotificationRendering(NotificationDetail.Detailed, actionsNeedUnlock = false), rendering(false, true))
        // On: discreet, no actions at all.
        assertEquals(NotificationRendering(NotificationDetail.Discreet, actionsNeedUnlock = false), rendering(true, false))
        // On + Show details: detailed, actions ask for the device unlock.
        assertEquals(NotificationRendering(NotificationDetail.Detailed, actionsNeedUnlock = true), rendering(true, true))
    }

    @Test
    fun thePolicyFollowsTheStoredSettings() = runTest {
        val policy = AppLockRenderingPolicy(preferences)
        val seen = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            assertEquals(
                listOf(NotificationDetail.Detailed, NotificationDetail.Discreet),
                policy.changes().take(2).toList().map { it.detail },
            )
        }
        assertEquals(NotificationRendering(), policy.current())

        preferences.updateDeviceState { it.copy(appLock = AppLockSettings(enabled = true)) }

        assertEquals(NotificationDetail.Discreet, policy.current().detail)
        seen.join()
        assertEquals(NotificationDetail.Discreet, policy.changes().first().detail)
    }
}
