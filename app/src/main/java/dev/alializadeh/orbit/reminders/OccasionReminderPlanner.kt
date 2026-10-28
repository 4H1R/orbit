package dev.alializadeh.orbit.reminders

import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.occasion.OccasionWithAudience
import dev.alializadeh.orbit.occasion.audience
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** An Occasion Reminder: [leadTime] before (or on) [occurrence] of [occasion], reaching [people]. */
data class PlannedOccasionReminder(
    val occasion: OccasionWithAudience,
    val people: List<UpcomingPerson>,
    val occurrence: RecurringDate.Occurrence,
    val leadTime: LeadTime,
    val daysAway: Int,
    /** The end of the occurrence's day: the Reminder expires then. */
    val expiresAt: Instant,
    override val silent: Boolean = false,
) : PlannedNotification {
    override val key: NotificationKey get() = NotificationKey.OccasionReminder(occasion.id)
    val isOnTheDay: Boolean get() = daysAway == 0
}

/** What the Occasion part of a plan does: [post], [cancel], the next times to wake, and its state. */
internal data class OccasionPlan(
    val post: List<PlannedOccasionReminder>,
    val cancel: List<NotificationKey>,
    val wakeAt: List<Instant>,
    val posted: List<PostedOccasionReminder>,
    val showing: List<ShowingOccasionReminder>,
)

/**
 * Occasion Reminders (PRD v1.1), with the same rules as Important dates' (see [ReminderPlanner]):
 * one per lead time (the Occasion's own or the default ones), at the Reminder time on its day or
 * later that day if missed, never twice, one slot per Occasion, cancelled when the Occasion is
 * edited or deleted and at the end of its day, quietly re-posted when what it shows changes (the
 * days left, how many People it reaches, the name, App lock). An Occasion that reaches nobody
 * posts nothing.
 */
internal object OccasionReminderPlanner {

    fun plan(
        occasions: List<OccasionWithAudience>,
        people: List<UpcomingPerson>,
        defaultLeadTimes: Set<LeadTime>,
        today: LocalDate,
        zone: ZoneId,
        timeHasCome: Boolean,
        canRemind: Boolean,
        state: NotificationState,
        rendering: NotificationRendering,
        slot: (LocalDate) -> Instant,
    ): OccasionPlan {
        val keepFrom = today.minusDays(POSTED_KEPT_DAYS).toEpochDay()
        val posted = state.postedOccasionReminders.filter { it.occurrenceEpochDay >= keepFrom }.toMutableList()
        val dueNow = mutableMapOf<Long, PlannedOccasionReminder>()
        val wakeAt = mutableListOf<Instant>()
        val byId = occasions.associateBy(OccasionWithAudience::id)
        fun planned(occasion: OccasionWithAudience, reached: List<UpcomingPerson>, occurrence: RecurringDate.Occurrence, leadTime: LeadTime, silent: Boolean) =
            PlannedOccasionReminder(
                occasion = occasion,
                people = reached,
                occurrence = occurrence,
                leadTime = leadTime,
                daysAway = ChronoUnit.DAYS.between(today, occurrence.date).toInt(),
                expiresAt = occurrence.date.plusDays(1).atStartOfDay(zone).toInstant(),
                silent = silent,
            )

        for (occasion in occasions) {
            val reached = occasion.audience(people)
            val date = occasion.occasion.date
            val leadTimes = occasion.occasion.leadTimes ?: defaultLeadTimes
            val occurrence = date.nextOccurrence(today)
            val following = date.nextOccurrence(occurrence.date.plusDays(1))
            for (leadTime in leadTimes.sortedBy(LeadTime::ordinal)) {
                val day = leadTime.reminderDate(occurrence.date, date.calendar)
                val key = PostedOccasionReminder(occasion.id, occurrence.date.toEpochDay(), leadTime)
                if (canRemind && reached.isNotEmpty() && day == today && timeHasCome && key !in posted) {
                    if (occasion.id !in dueNow) dueNow[occasion.id] = planned(occasion, reached, occurrence, leadTime, silent = false)
                    posted += key
                }
                wakeAt += slot(day)
                wakeAt += slot(leadTime.reminderDate(following.date, date.calendar))
            }
        }

        val post = mutableListOf<PlannedOccasionReminder>()
        val cancel = mutableListOf<NotificationKey>()
        val showing = mutableListOf<ShowingOccasionReminder>()
        for (shown in state.showingOccasionReminders) {
            if (shown.occasionId in dueNow) continue // replaced below
            val occasion = byId[shown.occasionId]
            val reached = occasion?.audience(people).orEmpty()
            if (occasion == null || fingerprint(occasion) != shown.fingerprint || today.toEpochDay() > shown.occurrenceEpochDay || reached.isEmpty()) {
                cancel += NotificationKey.OccasionReminder(shown.occasionId)
                continue
            }
            val occurrence = occasion.occasion.date.nextOccurrence(today)
            if (occurrence.date.toEpochDay() != shown.occurrenceEpochDay) {
                showing += shown
                continue
            }
            val leadTime = posted
                .filter { it.occasionId == occasion.id && it.occurrenceEpochDay == shown.occurrenceEpochDay }
                .minByOrNull { it.leadTime.ordinal }?.leadTime ?: LeadTime.OnTheDay
            val again = planned(occasion, reached, occurrence, leadTime, silent = true)
            val content = content(again, rendering)
            if (content != shown.content) post += again
            showing += shown.copy(content = content)
        }
        for (reminder in dueNow.values) {
            post += reminder
            showing += ShowingOccasionReminder(
                occasionId = reminder.occasion.id,
                occurrenceEpochDay = reminder.occurrence.date.toEpochDay(),
                fingerprint = fingerprint(reminder.occasion),
                content = content(reminder, rendering),
            )
        }
        // "In 7 days" turns into "in 6 days" at midnight.
        if (showing.any { it.occurrenceEpochDay > today.toEpochDay() }) {
            wakeAt += today.plusDays(1).atStartOfDay(zone).toInstant()
        }
        return OccasionPlan(post, cancel, wakeAt, posted, showing)
    }

    /** What [reminder] shows, as a short hash: the days left, the name, how many it reaches, the rendering. */
    fun content(reminder: PlannedOccasionReminder, rendering: NotificationRendering): String {
        val occasion = reminder.occasion.occasion
        val parts = listOf(reminder.daysAway, occasion.builtIn, occasion.label, reminder.people.size, rendering).joinToString("\u0000")
        val hash = MessageDigest.getInstance("SHA-256").digest(parts.toByteArray())
        return hash.take(CONTENT_HASH_BYTES).joinToString("") { ((it.toInt() and 0xff) or 0x100).toString(16).substring(1) }
    }

    /** The Occasion's day and Reminders as set: an edit to them cancels its showing Reminder. */
    fun fingerprint(occasion: OccasionWithAudience): String = with(occasion.occasion) {
        listOf(
            builtIn?.name.orEmpty(),
            label.orEmpty(),
            date.calendar.name,
            date.month,
            date.day,
            leadTimes?.sortedBy(LeadTime::ordinal)?.joinToString(",") ?: "default",
        ).joinToString("|")
    }

    private const val CONTENT_HASH_BYTES = 8
    private const val POSTED_KEPT_DAYS = 7L
}
