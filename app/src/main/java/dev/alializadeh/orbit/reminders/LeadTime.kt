package dev.alializadeh.orbit.reminders

import java.time.LocalDate
import java.time.Period

/**
 * How long before an Important date a Reminder comes. Lead times count back from the day the date
 * actually falls on that year. Stored by name: never rename a constant.
 */
enum class LeadTime(private val before: Period) {
    OnTheDay(Period.ZERO),
    OneDay(Period.ofDays(1)),
    ThreeDays(Period.ofDays(3)),
    OneWeek(Period.ofWeeks(1)),
    TwoWeeks(Period.ofWeeks(2)),
    OneMonth(Period.ofMonths(1)),
    ;

    /** The day of this Reminder for an occurrence on [occurrence]. */
    fun reminderDate(occurrence: LocalDate): LocalDate = occurrence.minus(before)

    companion object {
        /** The global default: 1 week before and on the day. */
        val DEFAULT: Set<LeadTime> = setOf(OneWeek, OnTheDay)
    }
}
