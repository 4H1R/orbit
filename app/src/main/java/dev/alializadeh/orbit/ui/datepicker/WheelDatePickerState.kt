package dev.alializadeh.orbit.ui.datepicker

import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.calendar.inCalendar
import java.time.LocalDate

/**
 * The wheel picker's selection and rules, free of Compose so they're JVM-tested. Immutable: every
 * change returns a new state.
 *
 * - Day · month · year, where the year can be "No year" ([year] null) when [allowNoYear].
 * - Month lengths come from the converter. 30 Esfand / 29 Feb can be picked with "No year" or a
 *   leap year only; picking a year that lacks the day moves it to the month's last day.
 * - Years run from the Calendar's [CalendarSystem.minYear] up to the current year plus
 *   [futureYears] (none unless asked for); later years are rejected. A year outside that range that
 *   a Calendar flip produced stays selectable.
 * - Flipping the Calendar converts the selection: exactly with a year, otherwise through this
 *   year's occurrence, recorded in [convertedViaYear] for the "Converted using 1405" hint.
 */
data class WheelDatePickerState(
    val calendar: CalendarSystem,
    val month: Int,
    val day: Int,
    val year: Int?,
    val today: LocalDate,
    val allowNoYear: Boolean,
    /** The source Calendar's year the last flip converted a year-less date through. */
    val convertedViaYear: Int? = null,
    /** How many years after the current one the year wheel offers. */
    val futureYears: Int = 0,
) {
    init {
        require(year != null || allowNoYear) { "This picker needs a year" }
        require(day in 1..dayCount) { "No day $day in month $month" }
    }

    /** The selection as a date in [calendar]. */
    val date: RecurringDate get() = RecurringDate(calendar, month, day, year)

    /** Days the day wheel offers: the month's length that year, or its longest without a year. */
    val dayCount: Int
        get() = if (year == null) calendar.maxMonthLength(month) else calendar.monthLength(year, month)

    val currentYear: Int get() = calendar.yearOf(today)

    private val lastYear: Int get() = currentYear + futureYears

    /** The year wheel, top to bottom: "No year" (null) if allowed, then the newest year first. */
    val yearOptions: List<Int?>
        get() {
            val years = buildList {
                if (year != null && year > lastYear) add(year)
                addAll(lastYear downTo calendar.minYear)
                if (year != null && year < calendar.minYear) add(year)
            }
            return if (allowNoYear) listOf(null) + years else years
        }

    fun withDay(day: Int): WheelDatePickerState = copy(day = day.coerceIn(1, dayCount), convertedViaYear = null)

    fun withMonth(month: Int): WheelDatePickerState = adjusted(month = month, year = year)

    fun withYear(year: Int?): WheelDatePickerState {
        require(year != null || allowNoYear) { "This picker needs a year" }
        require(year == null || year <= lastYear || year == this.year) { "$year is in the future" }
        return adjusted(month = month, year = year)
    }

    /** The same day in [target]; see the class rules for year-less dates. */
    fun withCalendar(target: CalendarSystem): WheelDatePickerState {
        if (target == calendar) return this
        val conversion = date.convertedTo(target, today)
        return copy(
            calendar = target,
            month = conversion.date.month,
            day = conversion.date.day,
            year = conversion.date.year,
            convertedViaYear = conversion.viaYear,
        )
    }

    private fun adjusted(month: Int, year: Int?): WheelDatePickerState {
        val length = if (year == null) calendar.maxMonthLength(month) else calendar.monthLength(year, month)
        return copy(month = month, year = year, day = minOf(day, length), convertedViaYear = null)
    }

    companion object {
        /**
         * A picker for a new date, on [calendar] (the Primary calendar), at today's day and month,
         * with "No year" if allowed, else this year.
         */
        fun forNewDate(calendar: CalendarSystem, today: LocalDate, allowNoYear: Boolean): WheelDatePickerState {
            val now = today.inCalendar(calendar)
            return WheelDatePickerState(
                calendar = calendar,
                month = now.month,
                day = now.day,
                year = if (allowNoYear) null else now.year,
                today = today,
                allowNoYear = allowNoYear,
            )
        }

        /** A picker editing [date], starting on the date's own Calendar. */
        fun forDate(date: RecurringDate, today: LocalDate, allowNoYear: Boolean): WheelDatePickerState =
            WheelDatePickerState(
                calendar = date.calendar,
                month = date.month,
                day = date.day,
                year = date.year,
                today = today,
                allowNoYear = allowNoYear,
            )
    }
}
