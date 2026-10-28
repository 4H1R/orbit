package dev.alializadeh.orbit.reminders

import android.os.SystemClock
import dev.alializadeh.orbit.backup.BackupStatusDao
import dev.alializadeh.orbit.calendar.LunarHijriAdjustment
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.occasion.OccasionRepository
import dev.alializadeh.orbit.person.PersonDao
import dev.alializadeh.orbit.upcoming.UpcomingRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Posts and cancels Orbit's notifications (Android's NotificationManager; a fake in tests). */
interface ReminderNotifier {
    /** Orbit's notifications on screen now, or null when that can't be read. */
    fun showing(): Set<NotificationKey>?

    /** The channels a notification would reach the user on now. */
    fun channelsOn(): Set<ReminderChannel>

    /** Posts (or replaces) [notification], rendered as [rendering] says. */
    suspend fun post(notification: PlannedNotification, rendering: NotificationRendering, now: Instant)

    fun cancel(key: NotificationKey)
}

/** The one alarm (AlarmManager; a fake in tests). */
interface ReminderAlarm {
    /** Replaces the alarm with one at [at], or cancels it when null. */
    fun set(at: Instant?)
}

/** How much notifications show: App lock decides (`AppLockRenderingPolicy`). */
@Serializable
enum class NotificationDetail {
    /** Names, photo, Gift ideas and actions. */
    Detailed,

    /** The copy deck's discreet texts only: no names, photos, Gift ideas or actions. */
    Discreet,
}

/** How notifications are rendered at post time. */
@Serializable
data class NotificationRendering(
    val detail: NotificationDetail = NotificationDetail.Detailed,
    /** Background actions (Mark as contacted, Snooze) ask for the device unlock first. */
    val actionsNeedUnlock: Boolean = false,
)

/** Where [NotificationRendering] comes from: App lock's settings (`AppLockRenderingPolicy`). */
fun interface NotificationRenderingPolicy {
    suspend fun current(): NotificationRendering

    /** The rendering as it changes; `ReminderTriggers` reconciles on each change. */
    fun changes(): Flow<NotificationRendering> = emptyFlow()
}

/**
 * Spaces Orbit's posts so Android never drops one. AOSP `NotificationManagerService
 * .checkDisqualifyingFeatures` silently drops ("Shedding") an *update* of a notification that's
 * already posted or enqueued while the app's enqueue rate is over `mMaxPackageEnqueueRate`
 * (`DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE` = 5 per second, `Settings.Global
 * .MAX_NOTIFICATION_ENQUEUE_RATE`); to the app the post still looks done. That rate is
 * `NotificationUsageStats.getAppEnqueueRate` → `RateEstimator`: 1 / an exponential moving average
 * (alpha 0.7) of the time between the app's accepted enqueues, new posts and updates alike
 * (read in android15-release). Keeping every gap at least [MIN_GAP_MILLIS] keeps that average at
 * 0.25 s or more, so the rate stays at 4 per second or less.
 *
 * A run posts for at most [BUDGET_MILLIS], so it fits the receivers' `goAsync` budget; the rest is
 * left to a follow-up run.
 */
@Singleton
class NotificationPacer internal constructor(
    private val elapsedMillis: () -> Long,
    private val sleep: suspend (Long) -> Unit,
) {
    @Inject constructor() : this(SystemClock::elapsedRealtime, { delay(it) })

    /** When the last post ended (in this process; the reconciler's mutex serialises runs). */
    private var lastPostAt: Long? = null

    /**
     * Runs [post] for [items] in order, spaced out, until the budget is spent. Returns how many it
     * ran: the first that many of [items].
     */
    suspend fun <T> pace(items: List<T>, post: suspend (T) -> Unit): Int {
        val start = elapsedMillis()
        items.forEachIndexed { index, item ->
            val wait = lastPostAt?.let { (it + MIN_GAP_MILLIS - elapsedMillis()).coerceAtLeast(0) } ?: 0
            if (elapsedMillis() + wait - start > BUDGET_MILLIS) return index
            if (wait > 0) sleep(wait)
            try {
                post(item)
            } finally {
                lastPostAt = elapsedMillis()
            }
        }
        return items.size
    }

    companion object {
        /** At most 4 posts or updates a second, under Android's 5. */
        const val MIN_GAP_MILLIS = 250L

        /** Posting time per run (21 posts), well inside the receivers' 9 s. */
        const val BUDGET_MILLIS = 5_000L
    }
}

/**
 * The one idempotent `reconcile()` (ticket 11): snapshots the data, runs [ReminderPlanner], then
 * cancels, posts (paced by [NotificationPacer]), records "last notified as Due" with the targeted
 * update, stores the device's [NotificationState], and sets the one alarm for the next time. Runs
 * are serialised; every trigger (the alarm, boot, time and zone changes, app updates, exact alarms
 * granted, app start, data edits, preference changes, Restore, notification actions) calls it.
 *
 * A run can stop part way (an exception, the receivers' timeout, the pacing budget), so:
 * - the alarm is always set (`finally`): the plan's next time, or sooner to retry or follow up;
 * - the state is stored after each post ([ReminderPlan.stateAfter]), so what was posted is never
 *   posted again with sound, and what wasn't is posted by the next run;
 * - People are marked as notified only once the digest naming them is posted.
 */
@Singleton
class ReminderReconciler @Inject constructor(
    private val upcoming: UpcomingRepository,
    private val occasions: OccasionRepository,
    private val preferences: PreferencesRepository,
    private val personDao: PersonDao,
    private val notifier: ReminderNotifier,
    private val alarm: ReminderAlarm,
    private val rendering: NotificationRenderingPolicy,
    private val clock: Clock,
    private val statusDao: BackupStatusDao,
    private val pacer: NotificationPacer,
) {
    private val mutex = Mutex()

    suspend fun reconcile(): ReminderPlan = mutex.withLock {
        val now = clock.instant()
        // Whatever happens below, an alarm is set: a run that fails before it's done is retried.
        var nextAlarm: Instant? = now.plus(RETRY_AFTER_FAILURE)
        try {
            reconcileAt(now) { nextAlarm = it }
        } finally {
            alarm.set(nextAlarm)
        }
    }

    private suspend fun reconcileAt(now: Instant, setNextAlarm: (Instant?) -> Unit): ReminderPlan {
        val zone = clock.zone
        val backedUp = preferences.preferences.first()
        // A receiver's process may have no activity to have applied it.
        LunarHijriAdjustment.set(backedUp.lunarAdjustmentDays)
        val device = preferences.deviceState.first()
        val token = statusDao.restoreToken()
        val people = upcoming.observePeople().first()
        val occasionList = occasions.observeOccasions().first()
        var showing = notifier.showing()
        val stored = if (device.notifications.restoreToken == token) {
            device.notifications
        } else {
            // A Restore replaced the data: the ids in the old state (and on screen) may name other
            // People now. Take down whatever still shows and plan from nothing.
            (showing ?: device.notifications.showingKeys()).forEach(notifier::cancel)
            showing = emptySet()
            NotificationState()
        }
        val state = showing?.let(stored::retainShowing) ?: stored
        val how = rendering.current()
        val plan = ReminderPlanner.plan(
            PlannerInput(
                people = people,
                reminderTime = backedUp.reminderTime,
                defaultLeadTimes = backedUp.defaultLeadTimes,
                now = now,
                zone = zone,
                state = state,
                channelsOn = notifier.channelsOn(),
                rendering = how,
                occasions = occasionList,
            ),
        )
        setNextAlarm(earliest(plan.nextAlarm, now.plus(RETRY_AFTER_FAILURE)))
        plan.cancel.forEach(notifier::cancel)
        if (plan.clearNotified.isNotEmpty()) personDao.clearLastNotifiedDueOn(plan.clearNotified.toList())
        val done = mutableSetOf<NotificationKey>()
        var marked = plan.markNotified.isEmpty()
        suspend fun record() {
            if (!marked && plan.canMarkAfter(done)) {
                personDao.setLastNotifiedDueOn(plan.markNotified.toList(), plan.today)
                marked = true
            }
            preferences.updateDeviceState { it.copy(notifications = plan.stateAfter(done).copy(restoreToken = token)) }
        }
        record()
        val posted = pacer.pace(plan.post) { notification ->
            notifier.post(notification, how, now)
            // Posted: recorded even when the run is being cancelled (the receivers' timeout).
            withContext(NonCancellable) {
                done += notification.key
                record()
            }
        }
        setNextAlarm(
            if (posted < plan.post.size) earliest(plan.nextAlarm, clock.instant().plus(FOLLOW_UP)) else plan.nextAlarm,
        )
        return plan
    }

    private fun earliest(planned: Instant?, other: Instant): Instant =
        if (planned == null || other.isBefore(planned)) other else planned

    /**
     * Runs a Restore's [replace] (the swap) with no `reconcile()` in progress, so none plans with the
     * old data and writes to the new, then takes down the notifications showing for the old data.
     * The next `reconcile()` sees the new restore token and starts from nothing.
     */
    suspend fun whileDataIsReplaced(replace: suspend () -> Unit) = mutex.withLock {
        replace()
        notifier.showing()?.forEach(notifier::cancel)
    }

    /**
     * A notification action ([target]): runs [act] only when the action was posted for the data
     * there is now (its restore token is the live one). Serialised with `reconcile()` and Restore's
     * swap. Returns whether it ran.
     */
    suspend fun runAction(target: NotificationActionTarget, act: suspend () -> Unit): Boolean = mutex.withLock {
        if (target.restoreToken != statusDao.restoreToken()) return@withLock false
        act()
        true
    }

    private companion object {
        /** After a run that failed or timed out: try again then (or at the plan's time, if sooner). */
        val RETRY_AFTER_FAILURE: Duration = Duration.ofMinutes(15)

        /** When the pacing budget ran out: the rest is posted by a run this much later. */
        val FOLLOW_UP: Duration = Duration.ofSeconds(10)
    }
}
