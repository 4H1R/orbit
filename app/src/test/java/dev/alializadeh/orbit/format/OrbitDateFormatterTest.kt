package dev.alializadeh.orbit.format

import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.CalendarSystem.SolarHijri
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.calendar.inCalendar
import dev.alializadeh.orbit.language.OrbitLanguage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrbitDateFormatterTest {

    private val english = OrbitDateFormatter(OrbitNumberFormatter(OrbitLanguage.English, useLatinDigits = false))
    private val persian = OrbitDateFormatter(OrbitNumberFormatter(OrbitLanguage.Persian, useLatinDigits = false))
    private val persianLatin = OrbitDateFormatter(OrbitNumberFormatter(OrbitLanguage.Persian, useLatinDigits = true))

    private val date = LocalDate.of(2026, 10, 27) // 5 Aban 1405, a Tuesday
    private val nextYear = LocalDate.of(2027, 1, 1)

    @Test
    fun allFourLanguageAndCalendarCombinationsWithTheYear() {
        assertEquals("27 Oct 2026", english.format(date, Gregorian, today = nextYear))
        assertEquals("5 Aban 1405", english.format(date, SolarHijri, today = LocalDate.of(2027, 6, 1)))
        assertEquals("۵ آبان ۱۴۰۵", persian.format(date, SolarHijri, today = LocalDate.of(2027, 6, 1)))
        assertEquals("۲۷ اکتبر ۲۰۲۶", persian.format(date, Gregorian, today = nextYear))
        assertEquals("5 آبان 1405", persianLatin.format(date, SolarHijri, today = LocalDate.of(2027, 6, 1)))
    }

    @Test
    fun theYearIsDroppedInTheCurrentYearOfTheCalendarShown() {
        val today = LocalDate.of(2026, 10, 28)
        assertEquals("27 Oct", english.format(date, Gregorian, today))
        assertEquals("5 Aban", english.format(date, SolarHijri, today))
        assertEquals("۵ آبان", persian.format(date, SolarHijri, today))
        assertEquals("۲۷ اکتبر", persian.format(date, Gregorian, today))
        // 1 Jan 2027 is still 1405 in Solar Hijri, but no longer 2026 in Gregorian.
        assertEquals("11 Dey", english.format(nextYear, SolarHijri, today))
        assertEquals("1 Jan 2027", english.format(nextYear, Gregorian, today))
        // 21 Mar 2027 is 1 Farvardin 1406: a new Solar Hijri year.
        assertEquals("1 Farvardin 1406", english.format(LocalDate.of(2027, 3, 21), SolarHijri, today))
    }

    @Test
    fun upcomingShowsWeekdays() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals("Tue 27 Oct", english.formatWithWeekday(date, Gregorian, today))
        assertEquals("Tue 5 Aban", english.formatWithWeekday(date, SolarHijri, today))
        assertEquals("سه‌شنبه ۵ آبان", persian.formatWithWeekday(date, SolarHijri, today))
        assertEquals("سه‌شنبه ۲۷ اکتبر", persian.formatWithWeekday(date, Gregorian, today))
    }

    @Test
    fun recurringDatesShowTheirOwnCalendarAndYearIfKnown() {
        assertEquals("14 Farvardin 1370", english.format(RecurringDate(SolarHijri, 1, 14, 1370)))
        assertEquals("14 Farvardin", english.format(RecurringDate(SolarHijri, 1, 14)))
        assertEquals("۲۹ فوریه", persian.format(RecurringDate(Gregorian, 2, 29)))
        assertEquals("۳۰ اسفند ۱۴۰۳", persian.format(RecurringDate(SolarHijri, 12, 30, 1403)))
    }

    @Test
    fun aCalendarDateAlwaysShowsItsYearUnlessAskedNotTo() {
        assertEquals("6 Aban 1405", english.format(LocalDate.of(2026, 10, 28).inCalendar(SolarHijri)))
        assertEquals("6 Aban", english.format(LocalDate.of(2026, 10, 28).inCalendar(SolarHijri), includeYear = false))
    }

    @Test
    fun theSecondCalendarShowsOnlyWhenTheDatesCalendarDiffers() {
        val today = LocalDate.of(2026, 3, 1) // 10 Esfand 1404
        val occurrence = LocalDate.of(2026, 3, 16) // 25 Esfand 1404
        assertEquals(
            listOf("16 Mar", "25 Esfand"),
            english.occurrenceParts(occurrence, ownCalendar = SolarHijri, primaryCalendar = Gregorian, today = today),
        )
        assertEquals(
            listOf("16 Mar"),
            english.occurrenceParts(occurrence, ownCalendar = Gregorian, primaryCalendar = Gregorian, today = today),
        )
        // Upcoming, Persian, Primary calendar Solar Hijri, a Gregorian date: Sunday 8 March 2026.
        assertEquals(
            listOf("یک‌شنبه ۱۷ اسفند", "۸ مارس"),
            persian.occurrenceParts(
                LocalDate.of(2026, 3, 8),
                ownCalendar = Gregorian,
                primaryCalendar = SolarHijri,
                today = today,
                withWeekday = true,
            ),
        )
        assertEquals(SolarHijri, OrbitDateFormatter.secondCalendar(SolarHijri, Gregorian))
        assertNull(OrbitDateFormatter.secondCalendar(Gregorian, Gregorian))
    }

    @Test
    fun monthNames() {
        assertEquals(
            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
            (1..12).map { english.monthName(Gregorian, it) },
        )
        assertEquals("October", english.monthName(Gregorian, 10, full = true))
        assertEquals(
            listOf(
                "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
                "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand",
            ),
            (1..12).map { english.monthName(SolarHijri, it) },
        )
        assertEquals("ژانویه", persian.monthName(Gregorian, 1))
        assertEquals("دسامبر", persian.monthName(Gregorian, 12))
        assertEquals("فروردین", persian.monthName(SolarHijri, 1))
        assertEquals("اسفند", persian.monthName(SolarHijri, 12, full = true))
    }

    @Test
    fun personDetailShowsADateWithAYearAsEntered() {
        val today = LocalDate.of(2026, 10, 28)
        val farvardin = RecurringDate(SolarHijri, 1, 14, 1370)
        assertEquals(listOf("3 Apr 1991", "14 Farvardin 1370"), english.importantDateParts(farvardin, Gregorian, today))
        assertEquals(listOf("۱۴ فروردین ۱۳۷۰"), persian.importantDateParts(farvardin, SolarHijri, today))
        assertEquals(
            listOf("۱۴ فروردین ۱۳۷۰", "۳ آوریل ۱۹۹۱"),
            persian.importantDateParts(RecurringDate(Gregorian, 4, 3, 1991), SolarHijri, today),
        )
    }

    @Test
    fun personDetailShowsAYearlessDateThroughItsNextOccurrence() {
        val today = LocalDate.of(2026, 10, 28)
        // 25 Esfand 1405 is 16 Mar 2027.
        assertEquals(
            listOf("16 Mar", "25 Esfand"),
            english.importantDateParts(RecurringDate(SolarHijri, 12, 25), Gregorian, today),
        )
        // In its own Calendar it reads as entered, even a leap day in a year without one.
        assertEquals(listOf("29 Feb"), english.importantDateParts(RecurringDate(Gregorian, 2, 29), Gregorian, today))
    }
}
