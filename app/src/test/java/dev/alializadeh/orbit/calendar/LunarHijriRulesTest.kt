package dev.alializadeh.orbit.calendar

import java.time.LocalDate
import java.time.chrono.HijrahDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarHijriRulesTest {

    @After
    fun resetAdjustment() = LunarHijriAdjustment.set(0)

    private fun lunar(year: Int, month: Int, day: Int) = CalendarDate(CalendarSystem.LunarHijri, year, month, day)

    /** Umm al-Qura's published days (Saudi Arabia's official calendar). */
    @Test
    fun knownUmmAlQuraDays() {
        assertEquals(LocalDate.of(2025, 3, 1), lunar(1446, 9, 1).toLocalDate()) // 1 Ramadan 1446
        assertEquals(LocalDate.of(2025, 3, 30), lunar(1446, 10, 1).toLocalDate()) // Eid al-Fitr 1446
        assertEquals(LocalDate.of(2025, 6, 6), lunar(1446, 12, 10).toLocalDate()) // Eid al-Adha 1446
        assertEquals(LocalDate.of(2025, 6, 26), lunar(1447, 1, 1).toLocalDate()) // 1 Muharram 1447
        assertEquals(lunar(1317, 8, 29), LocalDate.of(1900, 1, 1).inCalendar(CalendarSystem.LunarHijri))
    }

    @Test
    fun everyDayFrom1900To2100RoundTrips() {
        var date = LocalDate.of(1900, 1, 1)
        val end = LocalDate.of(2100, 12, 31)
        while (!date.isAfter(end)) {
            val labelled = date.inCalendar(CalendarSystem.LunarHijri)
            assertEquals(date, labelled.toLocalDate())
            date = date.plusDays(1)
        }
    }

    @Test
    fun monthsHave29Or30DaysAndYears354Or355() {
        for (year in 1317..1530) {
            val days = (1..12).sumOf { CalendarSystem.LunarHijri.monthLength(year, it) }
            assertTrue("$year has $days days", days in 354..355)
            assertEquals(days == 355, CalendarSystem.LunarHijri.isLeapYear(year))
            for (month in 1..12) assertTrue(CalendarSystem.LunarHijri.monthLength(year, month) in 29..30)
        }
        assertEquals(30, CalendarSystem.LunarHijri.maxMonthLength(9))
    }

    @Test
    fun theAdjustmentMovesEveryLunarDateByWholeDays() {
        val ummAlQura = lunar(1446, 9, 1).toLocalDate()
        LunarHijriAdjustment.set(1)
        assertEquals(ummAlQura.plusDays(1), lunar(1446, 9, 1).toLocalDate())
        assertEquals(lunar(1446, 9, 1), ummAlQura.plusDays(1).inCalendar(CalendarSystem.LunarHijri))
        LunarHijriAdjustment.set(-5)
        assertEquals(-2, LunarHijriAdjustment.current)
        assertEquals(ummAlQura.minusDays(2), lunar(1446, 9, 1).toLocalDate())
        // Explicit, without the process-wide value (Settings previews each choice).
        assertEquals(lunar(1446, 8, 29), LunarHijriRules.fromLocalDate(ummAlQura, adjustment = 1)) // Sha'ban 1446 has 29 days
        // Other Calendars don't move.
        assertEquals(LocalDate.of(2025, 3, 1), CalendarDate(CalendarSystem.Gregorian, 2025, 3, 1).toLocalDate())
    }

    @Test
    fun aDay30FallsOnThe29thInAShortMonthAndADateCanComeTwiceInOneGregorianYear() {
        // A month that has 29 days in 1446.
        val shortMonth = (1..12).first { CalendarSystem.LunarHijri.monthLength(1446, it) == 29 }
        val date = RecurringDate(CalendarSystem.LunarHijri, shortMonth, 30)
        assertEquals(lunar(1446, shortMonth, 29).toLocalDate(), date.occurrenceIn(1446))

        val ramadan = RecurringDate(CalendarSystem.LunarHijri, 9, 1)
        val first = ramadan.nextOccurrence(LocalDate.of(1997, 1, 1))
        val second = ramadan.nextOccurrence(first.date.plusDays(1))
        assertEquals(LocalDate.from(HijrahDate.of(1417, 9, 1)), first.date)
        assertEquals(1997, first.date.year)
        assertEquals(1997, second.date.year)
        assertEquals(1418, second.calendarYear)
    }

    @Test
    fun yearsAreCountedInLunarYears() {
        val birthday = RecurringDate(CalendarSystem.LunarHijri, 9, 1, year = 1410)
        assertEquals(36, birthday.nextOccurrence(LocalDate.of(2025, 1, 1)).yearsCount)
    }
}
