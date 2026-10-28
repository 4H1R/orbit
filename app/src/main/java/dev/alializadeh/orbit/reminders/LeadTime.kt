package dev.alializadeh.orbit.reminders

import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.inCalendar
import java.time.LocalDate

/**
 * How long before an Important date a Reminder comes. Lead times count back from the day the date
 * actually falls on that year; "1 month" counts back a month of the date's own Calendar (ADR 0003:
 * it recurs in its own Calendar). Stored by name: never rename a constant.
 */
enum class LeadTime(private val daysBefore: Long?) {
    OnTheDay(0),
    OneDay(1),
    ThreeDays(3),
    OneWeek(7),
    TwoWeeks(14),

    /** The same day of the month before in the date's own Calendar, or that month's last day. */
    OneMonth(null),
    ;

    /** The day of this Reminder for an occurrence on [occurrence] of a date in [calendar]. */
    fun reminderDate(occurrence: LocalDate, calendar: CalendarSystem): LocalDate {
        val days = daysBefore ?: return occurrence.inCalendar(calendar).monthBefore().toLocalDate()
        return occurrence.minusDays(days)
    }

    companion object {
        /** The global default: 1 week before and on the day. */
        val DEFAULT: Set<LeadTime> = setOf(OneWeek, OnTheDay)
    }
}
