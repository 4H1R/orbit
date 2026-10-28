package dev.alializadeh.orbit.reminders

import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.upcoming.DuePerson
import dev.alializadeh.orbit.upcoming.UpcomingFeed
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Everything the planner decides from: a snapshot of the data, the time, and what's been notified. */
data class PlannerInput(
    /** Every Person (Archived ones too: they produce nothing). */
    val people: List<UpcomingPerson>,
    val reminderTime: LocalTime,
    val defaultLeadTimes: Set<LeadTime>,
    val now: Instant,
    val zone: ZoneId,
    /** The device's notification state, already trimmed to what's still showing. */
    val state: NotificationState,
    /** The channels Orbit can post on now (notifications allowed and the channel not blocked). */
    val channelsOn: Set<ReminderChannel> = ReminderChannel.entries.toSet(),
)

/** What `reconcile()` does: post, cancel, mark People as notified, store [state], set the alarm. */
data class ReminderPlan(
    /** "Today" in the zone: the day [markNotified] is recorded with. */
    val today: LocalDate,
    val post: List<PlannedNotification>,
    val cancel: List<NotificationKey>,
    /** People to record as "last notified as Due" today (named in the digest, or left out of it). */
    val markNotified: Set<Long>,
    /** The one alarm: the next moment something may be posted; null when nothing ever will. */
    val nextAlarm: Instant?,
    val state: NotificationState,
)

/** A notification to post now. */
sealed interface PlannedNotification {
    val key: NotificationKey

    /** Replaces one that's showing without alerting again (the digest after someone left it). */
    val silent: Boolean
}

/** A Reminder: [leadTime] before (or on) [occurrence] of [date]. */
data class PlannedReminder(
    val person: UpcomingPerson,
    val date: ImportantDate,
    val occurrence: RecurringDate.Occurrence,
    val leadTime: LeadTime,
    /** Days from today to the occurrence: 0 is the on-the-day Reminder, the only one with actions. */
    val daysAway: Int,
    /** The end of the occurrence's day: the Reminder expires then. */
    val expiresAt: Instant,
) : PlannedNotification {
    override val key: NotificationKey get() = NotificationKey.DateReminder(date.id)
    override val silent: Boolean get() = false
    val isOnTheDay: Boolean get() = daysAway == 0
}

/** The Cadence digest naming [people] (Upcoming's Due order). One Person gets actions. */
data class PlannedDigest(
    val people: List<DuePerson>,
    val today: LocalDate,
    override val silent: Boolean = false,
) : PlannedNotification {
    override val key: NotificationKey get() = NotificationKey.Digest
}

/**
 * Every rule of what Orbit notifies and when (ticket 07), as one pure function: `reconcile()`
 * applies its result. Times are local: a Reminder is "[LeadTime] before the day the date falls on,
 * at the Reminder time" in the phone's zone, so DST and zone changes move nothing.
 *
 * **Reminders**
 * - One per lead time of each Important date of everyone not Archived. A date's lead times are its
 *   own, or the default ones; an empty set is No reminders. Lead times count back from the day
 *   the date actually falls on that year (a missing day falls on the month's last day, a Solar
 *   Hijri date falls on its own Calendar's day).
 * - Posted at the Reminder time on its day, or later that same day if missed (phone off, date
 *   added after the time); a Reminder from an earlier day is dropped. So a date added after a lead
 *   time has passed gets only its later Reminders. Never posted twice.
 * - Each date has one notification slot; a later Reminder replaces the earlier one. It's cancelled
 *   when the date is edited or deleted, its Person archived or deleted, and at the end of the
 *   date's day (expiry).
 *
 * **Cadence digest**
 * - At most one a day, at the Reminder time; a missed one goes out on the next run.
 * - Names the People who are Due and haven't been notified for this Due period
 *   (`DueState.notifiedThisPeriod`: became Due since they were last notified). Snooze ending
 *   starts a new period.
 * - A Person with an on-the-day Reminder today is left out, and counts as notified.
 * - Once a named Person stops being Due (Interaction, Snooze, Archive, delete), they leave the
 *   digest: it's cancelled when nobody's left, else quietly re-posted with the rest.
 *
 * A channel that's off posts nothing and records nothing, so turning it back on catches up (the
 * same day for Reminders).
 */
object ReminderPlanner {

    fun plan(input: PlannerInput): ReminderPlan {
        val zone = input.zone
        val now = input.now
        val today = now.atZone(zone).toLocalDate()
        fun slot(day: LocalDate): Instant = day.atTime(input.reminderTime).atZone(zone).toInstant()
        val reminderTimeToday = slot(today)
        val timeHasCome = !now.isBefore(reminderTimeToday)
        val active = input.people.filterNot(UpcomingPerson::isArchived)
        val canRemind = ReminderChannel.ImportantDates in input.channelsOn
        val canDigest = ReminderChannel.KeepInTouch in input.channelsOn

        var nextAlarm: Instant? = null
        fun consider(instant: Instant) {
            if (instant.isAfter(now) && (nextAlarm == null || instant.isBefore(nextAlarm))) nextAlarm = instant
        }

        // Reminders.
        // Kept a week past their day, so setting the clock back (or a zone change) can't repost them.
        val keepFrom = today.minusDays(POSTED_KEPT_DAYS).toEpochDay()
        val posted = input.state.postedReminders.filter { it.occurrenceEpochDay >= keepFrom }.toMutableList()
        val dueNow = mutableMapOf<Long, PlannedReminder>()
        val onTheDayToday = mutableSetOf<Long>()
        val datesById = mutableMapOf<Long, ImportantDate>()
        for (person in active) {
            for (date in person.importantDates) {
                datesById[date.id] = date
                val leadTimes = date.leadTimes ?: input.defaultLeadTimes
                val occurrence = date.date.nextOccurrence(today)
                val following = date.date.nextOccurrence(occurrence.date.plusDays(1))
                if (canRemind && occurrence.date == today && LeadTime.OnTheDay in leadTimes) onTheDayToday += person.id
                for (leadTime in leadTimes.sortedBy(LeadTime::ordinal)) {
                    val day = leadTime.reminderDate(occurrence.date)
                    val key = PostedReminder(date.id, occurrence.date.toEpochDay(), leadTime)
                    if (canRemind && day == today && timeHasCome && key !in posted) {
                        // The nearest lead time wins if two fall on one day; both count as posted.
                        if (date.id !in dueNow) {
                            dueNow[date.id] = PlannedReminder(
                                person = person,
                                date = date,
                                occurrence = occurrence,
                                leadTime = leadTime,
                                daysAway = ChronoUnit.DAYS.between(today, occurrence.date).toInt(),
                                expiresAt = occurrence.date.plusDays(1).atStartOfDay(zone).toInstant(),
                            )
                        }
                        posted += key
                    }
                    consider(slot(day))
                    consider(slot(leadTime.reminderDate(following.date)))
                }
            }
        }

        val post = mutableListOf<PlannedNotification>()
        val cancel = mutableListOf<NotificationKey>()
        val showing = mutableListOf<ShowingReminder>()
        for (shown in input.state.showingReminders) {
            if (shown.dateId in dueNow) continue // replaced below
            val date = datesById[shown.dateId]
            val stale = date == null ||
                fingerprint(date) != shown.fingerprint ||
                today.toEpochDay() > shown.occurrenceEpochDay
            if (stale) cancel += NotificationKey.DateReminder(shown.dateId) else showing += shown
        }
        for (reminder in dueNow.values) {
            post += reminder
            showing += ShowingReminder(
                dateId = reminder.date.id,
                personId = reminder.person.id,
                occurrenceEpochDay = reminder.occurrence.date.toEpochDay(),
                fingerprint = fingerprint(reminder.date),
            )
        }

        // The Cadence digest.
        val due = UpcomingFeed.of(input.people, today).due
        val currentSlot = if (timeHasCome) today else today.minusDays(1)
        val lastDigest = input.state.lastDigestEpochDay?.let(LocalDate::ofEpochDay)
        val digestDue = canDigest && if (lastDigest == null) timeHasCome else lastDigest.isBefore(currentSlot)
        val markNotified = mutableSetOf<Long>()
        var digestRanFor = lastDigest
        var digestIds = input.state.digestPersonIds
        var newDigest = false
        if (digestDue) {
            val becameDue = due.filterNot { it.state.notifiedThisPeriod }
            markNotified += becameDue.map { it.person.id }
            val named = becameDue.filterNot { it.person.id in onTheDayToday }
            digestRanFor = currentSlot
            if (named.isNotEmpty()) {
                post += PlannedDigest(named, today)
                digestIds = named.map { it.person.id }
                newDigest = true
            }
        }
        if (!newDigest && digestIds.isNotEmpty()) {
            val stillDue = due.filter { it.person.id in digestIds }
            when {
                stillDue.isEmpty() -> {
                    cancel += NotificationKey.Digest
                    digestIds = emptyList()
                }
                stillDue.size < digestIds.size -> {
                    post += PlannedDigest(stillDue, today, silent = true)
                    digestIds = stillDue.map { it.person.id }
                }
            }
        }
        if (canDigest) {
            // The first slot still to come that the digest hasn't run for.
            val firstSlot = if (timeHasCome || (digestRanFor != null && !digestRanFor.isBefore(today))) {
                today.plusDays(1)
            } else {
                today
            }
            val nextDigestDay = active.mapNotNull { person ->
                if (person.id in markNotified) return@mapNotNull null
                val state = person.keepInTouch.on(today)
                when {
                    state.isDue -> if (state.notifiedThisPeriod) null else firstSlot
                    else -> state.dueOn?.let { maxOf(it, firstSlot) }
                }
            }.minOrNull()
            nextDigestDay?.let { consider(slot(it)) }
        }

        return ReminderPlan(
            today = today,
            post = post,
            cancel = cancel.filterNot { key -> post.any { it.key == key } },
            markNotified = markNotified,
            nextAlarm = nextAlarm,
            state = NotificationState(
                postedReminders = posted,
                showingReminders = showing,
                lastDigestEpochDay = digestRanFor?.toEpochDay(),
                digestPersonIds = digestIds,
            ),
        )
    }

    /** How long past its day a posted Reminder is remembered. */
    private const val POSTED_KEPT_DAYS = 7L

    /** The date as the user entered it: any edit changes it, and cancels its showing Reminder. */
    fun fingerprint(date: ImportantDate): String = listOf(
        date.type.name,
        date.label.orEmpty(),
        date.date.calendar.name,
        date.date.month,
        date.date.day,
        date.date.year ?: "",
        date.leadTimes?.sortedBy(LeadTime::ordinal)?.joinToString(",") ?: "default",
    ).joinToString("|")
}
