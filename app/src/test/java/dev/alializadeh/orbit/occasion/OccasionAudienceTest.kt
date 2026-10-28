package dev.alializadeh.orbit.occasion

import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.upcoming.UpcomingFeed
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OccasionAudienceTest {

    private val nowruz = RecurringDate(CalendarSystem.SolarHijri, 1, 1)
    private val sara = occasionPerson(1, ringId = 10, ringPosition = 1)
    private val reza = occasionPerson(2, ringId = 11, ringPosition = 0, tagIds = setOf(5))
    private val mina = occasionPerson(3, ringId = 12, ringPosition = 2, tagIds = setOf(6))
    private val archived = occasionPerson(4, ringId = 10, archived = true)
    private val people = listOf(sara, reza, mina, archived)

    @Test
    fun everyoneButTheArchivedInnermostRingFirst() {
        assertEquals(listOf(reza, sara, mina), occasionOf(1, nowruz, forEveryone = true).audience(people))
    }

    @Test
    fun anyOfItsRingsTagsAndPeopleIsEnough() {
        assertEquals(listOf(reza, sara), occasionOf(1, nowruz, ringIds = setOf(10), tagIds = setOf(5)).audience(people))
        assertEquals(listOf(mina), occasionOf(1, nowruz, personIds = setOf(3, 4)).audience(people))
        assertTrue(occasionOf(1, nowruz).audience(people).isEmpty())
    }

    @Test
    fun theFeedShowsOccasionsThatReachSomeoneByDay() {
        val today = LocalDate.of(2027, 3, 21) // 1 Farvardin 1406
        val yalda = RecurringDate(CalendarSystem.SolarHijri, 9, 30)
        val soon = RecurringDate(CalendarSystem.Gregorian, 4, 1)
        val feed = UpcomingFeed.of(
            people,
            today,
            listOf(
                occasionOf(1, nowruz, forEveryone = true, builtIn = BuiltInOccasion.Nowruz),
                occasionOf(2, soon, personIds = setOf(1)),
                occasionOf(3, soon, personIds = setOf(4)), // only an Archived Person: not shown
                occasionOf(4, yalda, forEveryone = true, builtIn = BuiltInOccasion.Yalda),
            ),
        )
        assertEquals(listOf(1L), feed.todayOccasions.map { it.occasion.id })
        assertEquals(listOf(reza, sara, mina), feed.todayOccasions.single().people)
        assertEquals(listOf(2L), feed.comingUpOccasions.map { it.occasion.id })
        assertEquals(11, feed.comingUpOccasions.single().daysAway)
        // Yalda is beyond Coming up, so it's "Next".
        assertEquals(4L, (feed.next as dev.alializadeh.orbit.upcoming.UpcomingOccasion).occasion.id)
        assertTrue(!feed.isEmpty)
    }
}
