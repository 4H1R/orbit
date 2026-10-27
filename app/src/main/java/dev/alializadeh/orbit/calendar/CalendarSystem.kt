package dev.alializadeh.orbit.calendar

import java.time.LocalDate

/**
 * The calendar a date is expressed in (glossary: Calendar). Each one knows its month lengths and
 * how to convert its dates to and from [LocalDate], the calendar-neutral day Orbit computes with.
 *
 * Both calendars have 12 months; months and days are 1-based.
 */
enum class CalendarSystem {
    Gregorian,
    SolarHijri,
    ;

    /** The lowest year date entry offers (a sanity limit, not a technical one): 1900 CE / 1279 SH. */
    val minYear: Int
        get() = when (this) {
            Gregorian -> 1900
            SolarHijri -> 1279
        }

    fun isLeapYear(year: Int): Boolean = when (this) {
        Gregorian -> java.time.Year.isLeap(year.toLong())
        SolarHijri -> SolarHijriRules.isLeapYear(year)
    }

    /** Days in [month] of [year]. */
    fun monthLength(year: Int, month: Int): Int = when (this) {
        Gregorian -> java.time.YearMonth.of(year, month).lengthOfMonth()
        SolarHijri -> SolarHijriRules.monthLength(year, month)
    }

    /** The longest [month] gets in any year: 29 for February, 30 for Esfand. */
    fun maxMonthLength(month: Int): Int = when (this) {
        Gregorian -> java.time.Month.of(month).maxLength()
        SolarHijri -> SolarHijriRules.maxMonthLength(month)
    }

    /** The year of [date] in this calendar. */
    fun yearOf(date: LocalDate): Int = CalendarDate.of(date, this).year

    companion object {
        const val MONTHS_PER_YEAR = 12
    }
}
