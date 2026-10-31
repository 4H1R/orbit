package dev.alializadeh.orbit.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.ApplicationScope
import dev.alializadeh.orbit.OrbitLog
import dev.alializadeh.orbit.due.SnoozeDuration
import dev.alializadeh.orbit.interaction.InteractionRepository
import dev.alializadeh.orbit.person.PersonRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * [ReminderAlarm] on AlarmManager: one `setExactAndAllowWhileIdle(RTC_WAKEUP)` alarm, replaced
 * each time (same PendingIntent). Exact alarms are checked before every alarm: without them (only
 * possible on Android 12/12L) it falls back to an inexact one, and Reminder health says so.
 *
 * Android 12/12L only: SCHEDULE_EXACT_ALARM can be revoked, and revoking it removes the app's
 * exact alarms (those with no window) and kills the app, with no broadcast (the state-changed one
 * comes only on a grant): AOSP `AlarmManagerService.removeExactAlarmsOnPermissionRevokedLocked`,
 * android12L-release. So there a second, inexact `setAndAllowWhileIdle` alarm for the same time
 * stands by: it survives a revocation, and since 12 an inexact one-off alarm's window ends at most
 * an hour after its time (`maxTriggerTime`). Its run sees exact alarms gone, falls back and shows
 * it in Reminder health. 13+ use USE_EXACT_ALARM, which can't be revoked: just the one alarm.
 */
class AndroidReminderAlarm @Inject constructor(@ApplicationContext private val context: Context) : ReminderAlarm {

    override fun set(at: Instant?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(MAIN_REQUEST, PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (at == null) {
            manager.cancel(intent)
            cancelStandby(manager)
            return
        }
        val millis = at.toEpochMilli()
        val exact = try {
            manager.canScheduleExactAlarms().also { allowed ->
                if (allowed) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            }
        } catch (_: SecurityException) {
            false // Revoked between the check and the call.
        }
        if (!exact) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            cancelStandby(manager)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            val standby = alarmIntent(STANDBY_REQUEST, PendingIntent.FLAG_UPDATE_CURRENT)!!
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, standby)
        } else {
            cancelStandby(manager)
        }
    }

    private fun cancelStandby(manager: AlarmManager) {
        alarmIntent(STANDBY_REQUEST, PendingIntent.FLAG_NO_CREATE)?.let(manager::cancel)
    }

    private fun alarmIntent(request: Int, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        request,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or flags,
    )

    companion object {
        const val ACTION_ALARM = "dev.alializadeh.orbit.action.REMINDER_ALARM"
        private const val MAIN_REQUEST = 0

        /** Android 12/12L's inexact stand-by for the exact alarm. */
        private const val STANDBY_REQUEST = 1
    }
}

/** What the receivers need from the app graph (receivers aren't injected). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun reconciler(): ReminderReconciler
    fun notifier(): ReminderNotifier
    fun interactions(): InteractionRepository
    fun people(): PersonRepository
    fun clock(): Clock

    @ApplicationScope
    fun scope(): CoroutineScope

    companion object {
        fun of(context: Context): ReminderEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
    }
}

/** The alarm went off: post what's due and set the next one. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidReminderAlarm.ACTION_ALARM) return
        val app = ReminderEntryPoint.of(context)
        runAsync(app.scope()) { app.reconciler().reconcile() }
    }
}

/**
 * Everything that can lose or move the alarm: boot (on Android 15+ also the first start after a
 * force-stop), the time or zone changing (and on Android 17 the zone's offset), an app update, and
 * exact alarms being allowed. Non-exported: these all come from the system.
 */
class ReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SYSTEM_ACTIONS) return
        val app = ReminderEntryPoint.of(context)
        runAsync(app.scope()) { app.reconciler().reconcile() }
    }

    private companion object {
        val SYSTEM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // Intent.ACTION_TIMEZONE_OFFSET_CHANGED (API 37), by value for older SDK stubs.
            "android.intent.action.TIMEZONE_OFFSET_CHANGED",
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

/**
 * Mark as contacted (an Interaction of type Other, today, no note), Snooze 1 week and Done on a
 * Follow-up or Loan, from a notification. The notification goes at once; then a reconcile tidies up the rest. Not exported:
 * only Orbit's own immutable PendingIntents reach it.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val target = NotificationActionTarget.of(intent) ?: return
        val app = ReminderEntryPoint.of(context)
        app.notifier().cancel(target.key)
        runAsync(app.scope()) {
            try {
                // Posted before a Restore: the id may be someone else's now, so it does nothing.
                app.reconciler().runAction(target) {
                    when (target.type) {
                        NotificationActionType.MarkAsContacted -> app.interactions().markAsContacted(target.personId)
                        NotificationActionType.SnoozeWeek -> app.people().setSnoozedUntil(
                            target.personId,
                            SnoozeDuration.OneWeek.endFrom(LocalDate.now(app.clock())),
                        )
                        NotificationActionType.MarkDone -> when (val key = target.key) {
                            is NotificationKey.FollowUp -> app.people().setFollowUpDone(key.followUpId, LocalDate.now(app.clock()))
                            is NotificationKey.Loan -> app.people().setLoanReturned(key.loanId, LocalDate.now(app.clock()))
                            else -> Unit
                        }
                    }
                }
            } catch (e: RuntimeException) {
                // The Person was deleted since the notification was posted.
                OrbitLog.w(TAG, "Notification action for a Person who's gone", e)
            }
            app.reconciler().reconcile()
        }
    }
}

/** Runs [block] off the main thread while the broadcast is kept alive (`goAsync`, ~10 s budget). */
private fun BroadcastReceiver.runAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            withTimeout(RECEIVER_BUDGET_MILLIS) { block() }
        } catch (e: Exception) {
            OrbitLog.w(TAG, "Reminder work failed", e)
        } finally {
            pending.finish()
        }
    }
}

private const val TAG = "OrbitReminders"
private const val RECEIVER_BUDGET_MILLIS = 9_000L
