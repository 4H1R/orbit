package dev.alializadeh.orbit.calendar

import java.time.LocalDate

/**
 * The Solar Hijri (شمسی) calendar by the 33-year arithmetic rule: a year is leap when
 * `(25·y + 11) mod 33 < 8`. That matches the Iranian calendar authority's official leap table for
 * every year from 1206 to 1497 SH (checked in `SolarHijriRulesTest` against the vendored table), and per
 * ICU it holds until 1501 SH. It's the rule Android's own ICU uses in that range.
 *
 * Months 1–6 have 31 days, 7–11 have 30, and Esfand (12) has 29, or 30 in a leap year.
 *
 * Conversion goes through the epoch day: 1 Farvardin of year `y` is
 * `365·(y − 1) + ⌊(8·y + 21) / 33⌋` days after the calendar's epoch.
 */
object SolarHijriRules {

    fun isLeapYear(year: Int): Boolean = Math.floorMod(25L * year + 11, 33L) < 8

    fun monthLength(year: Int, month: Int): Int {
        requireMonth(month)
        return when {
            month <= 6 -> 31
            month <= 11 -> 30
            isLeapYear(year) -> 30
            else -> 29
        }
    }

    fun maxMonthLength(month: Int): Int {
        requireMonth(month)
        return if (month <= 6) 31 else 30
    }

    /** The [LocalDate] of [day] [month] [year] SH. The day must exist in that month and year. */
    fun toLocalDate(year: Int, month: Int, day: Int): LocalDate {
        require(day in 1..monthLength(year, month)) { "No day $day in month $month of $year SH" }
        val dayOfYear = daysBeforeMonth(month) + day - 1
        return LocalDate.ofEpochDay(epochDayOfNowruz(year) + dayOfYear)
    }

    /** [date] as a Solar Hijri date. */
    fun fromLocalDate(date: LocalDate): CalendarDate {
        val epochDay = date.toEpochDay()
        // Estimate the year, then correct it: the estimate is off by at most one.
        var year = (Math.floorDiv((epochDay - PERSIAN_EPOCH_DAY) * 33 + 3, 12_053L) + 1).toInt()
        while (epochDayOfNowruz(year) > epochDay) year--
        while (epochDayOfNowruz(year + 1) <= epochDay) year++
        val dayOfYear = (epochDay - epochDayOfNowruz(year)).toInt()
        val month = if (dayOfYear < 186) dayOfYear / 31 + 1 else (dayOfYear - 6) / 30 + 1
        val day = dayOfYear - daysBeforeMonth(month) + 1
        return CalendarDate(CalendarSystem.SolarHijri, year, month, day)
    }

    /** The epoch day of 1 Farvardin of [year]. */
    fun epochDayOfNowruz(year: Int): Long =
        PERSIAN_EPOCH_DAY + 365L * (year - 1) + Math.floorDiv(8L * year + 21, 33L)

    private fun daysBeforeMonth(month: Int): Int =
        if (month <= 7) (month - 1) * 31 else 6 * 31 + (month - 7) * 30

    private fun requireMonth(month: Int) =
        require(month in 1..CalendarSystem.MONTHS_PER_YEAR) { "No month $month" }

    /** Julian day 1948320 (the day 1 Farvardin 1 SH counts from), as an epoch day. */
    private const val PERSIAN_EPOCH_DAY = 1_948_320L - 2_440_588L
}
