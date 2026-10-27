package dev.alializadeh.orbit.calendar

import java.time.LocalDate

/**
 * A date that comes round every year in its own [calendar]: (Calendar, month, day, optional year),
 * the shape an Important date is stored in (ADR 0003). 14 Farvardin is always 14 Farvardin, even
 * though its Gregorian day shifts from year to year.
 *
 * - The day may be one the month only has in some years (30 Esfand, 29 Feb) when there's no
 *   [year], or when [year] is a leap year.
 * - In a year the month is shorter, the date falls on the month's last day (30 Esfand → 29 Esfand,
 *   29 Feb → 28 Feb).
 */
data class RecurringDate(
    val calendar: CalendarSystem,
    val month: Int,
    val day: Int,
    val year: Int? = null,
) {
    init {
        require(month in 1..CalendarSystem.MONTHS_PER_YEAR) { "No month $month" }
        require(day in 1..calendar.maxMonthLength(month)) { "No day $day in month $month" }
        if (year != null) {
            require(day <= calendar.monthLength(year, month)) { "No day $day in $month/$year $calendar" }
        }
    }

    /** The day this date falls on in [calendarYear] (a year of its own [calendar]). */
    fun occurrenceIn(calendarYear: Int): LocalDate {
        val dayThatYear = minOf(day, calendar.monthLength(calendarYear, month))
        return CalendarDate(calendar, calendarYear, month, dayThatYear).toLocalDate()
    }

    /** The first time this date falls on or after [date]. */
    fun nextOccurrence(date: LocalDate): Occurrence {
        val thisYear = calendar.yearOf(date)
        val calendarYear = if (occurrenceIn(thisYear) >= date) thisYear else thisYear + 1
        return Occurrence(
            date = occurrenceIn(calendarYear),
            calendarYear = calendarYear,
            yearsCount = year?.let { calendarYear - it },
        )
    }

    /**
     * This date in [target]. With a [year] it's exact. Without one it goes through this year's
     * occurrence (this year of the date's own Calendar, as of [today]), and [Conversion.viaYear]
     * says which year was used ("Converted using 1405").
     */
    fun convertedTo(target: CalendarSystem, today: LocalDate): Conversion {
        if (target == calendar) return Conversion(this, viaYear = null)
        return if (year != null) {
            val converted = CalendarDate(calendar, year, month, day).toLocalDate().inCalendar(target)
            Conversion(RecurringDate(target, converted.month, converted.day, converted.year), viaYear = null)
        } else {
            val viaYear = calendar.yearOf(today)
            val converted = occurrenceIn(viaYear).inCalendar(target)
            Conversion(RecurringDate(target, converted.month, converted.day, year = null), viaYear)
        }
    }

    /** One occurrence: the day it falls on, which year of the date's own Calendar that is, and the years count. */
    data class Occurrence(
        val date: LocalDate,
        val calendarYear: Int,
        /** Years since [year], measured in the date's own Calendar; null when the year is unknown. */
        val yearsCount: Int?,
    )

    data class Conversion(
        val date: RecurringDate,
        /** The source Calendar's year a year-less date was converted through, else null. */
        val viaYear: Int?,
    )
}
