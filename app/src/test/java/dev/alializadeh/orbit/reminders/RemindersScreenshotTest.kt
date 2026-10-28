package dev.alializadeh.orbit.reminders

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.testing.NoCloser
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.SheetFrame
import dev.alializadeh.orbit.testing.captureScreen
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings → Reminders, Reminder health and the Reminder time sheet, in en/fa, light and dark. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class RemindersScreenshotTest(private val variant: ScreenshotVariant) {

    private val allSet = ReminderHealth(notificationsAllowed = true, batteryUnrestricted = true, pausedIfUnused = false, phoneMaker = "Google")

    @Test
    fun remindersSettings() = captureScreen("reminders_settings", variant, tall = true) {
        RemindersSettingsScreen(
            uiState = RemindersSettingsUiState(
                reminderTime = LocalTime.of(9, 0),
                defaultLeadTimes = LeadTime.DEFAULT,
                health = allSet.copy(batteryUnrestricted = false, channelsOff = setOf(ReminderChannel.KeepInTouch)),
            ),
            onBack = {},
            onReminderTimeClick = {},
            onLeadTimeToggle = {},
            onHealthClick = {},
            onChannelClick = {},
        )
    }

    /** Android 12: notifications on but one channel muted, exact alarms denied, battery optimised, paused if unused. */
    @Test
    fun reminderHealthProblems() = captureScreen("reminder_health_problems", variant, tall = true) {
        ReminderHealthScreen(
            uiState = ReminderHealthUiState(
                health = ReminderHealth(
                    notificationsAllowed = true,
                    channelsOff = setOf(ReminderChannel.ImportantDates),
                    exactAlarms = false,
                    batteryUnrestricted = false,
                    pausedIfUnused = true,
                    phoneMaker = "samsung",
                    notificationsNeedPermission = false,
                ),
            ),
            onBack = {},
            onFixNotifications = {},
            onFixExactAlarms = {},
            onFixBattery = {},
            onFixPausedIfUnused = {},
            onPhoneMakerSteps = {},
        )
    }

    /** Android 13+ with everything set: no exact-alarm row (it can't be taken away there). */
    @Test
    fun reminderHealthAllSet() = captureScreen("reminder_health_all_set", variant) {
        ReminderHealthScreen(
            uiState = ReminderHealthUiState(health = allSet, askedForNotifications = true),
            onBack = {},
            onFixNotifications = {},
            onFixExactAlarms = {},
            onFixBattery = {},
            onFixPausedIfUnused = {},
            onPhoneMakerSteps = {},
        )
    }

    @Test
    fun reminderTimeSheet() = captureScreen("reminder_time_sheet", variant) {
        SheetFrame {
            ReminderTimeSheetContent(initial = LocalTime.of(9, 30), onDone = {}, sheet = NoCloser)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}
