package dev.alializadeh.orbit.reminders

import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.occasion.OccasionWithAudience
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.upcoming.DuePerson
import dev.alializadeh.orbit.upcoming.UpcomingFeed
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.security.MessageDigest
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
    /**
     * How notifications are rendered now (App lock decides). When it differs from how the showing
     * ones were posted ([NotificationState.renderedAs]), they're quietly re-posted.
     */
    val rendering: NotificationRendering = NotificationRendering(),
    /** Every Occasion with who it's for (v1.1): they reach [people]. */
    val occasions: List<OccasionWithAudience> = emptyList(),
)

/** What `reconcile()` does: post, cancel, mark People as notified, store [state], set the alarm. */
data class ReminderPlan(
    /** "Today" in the zone: the day [markNotified] is recorded with. */
    val today: LocalDate,
    val post: List<PlannedNotification>,
    val cancel: List<NotificationKey>,
    /**
     * People to record as "last notified as Due" today (named in the digest, or left out of it).
     * Recorded only once the new digest, if there is one, is posted ([canMarkAfter]).
     */
    val markNotified: Set<Long>,
    /** The one alarm: the next moment something may be posted or change; null when nothing ever will. */
    val nextAlarm: Instant?,
    /** The state once everything in [post] is posted; [stateAfter] when only some of it was. */
    val state: NotificationState,
    /**
     * People with a "last notified as Due" marker who aren't Due now: it's cleared, so the next time
     * they become Due is a new Due period (`KeepInTouch`).
     */
    val clearNotified: Set<Long> = emptySet(),
    /** The state this was planned from: what [stateAfter] keeps for whatever wasn't posted. */
    val previous: NotificationState = NotificationState(),
) {
    /** Whether [markNotified] may be recorded once [done] are posted: there's no new digest, or it's among them. */
    fun canMarkAfter(done: Set<NotificationKey>): Boolean =
        post.none { it is PlannedDigest && !it.silent } || NotificationKey.Digest in done

    /**
     * The state to store when only [done] of [post] were posted (a run that failed, timed out or
     * ran out of its pacing budget part way): what wasn't posted stays as it was in [previous], so
     * the next run posts it then, and what was is never posted (or alerts) again.
     */
    fun stateAfter(done: Set<NotificationKey>): NotificationState {
        val missed = post.filter { it.key !in done }
        if (missed.isEmpty()) return state
        val missedDates = missed.mapNotNull { (it.key as? NotificationKey.DateReminder)?.dateId }.toSet()
        val missedDigest = missed.filterIsInstance<PlannedDigest>().singleOrNull()
        val postedBefore = previous.postedReminders.toSet()
        val missedOccasions = missed.mapNotNull { (it.key as? NotificationKey.OccasionReminder)?.occasionId }.toSet()
        val occasionsPostedBefore = previous.postedOccasionReminders.toSet()
        return state.copy(
            postedReminders = state.postedReminders.filter { it.dateId !in missedDates || it in postedBefore },
            showingReminders = state.showingReminders.filter { it.dateId !in missedDates } +
                previous.showingReminders.filter { it.dateId in missedDates },
            postedOccasionReminders = state.postedOccasionReminders.filter {
                it.occasionId !in missedOccasions || it in occasionsPostedBefore
            },
            showingOccasionReminders = state.showingOccasionReminders.filter { it.occasionId !in missedOccasions } +
                previous.showingOccasionReminders.filter { it.occasionId in missedOccasions },
            lastDigestEpochDay = if (missedDigest?.silent == false) previous.lastDigestEpochDay else state.lastDigestEpochDay,
            digestPersonIds = if (missedDigest != null) previous.digestPersonIds else state.digestPersonIds,
            renderedAs = if (missedDigest != null) previous.renderedAs else state.renderedAs,
        )
    }
}

/** A notification to post now. */
sealed interface PlannedNotification {
    val key: NotificationKey

    /**
     * Replaces one that's showing without alerting again (the digest after someone left it, a
     * Reminder whose text changed, either after App lock changed how they render).
     */
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
    /** Re-posted (rendered another way, or its text changed) without alerting again. */
    override val silent: Boolean = false,
) : PlannedNotification {
    override val key: NotificationKey get() = NotificationKey.DateReminder(date.id)
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
 * - A showing Reminder is quietly re-posted when what it shows would change
 *   ([ShowingReminder.content]): the days left ("in 7 days" the next day), the Person's names or
 *   photo, their open Gift ideas, the years count, or how it renders. While one shows for a later
 *   day, the alarm also wakes at the next midnight so "in 7 days" becomes "in 6 days".
 *
 * **Cadence digest**
 * - At most one a day, by the local day it ran on: at the Reminder time, or earlier in the day
 *   when the last one ran before yesterday (a missed slot caught up). A day's catch-up run is that
 *   day's digest: People who become Due later that day wait for the next day's.
 * - Names the People who are Due and haven't been notified for this Due period
 *   (`DueState.notifiedThisPeriod`). An Interaction or a Snooze ending starts a new period; a
 *   Cadence or Ring change alone doesn't. The marker of anyone not Due is cleared
 *   ([ReminderPlan.clearNotified]), so becoming Due again later is notified once more.
 * - A Person with an on-the-day Reminder today is left out, and counts as notified.
 * - Once a named Person stops being Due (Interaction, Snooze, Archive, delete), they leave the
 *   digest: it's cancelled when nobody's left, else quietly re-posted with the rest.
 *
 * A channel that's off posts nothing and records nothing, so turning it back on catches up (the
 * same day for Reminders).
 *
 * **Rendering** (App lock): when [PlannerInput.rendering] differs from how the showing
 * notifications were posted (each Reminder's [ShowingReminder.content], the digest's
 * [NotificationState.renderedAs]), each one still showing is quietly re-posted as it is, so turning
 * App lock on takes names off the shade at once.
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
        val peopleById = active.associateBy(UpcomingPerson::id)
        for (person in active) {
            for (date in person.importantDates) {
                datesById[date.id] = date
                val leadTimes = date.leadTimes ?: input.defaultLeadTimes
                val occurrence = date.date.nextOccurrence(today)
                val following = date.date.nextOccurrence(occurrence.date.plusDays(1))
                if (canRemind && occurrence.date == today && LeadTime.OnTheDay in leadTimes) onTheDayToday += person.id
                for (leadTime in leadTimes.sortedBy(LeadTime::ordinal)) {
                    val day = leadTime.reminderDate(occurrence.date, date.date.calendar)
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
                    consider(slot(leadTime.reminderDate(following.date, date.date.calendar)))
                }
            }
        }

        val rerender = (input.state.renderedAs ?: NotificationRendering()) != input.rendering
        val post = mutableListOf<PlannedNotification>()
        val cancel = mutableListOf<NotificationKey>()
        val showing = mutableListOf<ShowingReminder>()
        for (shown in input.state.showingReminders) {
            if (shown.dateId in dueNow) continue // replaced below
            val date = datesById[shown.dateId]
            if (date == null || fingerprint(date) != shown.fingerprint || today.toEpochDay() > shown.occurrenceEpochDay) {
                cancel += NotificationKey.DateReminder(shown.dateId)
                continue
            }
            val person = peopleById[shown.personId]
            val occurrence = date.date.nextOccurrence(today)
            if (person == null || occurrence.date.toEpochDay() != shown.occurrenceEpochDay) {
                showing += shown
                continue
            }
            // The nearest lead time posted for this occurrence is the one showing.
            val leadTime = posted
                .filter { it.dateId == date.id && it.occurrenceEpochDay == shown.occurrenceEpochDay }
                .minByOrNull { it.leadTime.ordinal }?.leadTime ?: LeadTime.OnTheDay
            val again = PlannedReminder(
                person = person,
                date = date,
                occurrence = occurrence,
                leadTime = leadTime,
                daysAway = ChronoUnit.DAYS.between(today, occurrence.date).toInt(),
                expiresAt = occurrence.date.plusDays(1).atStartOfDay(zone).toInstant(),
                silent = true,
            )
            val content = content(again, input.rendering)
            // What it shows would change (a new day, an edit, App lock): quietly re-posted.
            if (content != shown.content) post += again
            showing += shown.copy(content = content)
        }
        for (reminder in dueNow.values) {
            post += reminder
            showing += ShowingReminder(
                dateId = reminder.date.id,
                personId = reminder.person.id,
                occurrenceEpochDay = reminder.occurrence.date.toEpochDay(),
                fingerprint = fingerprint(reminder.date),
                content = content(reminder, input.rendering),
            )
        }
        // "In 7 days" turns into "in 6 days" at midnight. On-the-day Reminders expire then by
        // themselves (`setTimeoutAfter`), so only a later day's Reminder needs the wake.
        if (showing.any { it.occurrenceEpochDay > today.toEpochDay() }) {
            consider(today.plusDays(1).atStartOfDay(zone).toInstant())
        }

        // Occasion Reminders, by the same rules.
        val occasionPlan = OccasionReminderPlanner.plan(
            occasions = input.occasions,
            people = input.people,
            defaultLeadTimes = input.defaultLeadTimes,
            today = today,
            zone = zone,
            timeHasCome = timeHasCome,
            canRemind = ReminderChannel.Occasions in input.channelsOn,
            state = input.state,
            rendering = input.rendering,
            slot = ::slot,
        )
        post += occasionPlan.post
        cancel += occasionPlan.cancel
        occasionPlan.wakeAt.forEach(::consider)

        // The Cadence digest: at most one per local day.
        val due = UpcomingFeed.of(input.people, today).due
        val lastDigest = input.state.lastDigestEpochDay?.let(LocalDate::ofEpochDay)
        val digestDue = canDigest && when {
            lastDigest == null -> timeHasCome
            !lastDigest.isBefore(today) -> false
            // Before the Reminder time only to catch up a slot missed on an earlier day.
            else -> timeHasCome || lastDigest.isBefore(today.minusDays(1))
        }
        val markNotified = mutableSetOf<Long>()
        var digestRanFor = lastDigest
        var digestIds = input.state.digestPersonIds
        var newDigest = false
        if (digestDue) {
            val becameDue = due.filterNot { it.state.notifiedThisPeriod }
            markNotified += becameDue.map { it.person.id }
            val named = becameDue.filterNot { it.person.id in onTheDayToday }
            digestRanFor = today
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
                stillDue.size < digestIds.size || rerender -> {
                    post += PlannedDigest(stillDue, today, silent = true)
                    digestIds = stillDue.map { it.person.id }
                }
            }
        }
        if (canDigest) {
            // The first slot still to come on a day the digest hasn't run on.
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

        // Not Due (any more): the next time they are is a new Due period.
        val clearNotified = input.people
            .filter { it.keepInTouch.lastNotifiedDueOn != null && !it.keepInTouch.on(today).isDue }
            .map(UpcomingPerson::id)
            .toSet()

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
                renderedAs = input.rendering,
                postedOccasionReminders = occasionPlan.posted,
                showingOccasionReminders = occasionPlan.showing,
            ),
            clearNotified = clearNotified,
            previous = input.state,
        )
    }

    /**
     * What [reminder] shows when rendered as [rendering], as a short hash: the days left, the
     * years count, the Person's names, photo, primary Contact method and open Gift ideas, and the
     * rendering. A showing Reminder whose content changes is quietly re-posted. Hashed, so no
     * names are copied into the device's notification state.
     */
    fun content(reminder: PlannedReminder, rendering: NotificationRendering): String {
        val person = reminder.person
        val parts = listOf(
            reminder.daysAway,
            reminder.occurrence.yearsCount,
            person.names,
            person.photoFileName,
            person.primaryContactMethod?.let { "${it.type}:${it.value}" },
            person.openGiftIdeas,
            rendering,
        ).joinToString(CONTENT_SEPARATOR)
        val hash = MessageDigest.getInstance("SHA-256").digest(parts.toByteArray())
        return hash.take(CONTENT_HASH_BYTES).joinToString("") { ((it.toInt() and 0xff) or 0x100).toString(16).substring(1) }
    }

    private const val CONTENT_SEPARATOR = "\u0000"
    private const val CONTENT_HASH_BYTES = 8

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
