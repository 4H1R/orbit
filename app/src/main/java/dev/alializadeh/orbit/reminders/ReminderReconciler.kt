package dev.alializadeh.orbit.reminders

import dev.alializadeh.orbit.backup.BackupStatusDao
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.person.PersonDao
import dev.alializadeh.orbit.upcoming.UpcomingRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * The one idempotent `reconcile()` (ticket 11): snapshots the data, runs [ReminderPlanner], then
 * cancels, posts, records "last notified as Due" with the targeted update, stores the device's
 * [NotificationState], and sets the one alarm for the next time. Runs are serialised; every
 * trigger (the alarm, boot, time and zone changes, app updates, exact alarms granted, app start,
 * data edits, preference changes, Restore, notification actions) calls it.
 */
@Singleton
class ReminderReconciler @Inject constructor(
    private val upcoming: UpcomingRepository,
    private val preferences: PreferencesRepository,
    private val personDao: PersonDao,
    private val notifier: ReminderNotifier,
    private val alarm: ReminderAlarm,
    private val rendering: NotificationRenderingPolicy,
    private val clock: Clock,
    private val statusDao: BackupStatusDao,
) {
    private val mutex = Mutex()

    suspend fun reconcile(): ReminderPlan = mutex.withLock {
        val now = clock.instant()
        val zone = clock.zone
        val backedUp = preferences.preferences.first()
        val device = preferences.deviceState.first()
        val token = statusDao.restoreToken()
        val people = upcoming.observePeople().first()
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
            ),
        )
        plan.cancel.forEach(notifier::cancel)
        plan.post.forEach { notifier.post(it, how, now) }
        if (plan.markNotified.isNotEmpty()) personDao.setLastNotifiedDueOn(plan.markNotified.toList(), plan.today)
        preferences.updateDeviceState { it.copy(notifications = plan.state.copy(restoreToken = token)) }
        alarm.set(plan.nextAlarm)
        plan
    }

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
}
