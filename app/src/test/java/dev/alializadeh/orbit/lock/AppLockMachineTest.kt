package dev.alializadeh.orbit.lock

import dev.alializadeh.orbit.lock.AppLockMachine.Companion.OWN_ACTIVITY_LIMIT_MILLIS
import dev.alializadeh.orbit.lock.AppLockMachine.Companion.OWN_ACTIVITY_WINDOW_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every rule of when App lock locks, on a fake elapsed-realtime clock. */
class AppLockMachineTest {

    /** Elapsed realtime: the machine's clock. */
    private var elapsed = 1_000_000L

    /** Uptime: stands still while the phone is in deep sleep (screen off). */
    private var uptime = 900_000L
    private val machine = AppLockMachine(elapsedRealtime = { elapsed }, uptime = { uptime })

    private fun pass(millis: Long) {
        elapsed += millis
        uptime += millis
    }

    private fun on(relockAfter: RelockAfter = RelockAfter.OneMinute) =
        AppLockSettings(enabled = true, relockAfter = relockAfter)

    /** A cold start with App lock on, then the unlock. */
    private fun unlockedWith(relockAfter: RelockAfter) {
        machine.onSettings(on(relockAfter))
        machine.onForeground(hasScreenLock = true)
        machine.onUnlocked()
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    /** Time away; [ownActivity]: on Orbit's own picker, which returns its result to bring Orbit back. */
    private fun away(millis: Long, ownActivity: Boolean = false) {
        if (ownActivity) machine.onPickerLaunched(PICKER)
        machine.onBackground()
        pass(millis)
        machine.onForeground(hasScreenLock = true)
        if (ownActivity) machine.onPickerResult(PICKER)
        machine.onResumed()
    }

    @Test
    fun unknownUntilTheSettingsAreRead() {
        assertEquals(AppLockState.Unknown, machine.state)
        machine.onSettings(AppLockSettings())
        assertEquals(AppLockState.Off, machine.state)
    }

    @Test
    fun aColdStartIsLocked() {
        machine.onSettings(on())
        machine.onForeground(hasScreenLock = true)
        assertEquals(AppLockState.Locked, machine.state)

        machine.onUnlocked()
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    @Test
    fun offNeverLocks() {
        machine.onSettings(AppLockSettings(enabled = false))
        away(60 * 60_000)
        assertEquals(AppLockState.Off, machine.state)
    }

    @Test
    fun immediatelyLocksAfterAnyTimeAway() {
        unlockedWith(RelockAfter.Immediately)
        away(0)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun afterOneMinute() {
        unlockedWith(RelockAfter.OneMinute)
        away(60_000)
        assertEquals(AppLockState.Unlocked, machine.state)
        away(60_001)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun afterFiveMinutes() {
        unlockedWith(RelockAfter.FiveMinutes)
        away(4 * 60_000)
        assertEquals(AppLockState.Unlocked, machine.state)
        away(5 * 60_000 + 1)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun timeAwayIsOneStintNotAddedUp() {
        unlockedWith(RelockAfter.OneMinute)
        repeat(5) { away(50_000) }
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    @Test
    fun screenOffIsTimeAway() {
        // The activity stops when the screen goes off: that's going to the background.
        unlockedWith(RelockAfter.OneMinute)
        machine.onBackground()
        machine.onScreenOff()
        elapsed += 2 * 60_000
        machine.onForeground(hasScreenLock = true)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun orbitsOwnPickerIsNotTimeAway() {
        unlockedWith(RelockAfter.Immediately)
        away(3 * 60_000, ownActivity = true)
        assertEquals(AppLockState.Unlocked, machine.state)
        // The exemption is for that one trip only.
        away(0)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun aPickerLeftOpenTooLongLocksAfterAll() {
        unlockedWith(RelockAfter.FiveMinutes)
        away(OWN_ACTIVITY_LIMIT_MILLIS + 1, ownActivity = true)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun theScreenGoingOffOverAPickerStartsTheTimeAway() {
        unlockedWith(RelockAfter.OneMinute)
        machine.onPickerLaunched(PICKER)
        machine.onBackground()
        elapsed += 30_000
        machine.onScreenOff()
        elapsed += 50_000
        machine.onForeground(hasScreenLock = true)
        machine.onPickerResult(PICKER)
        machine.onResumed()
        assertEquals(AppLockState.Unlocked, machine.state) // 50 s since screen-off
        machine.onPickerLaunched(PICKER)
        machine.onBackground()
        machine.onScreenOff()
        elapsed += 61_000
        machine.onForeground(hasScreenLock = true)
        machine.onPickerResult(PICKER)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun aPickerAnnouncedLongBeforeLeavingDoesNotExemptIt() {
        unlockedWith(RelockAfter.Immediately)
        machine.onPickerLaunched(PICKER)
        pass(OWN_ACTIVITY_WINDOW_MILLIS + 1)
        machine.onBackground()
        pass(1_000)
        machine.onForeground(hasScreenLock = true)
        machine.onPickerResult(PICKER)
        assertEquals(AppLockState.Locked, machine.state)
    }

    /** Review finding 1: the unlock's prompt never arms the exemption, and an unlock ends one. */
    @Test
    fun leavingRightAfterAnUnlockLocks() {
        unlockedWith(RelockAfter.Immediately)
        // Home (or Call) within seconds of unlocking.
        away(1_000)
        assertEquals(AppLockState.Locked, machine.state)

        machine.onPickerLaunched(PICKER)
        machine.onUnlocked()
        away(1_000)
        assertEquals(AppLockState.Locked, machine.state)
    }

    /** Review finding 2: only the picker's own result brings Orbit back without the gate. */
    @Test
    fun aPickerReturnWaitsForItsResult() {
        unlockedWith(RelockAfter.Immediately)
        machine.onPickerLaunched(PICKER)
        machine.onBackground()
        pass(20_000)
        machine.onForeground(hasScreenLock = true)
        // Between onStart and onResume: nothing is drawn, and no prompt yet.
        assertEquals(AppLockState.Deciding, machine.state)
        machine.onPickerResult(PICKER)
        assertEquals(AppLockState.Deciding, machine.state)
        machine.onResumed()
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    @Test
    fun aCancelledPickerIsStillItsReturn() {
        // A cancel comes back as a result too, like a pick.
        unlockedWith(RelockAfter.Immediately)
        away(5_000, ownActivity = true)
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    @Test
    fun backWithoutThePickersResultIsTimeAway() {
        // The picker, then Home; Orbit back some other way: no result before onResume.
        unlockedWith(RelockAfter.Immediately)
        val before = machine.arrival
        machine.onPickerLaunched(PICKER)
        machine.onBackground()
        pass(20_000)
        machine.onForeground(hasScreenLock = true)
        machine.onResumed()
        assertEquals(AppLockState.Locked, machine.state)
        assertEquals(before + 1, machine.arrival)
    }

    @Test
    fun theLauncherOrANotificationWhileAPickerIsOpenIsTimeAway() {
        // singleTask: the launcher (or a notification tap) closes the picker, whose cancel still
        // comes back as a result, and delivers a new intent. On Android 15 the order is onStart,
        // the result, the new intent, onResume; any order must lock.
        val orders = listOf(
            listOf("intent", "start", "result"),
            listOf("start", "intent", "result"),
            listOf("start", "result", "intent"),
        )
        for (order in orders) {
            unlockedWith(RelockAfter.Immediately)
            machine.onPickerLaunched(PICKER)
            machine.onBackground()
            pass(20_000)
            for (event in order) {
                when (event) {
                    "intent" -> machine.onNewIntent()
                    "start" -> machine.onForeground(hasScreenLock = true)
                    else -> machine.onPickerResult(PICKER)
                }
            }
            machine.onResumed()
            assertEquals(order.toString(), AppLockState.Locked, machine.state)
        }
    }

    @Test
    fun anotherRequestsResultIsNotThePickersReturn() {
        unlockedWith(RelockAfter.Immediately)
        machine.onPickerLaunched(PICKER)
        machine.onBackground()
        pass(20_000)
        machine.onForeground(hasScreenLock = true)
        machine.onPickerResult(PICKER + 1)
        assertEquals(AppLockState.Deciding, machine.state)
        machine.onResumed()
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun stoppedAgainWhileDecidingIsTimeAway() {
        unlockedWith(RelockAfter.Immediately)
        machine.onPickerLaunched(PICKER)
        machine.onBackground()
        pass(20_000)
        machine.onForeground(hasScreenLock = true)
        machine.onBackground()
        assertEquals(AppLockState.Locked, machine.state)
    }

    /** Review finding 4: a screen-off broadcast held back for a cached process still counts. */
    @Test
    fun aLateScreenOffUndoesThePickerExemption() {
        unlockedWith(RelockAfter.OneMinute)
        away(3 * 60_000, ownActivity = true)
        assertEquals(AppLockState.Unlocked, machine.state)
        // Delivered once Orbit is back, with the screen on again.
        machine.onScreenOff(screenIsOn = true)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun aLateScreenOffAfterAShortPickerTripKeepsTheGrace() {
        unlockedWith(RelockAfter.OneMinute)
        away(30_000, ownActivity = true)
        machine.onScreenOff(screenIsOn = true)
        assertEquals(AppLockState.Unlocked, machine.state) // within the minute anyway
    }

    @Test
    fun aLateScreenOffWhileDecidingEndsTheExemption() {
        unlockedWith(RelockAfter.Immediately)
        machine.onPickerLaunched(PICKER)
        machine.onBackground()
        pass(20_000)
        machine.onForeground(hasScreenLock = true)
        machine.onScreenOff(screenIsOn = true)
        machine.onPickerResult(PICKER)
        machine.onResumed()
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun deepSleepOnAPickerMeansTheScreenWasOff() {
        // No broadcast at all, but uptime stood still: the phone slept, so the screen was off.
        unlockedWith(RelockAfter.Immediately)
        machine.onPickerLaunched(PICKER)
        machine.onBackground()
        pass(10_000)
        elapsed += 60_000 // asleep
        pass(5_000)
        machine.onForeground(hasScreenLock = true)
        machine.onPickerResult(PICKER)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun aScreenOffNowInTheForegroundWaitsForTheStop() {
        // The screen is going off now: the activity stops next, and that time away is judged then.
        unlockedWith(RelockAfter.OneMinute)
        away(3 * 60_000, ownActivity = true)
        machine.onScreenOff(screenIsOn = false)
        assertEquals(AppLockState.Unlocked, machine.state)
        machine.onBackground()
        pass(30_000)
        machine.onForeground(hasScreenLock = true)
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    @Test
    fun contactActionsCountAsTimeAway() {
        // The dialer, messenger, browser or maps: plain time away, nothing announced.
        unlockedWith(RelockAfter.OneMinute)
        away(3 * 60_000)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun aRotationOrRepeatedStartDoesNotLock() {
        unlockedWith(RelockAfter.Immediately)
        machine.onForeground(hasScreenLock = true)
        machine.onForeground(hasScreenLock = true)
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    @Test
    fun onlyElapsedRealtimeCounts() {
        // The machine has no wall clock: moving the phone's date or time can't change time away.
        unlockedWith(RelockAfter.OneMinute)
        machine.onBackground()
        elapsed += 30_000 // while the user sets the date a day ahead
        machine.onForeground(hasScreenLock = true)
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    @Test
    fun turningOnOrOffNeverLocksByItself() {
        machine.onSettings(AppLockSettings(enabled = false))
        machine.onForeground(hasScreenLock = true)
        machine.onSettings(on())
        assertEquals(AppLockState.Unlocked, machine.state)
        machine.onSettings(AppLockSettings(enabled = false))
        assertEquals(AppLockState.Off, machine.state)
    }

    @Test
    fun changingTheTimingKeepsItUnlocked() {
        unlockedWith(RelockAfter.OneMinute)
        machine.onSettings(on(RelockAfter.Immediately))
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    @Test
    fun noScreenLockPausesAndOpensWithoutTheGate() {
        machine.onSettings(on())
        machine.onForeground(hasScreenLock = false)
        assertEquals(AppLockState.Paused, machine.state)
        machine.onBackground()
        elapsed += 10 * 60_000
        machine.onForeground(hasScreenLock = false)
        assertEquals(AppLockState.Paused, machine.state)
    }

    @Test
    fun theGateComesBackOnceThereIsAScreenLockAgain() {
        unlockedWith(RelockAfter.OneMinute)
        machine.onBackground()
        machine.onForeground(hasScreenLock = false) // screen lock removed
        assertEquals(AppLockState.Paused, machine.state)
        machine.onBackground()
        elapsed += 10_000
        machine.onForeground(hasScreenLock = true) // set again, back within a minute
        assertEquals(AppLockState.Unlocked, machine.state)
        away(2 * 60_000)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun eachArrivalCountsOnce() {
        unlockedWith(RelockAfter.Immediately)
        val first = machine.arrival
        away(0)
        machine.onForeground(hasScreenLock = true)
        assertEquals(first + 1, machine.arrival)
    }

    private companion object {
        const val PICKER = 7
    }
}
