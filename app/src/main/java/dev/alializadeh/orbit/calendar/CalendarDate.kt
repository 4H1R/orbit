package dev.alializadeh.orbit.calendar

import java.time.LocalDate

/** One day, labelled in [calendar]. Convert with [toLocalDate] and [of]. */
data class CalendarDate(
    val calendar: CalendarSystem,
    val year: Int,
    val month: Int,
    val day: Int,
) {
    init {
        require(month in 1..CalendarSystem.MONTHS_PER_YEAR) { "No month $month" }
        require(day in 1..calendar.monthLength(year, month)) { "No day $day in $month/$year $calendar" }
    }

    fun toLocalDate(): LocalDate = when (calendar) {
        CalendarSystem.Gregorian -> LocalDate.of(year, month, day)
        CalendarSystem.SolarHijri -> SolarHijriRules.toLocalDate(year, month, day)
    }

    /**
     * The same day of the month before, in this date's own [calendar]: 1 Farvardin → 1 Esfand of
     * the year before. A day that month doesn't have falls on its last day (31 Farvardin → 29 or 30
     * Esfand, 31 March → 28 or 29 February).
     */
    fun monthBefore(): CalendarDate {
        val (previousYear, previousMonth) = if (month == 1) year - 1 to CalendarSystem.MONTHS_PER_YEAR else year to month - 1
        return CalendarDate(calendar, previousYear, previousMonth, minOf(day, calendar.monthLength(previousYear, previousMonth)))
    }

    companion object {
        /** [date] labelled in [calendar]. */
        fun of(date: LocalDate, calendar: CalendarSystem): CalendarDate = when (calendar) {
            CalendarSystem.Gregorian ->
                CalendarDate(CalendarSystem.Gregorian, date.year, date.monthValue, date.dayOfMonth)
            CalendarSystem.SolarHijri -> SolarHijriRules.fromLocalDate(date)
        }
    }
}

/** [this] date labelled in [calendar]. */
fun LocalDate.inCalendar(calendar: CalendarSystem): CalendarDate = CalendarDate.of(this, calendar)
