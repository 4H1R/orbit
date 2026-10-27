package dev.alializadeh.orbit.format

import dev.alializadeh.orbit.calendar.CalendarDate
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.calendar.inCalendar
import dev.alializadeh.orbit.language.OrbitLanguage
import java.time.LocalDate

/**
 * Orbit's own date patterns, day first in both languages and both Calendars (never
 * `DateTimeFormatter`, ICU skeletons or `FormatStyle`):
 *
 * | | Gregorian | Solar Hijri |
 * |---|---|---|
 * | English | 27 Oct 2026 | 5 Aban 1405 |
 * | Persian | ۲۷ اکتبر ۲۰۲۶ | ۵ آبان ۱۴۰۵ |
 *
 * Absolute dates drop the year when it's the current year of the Calendar shown. Only Upcoming
 * shows weekdays ([formatWithWeekday]). Digits come from [numbers].
 */
class OrbitDateFormatter(private val numbers: OrbitNumberFormatter) {

    private val language: OrbitLanguage get() = numbers.language

    /** [date] in [calendar]: "27 Oct", or "27 Oct 2025" when it isn't [today]'s year. */
    fun format(date: LocalDate, calendar: CalendarSystem, today: LocalDate): String {
        val labelled = date.inCalendar(calendar)
        return format(labelled, includeYear = labelled.year != calendar.yearOf(today))
    }

    /** Upcoming's form: "Tue 27 Oct" / «سه‌شنبه ۵ آبان», with the year when it isn't this year. */
    fun formatWithWeekday(date: LocalDate, calendar: CalendarSystem, today: LocalDate): String =
        CalendarNames.weekday(language, date.dayOfWeek) + " " + format(date, calendar, today)

    /** [date] with its year always shown: "5 Aban 1405". */
    fun format(date: CalendarDate, includeYear: Boolean = true): String =
        dayMonth(date.calendar, date.month, date.day) + if (includeYear) " " + numbers.format(date.year) else ""

    /**
     * A recurring date as it was entered, in its own Calendar: "14 Farvardin 1370", or
     * "14 Farvardin" when the year is unknown.
     */
    fun format(date: RecurringDate): String =
        dayMonth(date.calendar, date.month, date.day) + (date.year?.let { " " + numbers.format(it) } ?: "")

    /**
     * The parts to show for an occurrence of a date kept in [ownCalendar]: the Primary calendar
     * first, then the date's own Calendar when it differs ("15 Mar", "25 Esfand"). Join them with
     * the `separator` string. [withWeekday] is for Upcoming.
     */
    fun occurrenceParts(
        occurrence: LocalDate,
        ownCalendar: CalendarSystem,
        primaryCalendar: CalendarSystem,
        today: LocalDate,
        withWeekday: Boolean = false,
    ): List<String> {
        val primary = if (withWeekday) {
            formatWithWeekday(occurrence, primaryCalendar, today)
        } else {
            format(occurrence, primaryCalendar, today)
        }
        val second = secondCalendar(ownCalendar, primaryCalendar) ?: return listOf(primary)
        return listOf(primary, format(occurrence, second, today))
    }

    /** "Oct", "Aban", «آبان», «اکتبر». [full] spells out English Gregorian months (the picker). */
    fun monthName(calendar: CalendarSystem, month: Int, full: Boolean = false): String =
        CalendarNames.month(language, calendar, month, full)

    private fun dayMonth(calendar: CalendarSystem, month: Int, day: Int): String =
        numbers.format(day) + " " + monthName(calendar, month)

    companion object {
        /**
         * The second-Calendar display rule: only an Important date whose own Calendar differs from
         * the Primary calendar also shows its own Calendar, after the Primary one. Every other date
         * shows the Primary calendar only.
         */
        fun secondCalendar(ownCalendar: CalendarSystem, primaryCalendar: CalendarSystem): CalendarSystem? =
            ownCalendar.takeIf { it != primaryCalendar }
    }
}
