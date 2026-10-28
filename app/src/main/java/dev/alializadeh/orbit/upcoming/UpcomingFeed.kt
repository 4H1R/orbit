package dev.alializadeh.orbit.upcoming

import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.due.DueState
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.occasion.OccasionWithAudience
import dev.alializadeh.orbit.occasion.audience
import dev.alializadeh.orbit.person.PersonNames
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One Person as Upcoming sees them. Archived People are passed in too; the feed leaves them out. */
data class UpcomingPerson(
    val id: Long,
    val names: PersonNames,
    val photoFileName: String?,
    val isArchived: Boolean,
    val primaryContactMethod: ContactMethod?,
    val importantDates: List<ImportantDate>,
    /** The texts of their open Gift ideas, oldest first. */
    val openGiftIdeas: List<String>,
    /** What Due is worked out from. */
    val keepInTouch: KeepInTouch,
    val ringName: String? = null,
    /** Their Ring's place, innermost 0: the Due section's first sort key. */
    val ringPosition: Int = 0,
    /** Their Ring and Tags: who an Occasion reaches. */
    val ringId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
)

/** A Person who is Due today, with where they stand. */
data class DuePerson(val person: UpcomingPerson, val state: DueState)

/** Something on a day ahead: an Important date or an Occasion. */
sealed interface UpcomingEntry {
    val occurrence: RecurringDate.Occurrence

    /** Days from today (0 is today). */
    val daysAway: Int

    val date: LocalDate get() = occurrence.date
}

/** One occurrence of an Important date, [daysAway] days from today (0 is today). */
data class UpcomingDate(
    val person: UpcomingPerson,
    val importantDate: ImportantDate,
    override val occurrence: RecurringDate.Occurrence,
    override val daysAway: Int,
) : UpcomingEntry

/** One occurrence of an Occasion, with the [people] it reaches (never none). */
data class UpcomingOccasion(
    val occasion: OccasionWithAudience,
    val people: List<UpcomingPerson>,
    override val occurrence: RecurringDate.Occurrence,
    override val daysAway: Int,
) : UpcomingEntry

/**
 * What Upcoming shows from People's data: Today, Due, Coming up and the empty states. (Warnings
 * and the Backup nudge come from elsewhere in later slices.)
 */
data class UpcomingFeed(
    /** Whether there's anyone who isn't Archived: without, Upcoming shows the no-People state. */
    val hasPeople: Boolean,
    /** Dates falling today, nearest first (the first one is the hero card). */
    val today: List<UpcomingDate>,
    /**
     * Everyone Due today: innermost Ring first, then longest since the last Interaction (or since
     * they were added). Snoozed People aren't Due, so they're not here until the Snooze ends.
     */
    val due: List<DuePerson>,
    /** Dates in the next [UpcomingFeed.COMING_UP_DAYS] days (1 to 30 days away), soonest first. */
    val comingUp: List<UpcomingDate>,
    /** The first date or Occasion beyond the Coming up window, for "Next: …" in the all-caught-up state. */
    val next: UpcomingEntry?,
    /** Occasions today that reach anyone. */
    val todayOccasions: List<UpcomingOccasion> = emptyList(),
    /** Occasions 1 to 30 days away that reach anyone, soonest first. */
    val comingUpOccasions: List<UpcomingOccasion> = emptyList(),
) {
    /** Coming up as shown: dates and Occasions by day, a day's dates before its Occasions. */
    val comingUpEntries: List<UpcomingEntry>
        get() = (comingUp + comingUpOccasions).sortedWith(compareBy<UpcomingEntry> { it.daysAway }.thenBy { it is UpcomingOccasion })

    /** Nothing today, nobody Due, nothing coming up. */
    val isEmpty: Boolean
        get() = today.isEmpty() && todayOccasions.isEmpty() && due.isEmpty() && comingUp.isEmpty() && comingUpOccasions.isEmpty()

    companion object {
        /** Coming up covers the days 1 to 30 from today. */
        const val COMING_UP_DAYS = 30

        val Empty = UpcomingFeed(hasPeople = false, today = emptyList(), due = emptyList(), comingUp = emptyList(), next = null)

        /**
         * The feed for [people] on [today]: each Important date of everyone who isn't Archived,
         * resolved to its next occurrence in its own Calendar (a missing day falls on the month's
         * last day), ordered by day, then Birthday before Anniversary before Custom, then Person and
         * the order the dates were added; and everyone Due on [today].
         */
        fun of(people: List<UpcomingPerson>, today: LocalDate, occasions: List<OccasionWithAudience> = emptyList()): UpcomingFeed {
            val active = people.filterNot(UpcomingPerson::isArchived)
            val dates = active
                .flatMap { person ->
                    person.importantDates.map { date ->
                        val occurrence = date.date.nextOccurrence(today)
                        UpcomingDate(person, date, occurrence, ChronoUnit.DAYS.between(today, occurrence.date).toInt())
                    }
                }
                .sortedWith(
                    compareBy<UpcomingDate> { it.daysAway }
                        .thenBy { it.importantDate.type.ordinal }
                        .thenBy { it.person.id }
                        .thenBy { it.importantDate.position }
                        .thenBy { it.importantDate.id },
                )
            val due = active
                .map { DuePerson(it, it.keepInTouch.on(today)) }
                .filter { it.state.isDue }
                .sortedWith(DueOrder)
            val occasionDays = occasions
                .mapNotNull { occasion ->
                    val reached = occasion.audience(people)
                    if (reached.isEmpty()) return@mapNotNull null
                    val occurrence = occasion.occasion.date.nextOccurrence(today)
                    UpcomingOccasion(occasion, reached, occurrence, ChronoUnit.DAYS.between(today, occurrence.date).toInt())
                }
                .sortedWith(compareBy<UpcomingOccasion> { it.daysAway }.thenBy { it.occasion.id })
            val nextDate = dates.firstOrNull { it.daysAway > COMING_UP_DAYS }
            val nextOccasion = occasionDays.firstOrNull { it.daysAway > COMING_UP_DAYS }
            return UpcomingFeed(
                hasPeople = active.isNotEmpty(),
                today = dates.filter { it.daysAway == 0 },
                due = due,
                comingUp = dates.filter { it.daysAway in 1..COMING_UP_DAYS },
                next = listOfNotNull(nextDate, nextOccasion).minByOrNull(UpcomingEntry::daysAway),
                todayOccasions = occasionDays.filter { it.daysAway == 0 },
                comingUpOccasions = occasionDays.filter { it.daysAway in 1..COMING_UP_DAYS },
            )
        }
    }
}

/** Innermost Ring first, then longest since the last contact, then the Person's id. */
private val DueOrder: Comparator<DuePerson> =
    compareBy<DuePerson> { it.person.ringPosition }
        .thenByDescending { it.state.daysSinceContact }
        .thenBy { it.person.id }
