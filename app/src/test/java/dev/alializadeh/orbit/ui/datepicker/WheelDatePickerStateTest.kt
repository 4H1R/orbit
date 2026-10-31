package dev.alializadeh.orbit.ui.datepicker

import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.CalendarSystem.LunarHijri
import dev.alializadeh.orbit.calendar.CalendarSystem.SolarHijri
import dev.alializadeh.orbit.calendar.RecurringDate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelDatePickerStateTest {

    private val today = LocalDate.of(2026, 10, 28) // 6 Aban 1405

    @Test
    fun aNewDateStartsTodayOnTheGivenCalendarWithNoYear() {
        val state = WheelDatePickerState.forNewDate(SolarHijri, today, allowNoYear = true)
        assertEquals(RecurringDate(SolarHijri, 8, 6), state.date)

        val interaction = WheelDatePickerState.forNewDate(Gregorian, today, allowNoYear = false)
        assertEquals(RecurringDate(Gregorian, 10, 28, 2026), interaction.date)
    }

    @Test
    fun editingStartsOnTheDatesOwnCalendar() {
        val state = WheelDatePickerState.forDate(RecurringDate(SolarHijri, 1, 14, 1370), today, allowNoYear = true)
        assertEquals(SolarHijri, state.calendar)
        assertEquals(1370, state.year)
    }

    @Test
    fun yearsRunFromNoYearThroughThisYearDownToTheMinimum() {
        val gregorian = WheelDatePickerState.forNewDate(Gregorian, today, allowNoYear = true)
        assertEquals(listOf(null, 2026, 2025), gregorian.yearOptions.take(3))
        assertEquals(1900, gregorian.yearOptions.last())
        assertEquals(1 + (2026 - 1900 + 1), gregorian.yearOptions.size)

        val shamsi = WheelDatePickerState.forNewDate(SolarHijri, today, allowNoYear = false)
        assertEquals(1405, shamsi.yearOptions.first())
        assertEquals(1279, shamsi.yearOptions.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureYearsAreRejected() {
        WheelDatePickerState.forNewDate(Gregorian, today, allowNoYear = true).withYear(2027)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noYearIsRejectedWhenNotAllowed() {
        WheelDatePickerState.forNewDate(Gregorian, today, allowNoYear = false).withYear(null)
    }

    @Test
    fun monthLengthsComeFromTheConverter() {
        val state = WheelDatePickerState.forNewDate(SolarHijri, today, allowNoYear = true)
        assertEquals(31, state.withMonth(1).dayCount)
        assertEquals(30, state.withMonth(7).dayCount)
        assertEquals(30, state.withMonth(12).dayCount) // no year: Esfand can have 30
        assertEquals(29, state.withMonth(12).withYear(1404).dayCount)
        assertEquals(30, state.withMonth(12).withYear(1403).dayCount)
    }

    @Test
    fun thirtyEsfandIsPickableWithNoYearOrALeapYearOnly() {
        val thirtyEsfand = WheelDatePickerState.forNewDate(SolarHijri, today, allowNoYear = true)
            .withMonth(12)
            .withDay(30)
        assertEquals(RecurringDate(SolarHijri, 12, 30), thirtyEsfand.date)
        assertEquals(RecurringDate(SolarHijri, 12, 30, 1403), thirtyEsfand.withYear(1403).date)
        // A common year moves it to the month's last day.
        assertEquals(RecurringDate(SolarHijri, 12, 29, 1404), thirtyEsfand.withYear(1404).date)
    }

    @Test
    fun twentyNinthFebruaryIsPickableWithNoYearOrALeapYearOnly() {
        val leapDay = WheelDatePickerState.forNewDate(Gregorian, today, allowNoYear = true).withMonth(2).withDay(29)
        assertEquals(RecurringDate(Gregorian, 2, 29), leapDay.date)
        assertEquals(RecurringDate(Gregorian, 2, 29, 2024), leapDay.withYear(2024).date)
        assertEquals(RecurringDate(Gregorian, 2, 28, 2025), leapDay.withYear(2025).date)
    }

    @Test
    fun aShorterMonthMovesTheDayToItsLastDay() {
        val state = WheelDatePickerState.forNewDate(Gregorian, today, allowNoYear = false).withDay(31).withMonth(4)
        assertEquals(RecurringDate(Gregorian, 4, 30, 2026), state.date)
    }

    @Test
    fun aDayBeyondTheMonthIsClamped() {
        val state = WheelDatePickerState.forNewDate(Gregorian, today, allowNoYear = false).withMonth(2)
        assertEquals(28, state.withDay(31).day)
    }

    @Test
    fun flippingWithAYearConvertsExactly() {
        val state = WheelDatePickerState.forDate(RecurringDate(SolarHijri, 1, 14, 1370), today, allowNoYear = true)
        val flipped = state.withCalendar(Gregorian)
        assertEquals(RecurringDate(Gregorian, 4, 3, 1991), flipped.date)
        assertNull(flipped.convertedViaYear)
        assertEquals(state.date, flipped.withCalendar(SolarHijri).date)
    }

    @Test
    fun flippingWithoutAYearGoesThroughThisYearAndSaysSo() {
        val state = WheelDatePickerState.forDate(RecurringDate(SolarHijri, 8, 5), today, allowNoYear = true)
        val flipped = state.withCalendar(Gregorian)
        assertEquals(RecurringDate(Gregorian, 10, 27), flipped.date)
        assertEquals(1405, flipped.convertedViaYear)

        // Any edit clears the hint.
        assertNull(flipped.withDay(1).convertedViaYear)
        assertNull(flipped.withMonth(1).convertedViaYear)
        assertNull(flipped.withYear(2000).convertedViaYear)
        // Flipping back reports the year it went through this time.
        assertEquals(2026, flipped.withCalendar(SolarHijri).convertedViaYear)
    }

    @Test
    fun flippingYearLessThirtyEsfandUsesThisYearsLastDay() {
        val state = WheelDatePickerState.forDate(RecurringDate(SolarHijri, 12, 30), today, allowNoYear = true)
        assertEquals(RecurringDate(Gregorian, 3, 20), state.withCalendar(Gregorian).date)
    }

    @Test
    fun flippingToTheSameCalendarChangesNothing() {
        val state = WheelDatePickerState.forNewDate(Gregorian, today, allowNoYear = true)
        assertEquals(state, state.withCalendar(Gregorian))
    }

    /** Follow-ups and Loans look ahead; birthdays never do. */
    @Test
    fun futureYearsAreOfferedOnlyWhenAskedFor() {
        val plain = WheelDatePickerState.forNewDate(Gregorian, today, allowNoYear = false)
        assertEquals(2026, plain.yearOptions.first())

        val ahead = plain.copy(futureYears = 2)
        assertEquals(listOf<Int?>(2028, 2027, 2026), ahead.yearOptions.take(3))
        assertEquals(2028, ahead.withYear(2028).year)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { ahead.withYear(2029) }
        assertEquals(2, ahead.withYear(2027).withCalendar(SolarHijri).futureYears)
    }

    @Test
    fun aConvertedYearOutsideTheRangeStaysSelectable() {
        // 10 Esfand 1405 is 1 March 2027: a future Gregorian year.
        val state = WheelDatePickerState.forDate(RecurringDate(SolarHijri, 12, 10, 1405), today, allowNoYear = true)
        val flipped = state.withCalendar(Gregorian)
        assertEquals(RecurringDate(Gregorian, 3, 1, 2027), flipped.date)
        assertEquals(listOf(null, 2027, 2026), flipped.yearOptions.take(3))
        assertEquals(2027, flipped.withYear(2027).year)

        // 5 January 1900 is 15 Dey 1278, below the Solar Hijri minimum.
        val old = WheelDatePickerState.forDate(RecurringDate(Gregorian, 1, 5, 1900), today, allowNoYear = true)
            .withCalendar(SolarHijri)
        assertEquals(1278, old.year)
        assertEquals(1278, old.yearOptions.last())
        assertTrue(1279 in old.yearOptions)
    }

    @Test
    fun flippingToLunarHijriConvertsExactlyAndOffersItsYears() {
        val lunar = LunarHijri
        val state = WheelDatePickerState.forDate(RecurringDate(SolarHijri, 12, 11, 1403), today, allowNoYear = true)
            .withCalendar(lunar)
        assertEquals(RecurringDate(lunar, 9, 1, 1446), state.date)
        assertEquals(listOf(null, 1448), state.yearOptions.take(2))
        assertEquals(1317, state.yearOptions.last())
        // Without a year, every lunar month offers a 30th.
        assertEquals(30, state.withYear(null).withMonth(2).dayCount)
    }
}
