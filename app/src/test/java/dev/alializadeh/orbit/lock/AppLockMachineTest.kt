package dev.alializadeh.orbit.lock

import dev.alializadeh.orbit.lock.AppLockMachine.Companion.OWN_ACTIVITY_LIMIT_MILLIS
import dev.alializadeh.orbit.lock.AppLockMachine.Companion.OWN_ACTIVITY_WINDOW_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every rule of when App lock locks, on a fake elapsed-realtime clock. */
class AppLockMachineTest {

    /** Elapsed realtime: the only clock the machine has. */
    private var elapsed = 1_000_000L
    private val machine = AppLockMachine { elapsed }

    private fun on(relockAfter: RelockAfter = RelockAfter.OneMinute) =
        AppLockSettings(enabled = true, relockAfter = relockAfter)

    /** A cold start with App lock on, then the unlock. */
    private fun unlockedWith(relockAfter: RelockAfter) {
        machine.onSettings(on(relockAfter))
        machine.onForeground(hasScreenLock = true)
        machine.onUnlocked()
        assertEquals(AppLockState.Unlocked, machine.state)
    }

    private fun away(millis: Long, ownActivity: Boolean = false) {
        if (ownActivity) machine.onOwnActivity()
        machine.onBackground()
        elapsed += millis
        machine.onForeground(hasScreenLock = true)
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
        machine.onOwnActivity()
        machine.onBackground()
        elapsed += 30_000
        machine.onScreenOff()
        elapsed += 50_000
        machine.onForeground(hasScreenLock = true)
        assertEquals(AppLockState.Unlocked, machine.state) // 50 s since screen-off
        machine.onOwnActivity()
        machine.onBackground()
        machine.onScreenOff()
        elapsed += 61_000
        machine.onForeground(hasScreenLock = true)
        assertEquals(AppLockState.Locked, machine.state)
    }

    @Test
    fun aPickerAnnouncedLongBeforeLeavingDoesNotExemptIt() {
        unlockedWith(RelockAfter.Immediately)
        machine.onOwnActivity()
        elapsed += OWN_ACTIVITY_WINDOW_MILLIS + 1
        away(1_000)
        assertEquals(AppLockState.Locked, machine.state)
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
}
