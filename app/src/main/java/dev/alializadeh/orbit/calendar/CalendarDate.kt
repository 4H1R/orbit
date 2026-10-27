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
