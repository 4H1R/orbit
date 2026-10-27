package dev.alializadeh.orbit.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 33-year converter against the Iranian calendar authority's official table (1206–1498 SH,
 * vendored as `calendar/kabise.txt`; see `kabise-SOURCE.txt`), plus the research vectors.
 */
class SolarHijriRulesTest {

    private data class Row(val year: Int, val markedLeap: Boolean, val nowruz: LocalDate)

    private val table: List<Row> by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("calendar/kabise.txt"))
        stream.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (yearField, date) = line.trim().split(Regex("\\s+"))
                Row(
                    year = yearField.trimEnd('*').toInt(),
                    markedLeap = yearField.endsWith("*"),
                    nowruz = LocalDate.parse(date),
                )
            }
    }

    @Test
    fun theTableCoversEveryYearFrom1206To1498() {
        assertEquals((1206..1498).toList(), table.map(Row::year))
    }

    @Test
    fun nowruzMatchesTheOfficialTableEveryYear() {
        val mismatches = table.filter { row -> SolarHijriRules.toLocalDate(row.year, 1, 1) != row.nowruz }
        assertTrue("1 Farvardin wrong for ${mismatches.map(Row::year)}", mismatches.isEmpty())
    }

    @Test
    fun leapYearsMatchTheOfficialTableFrom1206To1497() {
        // A year's length is known from the next row's Nowruz, so the last row (1498) is excluded.
        val mismatches = table.zipWithNext().mapNotNull { (row, next) ->
            val officialLeap = next.nowruz.toEpochDay() - row.nowruz.toEpochDay() == 366L
            val ok = SolarHijriRules.isLeapYear(row.year) == officialLeap && row.markedLeap == officialLeap
            if (ok) null else row.year
        }
        assertTrue("Leap year wrong for $mismatches", mismatches.isEmpty())
    }

    @Test
    fun theDayBeforeNowruzIsTheLastDayOfEsfand() {
        table.drop(1).forEach { row ->
            val eve = SolarHijriRules.fromLocalDate(row.nowruz.minusDays(1))
            val expectedDay = if (SolarHijriRules.isLeapYear(row.year - 1)) 30 else 29
            assertEquals(CalendarDate(CalendarSystem.SolarHijri, row.year - 1, 12, expectedDay), eve)
            assertEquals(CalendarDate(CalendarSystem.SolarHijri, row.year, 1, 1), SolarHijriRules.fromLocalDate(row.nowruz))
        }
    }

    @Test
    fun everyDayFrom1900To2125RoundTrips() {
        var date = LocalDate.of(1900, 1, 1)
        var previous = SolarHijriRules.fromLocalDate(date.minusDays(1))
        while (date.year <= 2125) {
            val shamsi = SolarHijriRules.fromLocalDate(date)
            assertEquals(date, shamsi.toLocalDate())
            // Consecutive days are consecutive Solar Hijri days.
            val expected = if (previous.day < SolarHijriRules.monthLength(previous.year, previous.month)) {
                previous.copy(day = previous.day + 1)
            } else if (previous.month < 12) {
                previous.copy(month = previous.month + 1, day = 1)
            } else {
                previous.copy(year = previous.year + 1, month = 1, day = 1)
            }
            assertEquals(expected, shamsi)
            previous = shamsi
            date = date.plusDays(1)
        }
    }

    @Test
    fun researchVectors() {
        assertEquals(sh(1403, 12, 30), SolarHijriRules.fromLocalDate(LocalDate.of(2025, 3, 20)))
        assertEquals(sh(1404, 1, 1), SolarHijriRules.fromLocalDate(LocalDate.of(2025, 3, 21)))
        assertEquals(sh(1403, 1, 1), SolarHijriRules.fromLocalDate(LocalDate.of(2024, 3, 20)))
        assertEquals(sh(1405, 8, 5), SolarHijriRules.fromLocalDate(LocalDate.of(2026, 10, 27)))
        assertEquals(LocalDate.of(2030, 3, 20), SolarHijriRules.toLocalDate(1408, 12, 30))
        assertEquals(LocalDate.of(1900, 3, 21), SolarHijriRules.toLocalDate(1279, 1, 1))
    }

    @Test
    fun leapYearsAroundNowSometimesFiveApart() {
        assertEquals(listOf(1395, 1399, 1403, 1408, 1412), (1393..1413).filter(SolarHijriRules::isLeapYear))
    }

    @Test
    fun fiveAbanShiftsAgainstGregorian() {
        // ADR 0003: 27 Oct in most years, 26 Oct in 1403, 1407 and 1408.
        val octoberDays = (1400..1408).associateWith { SolarHijriRules.toLocalDate(it, 8, 5).dayOfMonth }
        assertEquals(mapOf(1400 to 27, 1401 to 27, 1402 to 27, 1403 to 26, 1404 to 27, 1405 to 27, 1406 to 27, 1407 to 26, 1408 to 26), octoberDays)
    }

    @Test
    fun monthLengths() {
        assertEquals(List(6) { 31 } + List(5) { 30 } + 29, (1..12).map { SolarHijriRules.monthLength(1404, it) })
        assertEquals(30, SolarHijriRules.monthLength(1403, 12))
        assertEquals(30, SolarHijriRules.maxMonthLength(12))
        assertEquals(31, SolarHijriRules.maxMonthLength(1))
        assertFalse(SolarHijriRules.isLeapYear(1405))
    }

    @Test(expected = IllegalArgumentException::class)
    fun thirtyEsfandInACommonYearIsRejected() {
        SolarHijriRules.toLocalDate(1404, 12, 30)
    }

    @Test
    fun calendarSystemsAgree() {
        assertEquals(29, CalendarSystem.Gregorian.maxMonthLength(2))
        assertEquals(28, CalendarSystem.Gregorian.monthLength(2026, 2))
        assertEquals(1405, CalendarSystem.SolarHijri.yearOf(LocalDate.of(2026, 10, 28)))
        assertEquals(2026, CalendarSystem.Gregorian.yearOf(LocalDate.of(2026, 10, 28)))
        assertEquals(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28).inCalendar(CalendarSystem.Gregorian).toLocalDate())
    }

    private fun sh(year: Int, month: Int, day: Int) = CalendarDate(CalendarSystem.SolarHijri, year, month, day)
}
