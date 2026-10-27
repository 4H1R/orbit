package dev.alializadeh.orbit.format

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How far ahead a date is, in the words Upcoming uses up to a week ahead. */
sealed interface RelativeAhead {
    data object Today : RelativeAhead
    data object Tomorrow : RelativeAhead

    /** 2 to 7 days ahead. */
    data class InDays(val days: Int) : RelativeAhead
}

/** How long ago a date was ("last talked 34 days ago"). */
sealed interface RelativeAgo {
    data object Today : RelativeAgo
    data object Yesterday : RelativeAgo

    /** 2 to 59 days ago. */
    data class DaysAgo(val days: Int) : RelativeAgo

    /** 60 days to 23 months ago, as whole months (at least 2). */
    data class MonthsAgo(val months: Int) : RelativeAgo

    /** 2 years ago or more. */
    data class YearsAgo(val years: Int) : RelativeAgo
}

/** The copy deck's relative-time boundaries. Rendered by `RelativeTimeText`. */
object RelativeDays {

    /** Upcoming shows relative words up to this many days ahead, and the date itself beyond. */
    const val MAX_DAYS_AHEAD = 7

    private const val MAX_DAYS_AGO = 59
    private const val MAX_MONTHS_AGO = 23

    /** [date] relative to [today], or null when it's in the past or more than a week ahead. */
    fun ahead(date: LocalDate, today: LocalDate): RelativeAhead? =
        when (val days = ChronoUnit.DAYS.between(today, date)) {
            0L -> RelativeAhead.Today
            1L -> RelativeAhead.Tomorrow
            in 2L..MAX_DAYS_AHEAD.toLong() -> RelativeAhead.InDays(days.toInt())
            else -> null
        }

    /** How long before [today] [date] was. A date after [today] counts as today. */
    fun ago(date: LocalDate, today: LocalDate): RelativeAgo {
        val days = ChronoUnit.DAYS.between(date, today)
        if (days <= 0) return RelativeAgo.Today
        if (days == 1L) return RelativeAgo.Yesterday
        if (days <= MAX_DAYS_AGO) return RelativeAgo.DaysAgo(days.toInt())
        val months = maxOf(2, ChronoUnit.MONTHS.between(date, today).toInt())
        if (months <= MAX_MONTHS_AGO) return RelativeAgo.MonthsAgo(months)
        return RelativeAgo.YearsAgo(ChronoUnit.YEARS.between(date, today).toInt())
    }
}
