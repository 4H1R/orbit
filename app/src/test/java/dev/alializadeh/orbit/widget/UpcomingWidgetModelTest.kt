package dev.alializadeh.orbit.widget

import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.occasion.occasionOf
import dev.alializadeh.orbit.occasion.occasionPerson
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.upcoming.UpcomingDate
import dev.alializadeh.orbit.upcoming.UpcomingFeed
import dev.alializadeh.orbit.upcoming.UpcomingOccasion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingWidgetModelTest {

    private val today = LocalDate.of(2026, 10, 28)

    private fun birthday(personId: Long, month: Int, day: Int) =
        ImportantDate(id = personId, personId = personId, type = ImportantDateType.Birthday, date = RecurringDate(Gregorian, month, day), position = 0)

    private val sara = occasionPerson(1).copy(importantDates = listOf(birthday(1, 10, 28)))
    private val reza = occasionPerson(2).copy(importantDates = listOf(birthday(2, 11, 2)))
    private val mina = occasionPerson(3).copy(
        keepInTouch = KeepInTouch(Cadence.days(30), addedOn = today.minusDays(60), lastInteraction = null),
    )
    private val feed = UpcomingFeed.of(
        listOf(sara, reza, mina),
        today,
        listOf(occasionOf(1, RecurringDate(Gregorian, 10, 28), forEveryone = true), occasionOf(2, RecurringDate(Gregorian, 11, 1), forEveryone = true)),
    )

    @Test
    fun todaysOccasionsAndDatesThenComingUpByDay() {
        val model = UpcomingWidgetModel.of(feed, today, discreet = false)
        assertEquals(
            listOf("occasion 1", "date 1", "occasion 2", "date 2"),
            model.rows.map {
                when (it) {
                    is UpcomingOccasion -> "occasion ${it.occasion.id}"
                    is UpcomingDate -> "date ${it.person.id}"
                }
            },
        )
        assertEquals(2, model.todayCount)
        assertEquals(2, model.comingUpCount)
        assertEquals(1, model.dueCount)
    }

    @Test
    fun discreetShowsCountsOnly() {
        val model = UpcomingWidgetModel.of(feed, today, discreet = true)
        assertTrue(model.rows.isEmpty())
        assertEquals(listOf(2, 2, 1), listOf(model.todayCount, model.comingUpCount, model.dueCount))
    }

    @Test
    fun emptyIsAllCaughtUp() {
        assertTrue(UpcomingWidgetModel.of(UpcomingFeed.Empty, today, discreet = false).isEmpty)
    }
}
