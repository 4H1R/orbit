package dev.alializadeh.orbit.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reminder health's checklist count, Upcoming's critical warning, and the phone maker's page. */
class ReminderHealthTest {

    private val healthy = ReminderHealth(notificationsAllowed = true, exactAlarms = null, batteryUnrestricted = true, pausedIfUnused = false)

    @Test
    fun everythingSetCountsNoProblemsAndWarnsOfNothing() {
        assertEquals(0, healthy.problems)
        assertNull(healthy.critical(askedForNotifications = true))
    }

    @Test
    fun eachItemThatNeedsTheUserCountsOnce() {
        val all = ReminderHealth(
            notificationsAllowed = true,
            channelsOff = setOf(ReminderChannel.KeepInTouch),
            exactAlarms = false,
            batteryUnrestricted = false,
            pausedIfUnused = true,
        )
        assertEquals(4, all.problems)
        // A muted channel isn't critical: the user chose it, and Upcoming still shows everything.
        assertEquals(CriticalReminderProblem.ExactAlarmsOff, all.critical(askedForNotifications = true))
        assertNull(healthy.copy(channelsOff = ReminderChannel.entries.toSet(), batteryUnrestricted = false).critical(true))
    }

    @Test
    fun notificationsOffWarnOnlyOnceOrbitHasAsked() {
        val off = healthy.copy(notificationsAllowed = false)
        // A fresh install on Android 13+: off by default, and Orbit hasn't asked yet.
        assertNull(off.critical(askedForNotifications = false))
        assertEquals(CriticalReminderProblem.NotificationsOff, off.critical(askedForNotifications = true))
        // Android 12: there's no ask, so off means the user turned them off.
        assertEquals(CriticalReminderProblem.NotificationsOff, off.copy(notificationsNeedPermission = false).critical(false))
        // Notifications come first.
        assertEquals(CriticalReminderProblem.NotificationsOff, off.copy(exactAlarms = false).critical(true))
    }

    @Test
    fun unavailableItemsAreNotProblems() {
        assertEquals(0, healthy.copy(exactAlarms = null, pausedIfUnused = null).problems)
    }

    @Test
    fun thePhoneMakersPageOnDontKillMyApp() {
        assertEquals("https://dontkillmyapp.com/samsung", DontKillMyApp.url("samsung"))
        assertEquals("https://dontkillmyapp.com/xiaomi", DontKillMyApp.url("Xiaomi"))
        assertEquals("https://dontkillmyapp.com/xiaomi", DontKillMyApp.url("Redmi"))
        assertEquals("https://dontkillmyapp.com/nokia", DontKillMyApp.url("HMD Global"))
        assertEquals("https://dontkillmyapp.com/google", DontKillMyApp.url("Google"))
        assertEquals("https://dontkillmyapp.com/", DontKillMyApp.url("Fairphone"))
        assertEquals("Samsung", DontKillMyApp.displayName("samsung"))
        assertEquals("HMD Global", DontKillMyApp.displayName("HMD Global"))
    }
}
