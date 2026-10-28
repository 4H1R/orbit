package dev.alializadeh.orbit.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The one alarm, and on Android 12/12L its inexact stand-by: revoking exact alarms there removes
 * the exact one (and kills Orbit) without telling it, so the stand-by is what still wakes it.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidReminderAlarmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val at = Instant.parse("2026-10-28T09:00:00Z")

    @Test
    @Config(sdk = [32])
    fun onAndroid12AnExactAlarmHasAnInexactStandby() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val alarm = AndroidReminderAlarm(context)
        alarm.set(at)
        alarm.set(at.plusSeconds(60)) // replaced, not added

        val scheduled = alarms.scheduledAlarms
        assertEquals(2, scheduled.size)
        assertTrue(scheduled.all { it.triggerAtMs == at.plusSeconds(60).toEpochMilli() && it.isAllowWhileIdle })
        assertEquals(
            setOf(ShadowAlarmManager.WINDOW_EXACT, ShadowAlarmManager.WINDOW_HEURISTIC),
            scheduled.map { it.windowLengthMs }.toSet(),
        )

        alarm.set(null)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    @Config(sdk = [32])
    fun withoutExactAlarmsTheOneAlarmIsInexact() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val alarm = AndroidReminderAlarm(context)
        alarm.set(at)
        // Revoked since: the next alarm falls back, and the stand-by goes.
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        alarm.set(at)

        val scheduled = alarms.scheduledAlarms.single()
        assertEquals(ShadowAlarmManager.WINDOW_HEURISTIC, scheduled.windowLengthMs)
        assertTrue(scheduled.isAllowWhileIdle)
    }

    @Test
    @Config(sdk = [35])
    fun fromAndroid13ThereIsJustTheExactAlarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        AndroidReminderAlarm(context).set(at)

        val scheduled = alarms.scheduledAlarms.single()
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, scheduled.windowLengthMs)
        assertEquals(at.toEpochMilli(), scheduled.triggerAtMs)
    }
}
