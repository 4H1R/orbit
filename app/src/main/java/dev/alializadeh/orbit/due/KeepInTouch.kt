package dev.alializadeh.orbit.due

import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.cadenceIn
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Everything Due is worked out from for one Person (glossary: Cadence, Due, Snooze). This and
 * [DueState] are the one place the rules live; every screen, the Cadence digest (slice 6) and the
 * Orbit view (slice 9) read Due through them. Build one with [Person.keepInTouch].
 *
 * The rules:
 * - A Person is Due once the days since their latest Interaction of any type (or since they were
 *   added, if there's none) reach their Cadence. There's no grace period.
 * - The Cadence is their own if they override it, else their Ring's; none means never Due.
 *   Changes to either take effect at once, since nothing about Due is stored.
 * - A Snooze only pushes back when they're next Due: until [snoozedUntil] they aren't Due, and
 *   they're Due again on that day if they're still behind. It isn't an Interaction.
 * - Archived People are never Due.
 * - A Due period starts the day after their latest Interaction (or the day they were added), or
 *   when a Snooze that held them back ends. They're notified once per period
 *   ([DueState.notifiedThisPeriod]): a Cadence or Ring change alone doesn't start a new one, so
 *   lengthening the Cadence of someone who stays Due doesn't notify them again. When a change makes
 *   them stop being Due, `reconcile()` clears [lastNotifiedDueOn] (as it does for every Person who
 *   isn't Due), so becoming Due again later is a new time they "become Due" and is notified once,
 *   like a Snooze ending. That also covers an Interaction logged with an earlier date after the
 *   digest named them.
 */
data class KeepInTouch(
    /** Their Cadence, own or from the Ring; null is "No Cadence". */
    val cadence: Cadence?,
    /** The day they were added (in the phone's zone): the start when there's no Interaction. */
    val addedOn: LocalDate,
    /** Their latest Interaction's date, of any type. */
    val lastInteraction: LocalDate?,
    val snoozedUntil: LocalDate? = null,
    /** Slice 6's "last notified as Due" marker (cleared by `reconcile()` while they aren't Due). */
    val lastNotifiedDueOn: LocalDate? = null,
    val isArchived: Boolean = false,
) {
    /** What the days are counted from: the latest Interaction, else the day they were added. */
    val lastContact: LocalDate get() = lastInteraction ?: addedOn

    /** The day their Cadence makes them Due, before any Snooze; null when they never are. */
    val cadenceDueOn: LocalDate? get() = if (isArchived) null else cadence?.dueDate(lastContact)

    /** The day they're (or were) Due, Snooze included; null when they never are. */
    val dueOn: LocalDate?
        get() {
            val due = cadenceDueOn ?: return null
            val snooze = snoozedUntil ?: return due
            return maxOf(due, snooze)
        }

    /**
     * The first day of the Due period they're in: the day a Snooze that held them back ended, else
     * the day after their latest Interaction (or the day they were added). Null when never Due.
     */
    val periodStart: LocalDate?
        get() {
            val cadenceDue = cadenceDueOn ?: return null
            val snooze = snoozedUntil
            return if (snooze != null && snooze.isAfter(cadenceDue)) snooze else lastContact.plusDays(1)
        }

    /** Where they stand on [today]. */
    fun on(today: LocalDate): DueState {
        val dueOn = dueOn
        val isDue = dueOn != null && !today.isBefore(dueOn)
        val cadenceDueOn = cadenceDueOn
        // A Snooze shows only while it's what keeps them from being Due.
        val activeSnooze = snoozedUntil?.takeIf { cadenceDueOn != null && it.isAfter(today) && it.isAfter(cadenceDueOn) }
        val since = ChronoUnit.DAYS.between(lastContact, today).toInt().coerceAtLeast(0)
        val start = periodStart
        val period = cadenceDueOn?.let { ChronoUnit.DAYS.between(lastContact, it).toInt() }
        return DueState(
            cadence = if (isArchived) null else cadence,
            lastInteraction = lastInteraction,
            daysSinceContact = since,
            isDue = isDue,
            dueOn = dueOn,
            daysOverdue = if (isDue) ChronoUnit.DAYS.between(dueOn, today).toInt() else 0,
            snoozedUntil = activeSnooze,
            notifiedThisPeriod = isDue && start != null && lastNotifiedDueOn != null && !lastNotifiedDueOn.isBefore(start),
            progress = if (period == null || period <= 0) 0f else (since.toFloat() / period).coerceIn(0f, 1f),
        )
    }
}

/** A Person's keep-in-touch standing on one day: what every screen shows about Due. */
data class DueState(
    /** Their Cadence; null for "No Cadence" (and for Archived People). */
    val cadence: Cadence?,
    /** Their latest Interaction's date; null for "No interactions yet". */
    val lastInteraction: LocalDate?,
    /** Days since their latest Interaction, or since they were added. */
    val daysSinceContact: Int,
    val isDue: Boolean,
    /** When Due, the day they became Due; otherwise the day they will (null: never). */
    val dueOn: LocalDate?,
    /** How many days past [dueOn] they are: 0 on the day they become Due, and when not Due. */
    val daysOverdue: Int,
    /** The day a Snooze ends while it's what holds them back; null when not Snoozed. */
    val snoozedUntil: LocalDate?,
    /** Slice 6: the digest already named them (or left them out for an on-the-day Reminder) this Due period. */
    val notifiedThisPeriod: Boolean = false,
    /** How far through their Cadence they are, 0 to 1 (Person detail's wavy progress). */
    val progress: Float = 0f,
) {
    val isSnoozed: Boolean get() = snoozedUntil != null
}

/** This Person's [KeepInTouch] facts in [ring], with their latest Interaction on [lastInteraction]. */
fun Person.keepInTouch(ring: Ring?, lastInteraction: LocalDate?, zone: ZoneId): KeepInTouch = KeepInTouch(
    cadence = cadenceIn(ring),
    addedOn = createdAt.atZone(zone).toLocalDate(),
    lastInteraction = lastInteraction,
    snoozedUntil = snoozedUntil,
    lastNotifiedDueOn = lastNotifiedDueOn,
    isArchived = isArchived,
)

/** How long a Snooze lasts, from today. */
enum class SnoozeDuration {
    ThreeDays,
    OneWeek,
    OneMonth,
    ;

    /** The day a Snooze started on [today] ends (when they can be Due again). */
    fun endFrom(today: LocalDate): LocalDate = when (this) {
        ThreeDays -> today.plusDays(3)
        OneWeek -> today.plusWeeks(1)
        OneMonth -> today.plusMonths(1)
    }
}
