package dev.alializadeh.orbit.format

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The copy deck's relative-time boundaries. */
class RelativeDaysTest {

    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun aheadUsesWordsUpToAWeek() {
        assertEquals(RelativeAhead.Today, RelativeDays.ahead(today, today))
        assertEquals(RelativeAhead.Tomorrow, RelativeDays.ahead(today.plusDays(1), today))
        assertEquals(RelativeAhead.InDays(2), RelativeDays.ahead(today.plusDays(2), today))
        assertEquals(RelativeAhead.InDays(7), RelativeDays.ahead(today.plusDays(7), today))
        assertNull(RelativeDays.ahead(today.plusDays(8), today))
        assertNull(RelativeDays.ahead(today.minusDays(1), today))
    }

    @Test
    fun agoUsesDaysUpTo59() {
        assertEquals(RelativeAgo.Today, RelativeDays.ago(today, today))
        assertEquals(RelativeAgo.Today, RelativeDays.ago(today.plusDays(3), today))
        assertEquals(RelativeAgo.Yesterday, RelativeDays.ago(today.minusDays(1), today))
        assertEquals(RelativeAgo.DaysAgo(2), RelativeDays.ago(today.minusDays(2), today))
        assertEquals(RelativeAgo.DaysAgo(34), RelativeDays.ago(today.minusDays(34), today))
        assertEquals(RelativeAgo.DaysAgo(59), RelativeDays.ago(today.minusDays(59), today))
    }

    @Test
    fun agoUsesMonthsFrom60DaysTo23Months() {
        // 60 days is 1 month and 30 days by the calendar, still shown as 2 months.
        assertEquals(RelativeAgo.MonthsAgo(2), RelativeDays.ago(today.minusDays(60), today))
        assertEquals(RelativeAgo.MonthsAgo(3), RelativeDays.ago(today.minusMonths(3), today))
        assertEquals(RelativeAgo.MonthsAgo(12), RelativeDays.ago(today.minusYears(1), today))
        assertEquals(RelativeAgo.MonthsAgo(23), RelativeDays.ago(today.minusMonths(23), today))
        assertEquals(RelativeAgo.MonthsAgo(23), RelativeDays.ago(today.minusMonths(24).plusDays(1), today))
    }

    @Test
    fun agoUsesYearsFromTwoYears() {
        assertEquals(RelativeAgo.YearsAgo(2), RelativeDays.ago(today.minusMonths(24), today))
        assertEquals(RelativeAgo.YearsAgo(2), RelativeDays.ago(today.minusMonths(35), today))
        assertEquals(RelativeAgo.YearsAgo(10), RelativeDays.ago(today.minusYears(10), today))
    }
}
