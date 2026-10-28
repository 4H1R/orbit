package dev.alializadeh.orbit.calendar

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lunar Hijri (قمری) as Umm al-Qura, through `java.time`'s `HijrahChronology`, whose table is the
 * same on Android and the JVM (1300–1600 AH), moved by the user's [LunarHijriAdjustment] (ADR 0007).
 *
 * The adjustment moves every Lunar Hijri date by whole days, so month lengths don't change: +1
 * means each Lunar Hijri date falls one day later than Umm al-Qura says (the month started a day
 * later, as Iran's official calendar often does).
 */
object LunarHijriRules {

    /** No month is longer than this in any year. */
    const val MAX_MONTH_LENGTH = 30

    fun isLeapYear(year: Int): Boolean = HijrahChronology.INSTANCE.isLeapYear(year.toLong())

    /** 29 or 30, from the Umm al-Qura table. */
    fun monthLength(year: Int, month: Int): Int = HijrahDate.of(year, month, 1).lengthOfMonth()

    fun toLocalDate(year: Int, month: Int, day: Int, adjustment: Int = LunarHijriAdjustment.current): LocalDate =
        LocalDate.from(HijrahDate.of(year, month, day)).plusDays(adjustment.toLong())

    fun fromLocalDate(date: LocalDate, adjustment: Int = LunarHijriAdjustment.current): CalendarDate {
        val hijrah = HijrahDate.from(date.minusDays(adjustment.toLong()))
        return CalendarDate(
            CalendarSystem.LunarHijri,
            hijrah.get(ChronoField.YEAR),
            hijrah.get(ChronoField.MONTH_OF_YEAR),
            hijrah.get(ChronoField.DAY_OF_MONTH),
        )
    }
}

/**
 * The Lunar date adjustment, −2…+2 days (a backed-up preference): process-wide, so every
 * conversion (screens, the reminder planner, the widget) agrees. Set from the preferences by the
 * activity's `MainViewModel` and by `reconcile()`; anything that caches occurrences recomputes on
 * [days] (`todayFlow` re-emits, `OrbitFormats` carries it for composition). Tests that set it put
 * it back to 0.
 */
object LunarHijriAdjustment {
    const val MAX_DAYS = 2

    private val state = MutableStateFlow(0)

    val days: StateFlow<Int> = state.asStateFlow()

    val current: Int get() = state.value

    fun set(days: Int) {
        state.value = days.coerceIn(-MAX_DAYS, MAX_DAYS)
    }
}
