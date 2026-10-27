package dev.alializadeh.orbit.calendar

import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.CalendarSystem.SolarHijri
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurringDateTest {

    private val today = LocalDate.of(2026, 10, 28) // 6 Aban 1405

    // --- Occurrences ---------------------------------------------------------------------------

    @Test
    fun aDateLaterThisYearOccursThisYear() {
        val occurrence = RecurringDate(Gregorian, 12, 5, 1990).nextOccurrence(today)
        assertEquals(LocalDate.of(2026, 12, 5), occurrence.date)
        assertEquals(2026, occurrence.calendarYear)
        assertEquals(36, occurrence.yearsCount)
    }

    @Test
    fun aDateEarlierThisYearOccursNextYear() {
        val occurrence = RecurringDate(Gregorian, 3, 1, 1990).nextOccurrence(today)
        assertEquals(LocalDate.of(2027, 3, 1), occurrence.date)
        assertEquals(37, occurrence.yearsCount)
    }

    @Test
    fun todayIsItsOwnNextOccurrence() {
        assertEquals(today, RecurringDate(Gregorian, 10, 28).nextOccurrence(today).date)
        assertEquals(today, RecurringDate(SolarHijri, 8, 6).nextOccurrence(today).date)
    }

    @Test
    fun aSolarHijriDateRecursInItsOwnCalendar() {
        // 14 Farvardin 1406 is 3 April 2027; the one after is 14 Farvardin 1407.
        val date = RecurringDate(SolarHijri, 1, 14, 1370)
        val next = date.nextOccurrence(today)
        assertEquals(LocalDate.of(2027, 4, 3), next.date)
        assertEquals(1406, next.calendarYear)
        assertEquals(36, next.yearsCount)
        assertEquals(shamsi(1407, 1, 14), date.nextOccurrence(next.date.plusDays(1)).date)
    }

    @Test
    fun theYearsCountIsMeasuredInTheDatesOwnCalendar() {
        // 1 Farvardin 1370 (21 Mar 1991): its 1405 occurrence (21 Mar 2026) is the 35th.
        val date = RecurringDate(SolarHijri, 1, 1, 1370)
        val occurrence = date.nextOccurrence(LocalDate.of(2026, 3, 1))
        assertEquals(LocalDate.of(2026, 3, 21), occurrence.date)
        assertEquals(35, occurrence.yearsCount)
        // Year-less dates have no count.
        assertNull(RecurringDate(SolarHijri, 1, 1).nextOccurrence(today).yearsCount)
    }

    // --- Missing days fall on the month's last day ---------------------------------------------

    @Test
    fun thirtyEsfandFallsOn29EsfandInACommonYear() {
        val date = RecurringDate(SolarHijri, 12, 30)
        assertEquals(shamsi(1405, 12, 29), date.occurrenceIn(1405))
        assertEquals(shamsi(1408, 12, 30), date.occurrenceIn(1408))
        assertEquals(LocalDate.of(2027, 3, 20), date.nextOccurrence(today).date)
    }

    @Test
    fun twentyNinthFebruaryFallsOn28thInACommonYear() {
        val date = RecurringDate(Gregorian, 2, 29, 2000)
        val next = date.nextOccurrence(today)
        assertEquals(LocalDate.of(2027, 2, 28), next.date)
        assertEquals(27, next.yearsCount)
        assertEquals(LocalDate.of(2028, 2, 29), date.occurrenceIn(2028))
    }

    @Test
    fun theMissingDayCountsAsTheOccurrenceSoTheNextOneIsAYearLater() {
        // On 28 Feb 2027 a 29 Feb date is today, not next year.
        val date = RecurringDate(Gregorian, 2, 29)
        assertEquals(LocalDate.of(2027, 2, 28), date.nextOccurrence(LocalDate.of(2027, 2, 28)).date)
        assertEquals(LocalDate.of(2028, 2, 29), date.nextOccurrence(LocalDate.of(2027, 3, 1)).date)
    }

    @Test(expected = IllegalArgumentException::class)
    fun thirtyEsfandNeedsALeapYear() {
        RecurringDate(SolarHijri, 12, 30, 1404)
    }

    @Test(expected = IllegalArgumentException::class)
    fun twentyNinthFebruaryNeedsALeapYear() {
        RecurringDate(Gregorian, 2, 29, 2026)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noMonthHas32Days() {
        RecurringDate(Gregorian, 1, 32)
    }

    // --- Conversion between Calendars ---------------------------------------------------------

    @Test
    fun withAYearConversionIsExact() {
        val converted = RecurringDate(SolarHijri, 1, 14, 1370).convertedTo(Gregorian, today)
        assertEquals(RecurringDate(Gregorian, 4, 3, 1991), converted.date)
        assertNull(converted.viaYear)
        assertEquals(RecurringDate(SolarHijri, 1, 14, 1370), converted.date.convertedTo(SolarHijri, today).date)
    }

    @Test
    fun withoutAYearConversionGoesThroughThisYearsOccurrence() {
        val converted = RecurringDate(SolarHijri, 8, 5).convertedTo(Gregorian, today)
        assertEquals(RecurringDate(Gregorian, 10, 27), converted.date)
        assertEquals(1405, converted.viaYear)

        val back = RecurringDate(Gregorian, 3, 20).convertedTo(SolarHijri, today)
        assertEquals(RecurringDate(SolarHijri, 12, 29), back.date) // 20 Mar 2026 = 29 Esfand 1404
        assertEquals(2026, back.viaYear)
    }

    @Test
    fun yearLessThirtyEsfandConvertsThroughThisYearsLastDay() {
        // 1405 is a common year, so 30 Esfand falls on 29 Esfand 1405 = 20 Mar 2027.
        val converted = RecurringDate(SolarHijri, 12, 30).convertedTo(Gregorian, today)
        assertEquals(RecurringDate(Gregorian, 3, 20), converted.date)
    }

    @Test
    fun yearLess29FebruaryConvertsThroughThisYears28th() {
        val converted = RecurringDate(Gregorian, 2, 29).convertedTo(SolarHijri, today)
        assertEquals(RecurringDate(SolarHijri, 12, 9), converted.date) // 28 Feb 2026
    }

    @Test
    fun convertingToTheSameCalendarChangesNothing() {
        val date = RecurringDate(Gregorian, 2, 29)
        assertEquals(RecurringDate.Conversion(date, null), date.convertedTo(Gregorian, today))
    }

    private fun shamsi(year: Int, month: Int, day: Int) = CalendarDate(SolarHijri, year, month, day).toLocalDate()

    // --- Age (completed years) -----------------------------------------------------------------

    @Test
    fun theAgeGoesUpOnTheDayTheDateFallsOn() {
        val birthday = RecurringDate(Gregorian, 10, 28, 1991)
        assertEquals(34, birthday.completedYears(LocalDate.of(2026, 10, 27)))
        assertEquals(35, birthday.completedYears(today))
        assertEquals(35, birthday.completedYears(LocalDate.of(2027, 10, 27)))
    }

    @Test
    fun theAgeIsCountedInTheDatesOwnCalendar() {
        // 7 Aban 1370: on 6 Aban 1405 (28 Oct 2026) the 35th Solar Hijri birthday is a day away.
        val birthday = RecurringDate(SolarHijri, 8, 7, 1370)
        assertEquals(34, birthday.completedYears(today))
        assertEquals(35, birthday.completedYears(today.plusDays(1)))
        assertEquals(35, birthday.nextOccurrence(today).yearsCount)
    }

    @Test
    fun aLeapDayBirthdayAgesOnTheLastDayOfFebruaryInOtherYears() {
        val birthday = RecurringDate(Gregorian, 2, 29, 2000)
        assertEquals(25, birthday.completedYears(LocalDate.of(2026, 2, 27)))
        assertEquals(26, birthday.completedYears(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun noYearNoAge() {
        assertNull(RecurringDate(Gregorian, 3, 1).completedYears(today))
    }
}
