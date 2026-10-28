package dev.alializadeh.orbit.upcoming

import app.cash.turbine.test
import dev.alializadeh.orbit.calendar.LunarHijriAdjustment
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayFlowTest {

    @After
    fun resetAdjustment() = LunarHijriAdjustment.set(0)

    private val clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC)

    @Test
    fun theSameDayComesAgainWhenTheLunarAdjustmentChanges() = runTest {
        val checks = MutableSharedFlow<Unit>()
        todayFlow(clock, checks).test {
            assertEquals(LocalDate.of(2026, 10, 28), awaitItem())
            // A resume on the same day is nothing new.
            checks.emit(Unit)
            expectNoEvents()
            // Lunar Hijri occurrences move: whatever resolved them does it again.
            LunarHijriAdjustment.set(1)
            assertEquals(LocalDate.of(2026, 10, 28), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
