package dev.alializadeh.orbit.calendar

import java.time.LocalDate

/**
 * The calendar a date is expressed in (glossary: Calendar). Each one knows its month lengths and
 * how to convert its dates to and from [LocalDate], the calendar-neutral day Orbit computes with.
 *
 * Every calendar has 12 months; months and days are 1-based. Stored by name: never rename a
 * constant.
 */
enum class CalendarSystem {
    Gregorian,
    SolarHijri,

    /** Important dates and Occasions only, never the Primary calendar (v1.1, ADR 0007). */
    LunarHijri,
    ;

    /** The lowest year date entry offers (a sanity limit, not a technical one): 1900 CE / 1279 SH / 1317 AH. */
    val minYear: Int
        get() = when (this) {
            Gregorian -> 1900
            SolarHijri -> 1279
            LunarHijri -> 1317
        }

    /** Whether this can be the Primary calendar (Lunar Hijri can't). */
    val canBePrimary: Boolean get() = this != LunarHijri

    fun isLeapYear(year: Int): Boolean = when (this) {
        Gregorian -> java.time.Year.isLeap(year.toLong())
        SolarHijri -> SolarHijriRules.isLeapYear(year)
        LunarHijri -> LunarHijriRules.isLeapYear(year)
    }

    /** Days in [month] of [year]. */
    fun monthLength(year: Int, month: Int): Int = when (this) {
        Gregorian -> java.time.YearMonth.of(year, month).lengthOfMonth()
        SolarHijri -> SolarHijriRules.monthLength(year, month)
        LunarHijri -> LunarHijriRules.monthLength(year, month)
    }

    /** The longest [month] gets in any year: 29 for February, 30 for Esfand and every lunar month. */
    fun maxMonthLength(month: Int): Int = when (this) {
        Gregorian -> java.time.Month.of(month).maxLength()
        SolarHijri -> SolarHijriRules.maxMonthLength(month)
        LunarHijri -> LunarHijriRules.MAX_MONTH_LENGTH
    }

    /** The year of [date] in this calendar. */
    fun yearOf(date: LocalDate): Int = CalendarDate.of(date, this).year

    companion object {
        const val MONTHS_PER_YEAR = 12

        /** The Calendars the Primary calendar can be, and the only ones Interactions are picked in. */
        val Primaries: List<CalendarSystem> = entries.filter(CalendarSystem::canBePrimary)
    }
}
