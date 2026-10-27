package dev.alializadeh.orbit.upcoming

import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.ImportantDate
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
)

/** One occurrence of an Important date, [daysAway] days from today (0 is today). */
data class UpcomingDate(
    val person: UpcomingPerson,
    val importantDate: ImportantDate,
    val occurrence: RecurringDate.Occurrence,
    val daysAway: Int,
) {
    val date: LocalDate get() = occurrence.date
}

/**
 * What Upcoming shows about Important dates. Sections come from slice 4 on (Due) and later
 * (warnings, the Backup nudge); this covers Today, Coming up and the empty states.
 */
data class UpcomingFeed(
    /** Whether there's anyone who isn't Archived: without, Upcoming shows the no-People state. */
    val hasPeople: Boolean,
    /** Dates falling today, nearest first (the first one is the hero card). */
    val today: List<UpcomingDate>,
    /** Dates in the next [UpcomingFeed.COMING_UP_DAYS] days (1 to 30 days away), soonest first. */
    val comingUp: List<UpcomingDate>,
    /** The first date beyond the Coming up window, for "Next: …" in the all-caught-up state. */
    val next: UpcomingDate?,
) {
    companion object {
        /** Coming up covers the days 1 to 30 from today. */
        const val COMING_UP_DAYS = 30

        val Empty = UpcomingFeed(hasPeople = false, today = emptyList(), comingUp = emptyList(), next = null)

        /**
         * The feed for [people] on [today]: each Important date of everyone who isn't Archived,
         * resolved to its next occurrence in its own Calendar (a missing day falls on the month's
         * last day), ordered by day, then Birthday before Anniversary before Custom, then Person and
         * the order the dates were added.
         */
        fun of(people: List<UpcomingPerson>, today: LocalDate): UpcomingFeed {
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
            return UpcomingFeed(
                hasPeople = active.isNotEmpty(),
                today = dates.filter { it.daysAway == 0 },
                comingUp = dates.filter { it.daysAway in 1..COMING_UP_DAYS },
                next = dates.firstOrNull { it.daysAway > COMING_UP_DAYS },
            )
        }
    }
}
