package dev.alializadeh.orbit.upcoming

import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.CalendarSystem.SolarHijri
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.testing.NeverDue
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Upcoming shows about Important dates, on fixed days (the feed is pure: no clock or zone). */
class UpcomingFeedTest {

    private val today = LocalDate.of(2026, 10, 28) // 6 Aban 1405

    private var nextDateId = 1L

    private fun date(
        date: RecurringDate,
        type: ImportantDateType = ImportantDateType.Custom,
        label: String? = "Day",
        position: Int = 0,
    ) = ImportantDate(id = nextDateId++, personId = 0, type = type, label = label, date = date, position = position)

    private fun person(
        id: Long,
        vararg dates: ImportantDate,
        archived: Boolean = false,
        ideas: List<String> = emptyList(),
        keepInTouch: KeepInTouch = NeverDue,
        ringPosition: Int = 0,
    ) = UpcomingPerson(
        id = id,
        names = PersonNames("Person $id"),
        photoFileName = null,
        isArchived = archived,
        primaryContactMethod = null,
        importantDates = dates.map { it.copy(personId = id) },
        openGiftIdeas = ideas,
        keepInTouch = keepInTouch,
        ringPosition = ringPosition,
    )

    private fun UpcomingFeed.days(list: List<UpcomingDate>) = list.map { it.date }

    private fun followUp(id: Long, on: LocalDate) =
        dev.alializadeh.orbit.person.FollowUp(id = id, personId = 1, text = "Follow-up $id", dueOn = on, createdAt = java.time.Instant.EPOCH)

    private fun loan(id: Long, on: LocalDate?) = dev.alializadeh.orbit.person.Loan(
        id = id,
        personId = 1,
        direction = dev.alializadeh.orbit.person.LoanDirection.Lent,
        text = "Loan $id",
        returnBy = on,
        createdAt = java.time.Instant.EPOCH,
    )

    /** Overdue, today and the next week show, earliest first; a day's Follow-ups before its Loans. */
    @Test
    fun tasksListWhatsCameOrWithinAWeekEarliestFirst() {
        val sara = person(1).copy(
            openFollowUps = listOf(followUp(1, today), followUp(2, today.plusDays(7)), followUp(3, today.plusDays(8)), followUp(4, today.minusDays(5))),
            openLoans = listOf(loan(5, today), loan(6, null)),
        )
        val feed = UpcomingFeed.of(listOf(sara), today)
        assertEquals(listOf("Follow-up 4", "Follow-up 1", "Loan 5", "Follow-up 2"), feed.tasks.map { it.text })
        assertFalse(feed.isEmpty)
    }

    @Test
    fun anArchivedPersonsTasksAreLeftOut() {
        val archived = person(1, archived = true).copy(openFollowUps = listOf(followUp(1, today)))
        assertTrue(UpcomingFeed.of(listOf(archived), today).tasks.isEmpty())
    }

    /** A Follow-up doesn't make them Due: Cadence is a separate thing. */
    @Test
    fun aFollowUpDoesntMakeSomeoneDue() {
        val feed = UpcomingFeed.of(listOf(person(1).copy(openFollowUps = listOf(followUp(1, today.minusDays(9))))), today)
        assertTrue(feed.due.isEmpty())
    }

    @Test
    fun todayComingUpAndBeyondAreSplitByDaysAway() {
        val feed = UpcomingFeed.of(
            listOf(
                person(
                    1,
                    date(RecurringDate(Gregorian, 10, 28)), // today
                    date(RecurringDate(Gregorian, 10, 29)), // tomorrow
                    date(RecurringDate(Gregorian, 11, 27)), // 30 days away: the last day of Coming up
                    date(RecurringDate(Gregorian, 11, 28)), // 31 days away
                    date(RecurringDate(Gregorian, 10, 27)), // yesterday: next year
                ),
            ),
            today,
        )

        assertEquals(listOf(today), feed.days(feed.today))
        assertEquals(listOf(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 27)), feed.days(feed.comingUp))
        assertEquals(listOf(1, 30), feed.comingUp.map { it.daysAway })
        assertEquals(LocalDate.of(2026, 11, 28), feed.next?.date)
        assertEquals(31, feed.next?.daysAway)
    }

    @Test
    fun aSolarHijriDateFollowsItsOwnCalendarAsItsGregorianDayShifts() {
        // 5 Aban is 26 Oct in 1403 but 27 Oct in 1405.
        val aban5 = person(1, date(RecurringDate(SolarHijri, 8, 5)))

        assertEquals(LocalDate.of(2024, 10, 26), UpcomingFeed.of(listOf(aban5), LocalDate.of(2024, 10, 20)).comingUp.single().date)
        assertEquals(LocalDate.of(2026, 10, 27), UpcomingFeed.of(listOf(aban5), LocalDate.of(2026, 10, 20)).comingUp.single().date)
        // On 27 Oct 2024 it has passed: the next one is a year of its own Calendar away.
        assertEquals(LocalDate.of(2025, 10, 27), UpcomingFeed.of(listOf(aban5), LocalDate.of(2024, 10, 27)).next?.date)
    }

    @Test
    fun thirtyEsfandFallsOnTheTwentyNinthInACommonYear() {
        val esfand30 = person(1, date(RecurringDate(SolarHijri, 12, 30)))

        // 1403 is a leap year: 30 Esfand 1403 is 20 Mar 2025.
        assertEquals(LocalDate.of(2025, 3, 20), UpcomingFeed.of(listOf(esfand30), LocalDate.of(2025, 3, 10)).comingUp.single().date)
        // 1404 isn't: it falls on 29 Esfand 1404, also 20 Mar (2026), and shows today then.
        assertEquals(LocalDate.of(2026, 3, 20), UpcomingFeed.of(listOf(esfand30), LocalDate.of(2026, 3, 10)).comingUp.single().date)
        assertEquals(1, UpcomingFeed.of(listOf(esfand30), LocalDate.of(2026, 3, 20)).today.size)
    }

    @Test
    fun aLeapDayFallsOnTheTwentyEighthOfFebruaryInACommonYear() {
        val leapDay = person(1, date(RecurringDate(Gregorian, 2, 29)))

        assertEquals(LocalDate.of(2026, 2, 28), UpcomingFeed.of(listOf(leapDay), LocalDate.of(2026, 2, 20)).comingUp.single().date)
        assertEquals(LocalDate.of(2028, 2, 29), UpcomingFeed.of(listOf(leapDay), LocalDate.of(2028, 2, 20)).comingUp.single().date)
        assertEquals(1, UpcomingFeed.of(listOf(leapDay), LocalDate.of(2026, 2, 28)).today.size)
    }

    @Test
    fun theYearsCountIsKnownOnlyWithTheYear() {
        val feed = UpcomingFeed.of(
            listOf(
                person(
                    1,
                    date(RecurringDate(Gregorian, 10, 28, 1991), ImportantDateType.Birthday),
                    date(RecurringDate(SolarHijri, 8, 20), ImportantDateType.Anniversary),
                ),
            ),
            today,
        )

        assertEquals(35, feed.today.single().occurrence.yearsCount)
        assertNull(feed.comingUp.single().occurrence.yearsCount)
    }

    @Test
    fun archivedPeopleNeverAppear() {
        val feed = UpcomingFeed.of(
            listOf(
                person(1, date(RecurringDate(Gregorian, 10, 28)), archived = true),
                person(2, date(RecurringDate(Gregorian, 11, 2))),
            ),
            today,
        )

        assertTrue(feed.today.isEmpty())
        assertEquals(listOf(2L), feed.comingUp.map { it.person.id })
        assertTrue(feed.hasPeople)
    }

    @Test
    fun withNoOneButTheArchivedThereAreNoPeople() {
        assertFalse(UpcomingFeed.of(emptyList(), today).hasPeople)
        assertFalse(UpcomingFeed.of(listOf(person(1, archived = true)), today).hasPeople)
        assertTrue(UpcomingFeed.of(listOf(person(1)), today).hasPeople)
    }

    @Test
    fun withNothingInThirtyDaysTheNextDateIsStillKnown() {
        val feed = UpcomingFeed.of(listOf(person(1, date(RecurringDate(Gregorian, 1, 12)))), today)

        assertTrue(feed.today.isEmpty() && feed.comingUp.isEmpty())
        assertEquals(LocalDate.of(2027, 1, 12), feed.next?.date)
        assertNull(UpcomingFeed.of(listOf(person(1)), today).next)
    }

    @Test
    fun theSameDayIsOrderedBirthdayAnniversaryCustomThenPerson() {
        val day = RecurringDate(Gregorian, 10, 28)
        val feed = UpcomingFeed.of(
            listOf(
                person(1, date(day, ImportantDateType.Custom)),
                person(2, date(day, ImportantDateType.Anniversary), date(day, ImportantDateType.Birthday, position = 1)),
                person(3, date(day, ImportantDateType.Birthday)),
            ),
            today,
        )

        assertEquals(
            listOf(2L to ImportantDateType.Birthday, 3L to ImportantDateType.Birthday, 2L to ImportantDateType.Anniversary, 1L to ImportantDateType.Custom),
            feed.today.map { it.person.id to it.importantDate.type },
        )
    }

    @Test
    fun theFeedCarriesEachPersonsOpenGiftIdeas() {
        val feed = UpcomingFeed.of(listOf(person(1, date(RecurringDate(Gregorian, 10, 30)), ideas = listOf("Book", "Scarf"))), today)

        assertEquals(listOf("Book", "Scarf"), feed.comingUp.single().person.openGiftIdeas)
    }

    // Due

    private fun due(lastInteraction: LocalDate?, cadence: Cadence? = Cadence.days(30), snoozedUntil: LocalDate? = null) =
        KeepInTouch(cadence = cadence, addedOn = LocalDate.of(2026, 1, 1), lastInteraction = lastInteraction, snoozedUntil = snoozedUntil)

    @Test
    fun dueIsInnermostRingFirstThenLongestSinceTheLastContact() {
        val feed = UpcomingFeed.of(
            listOf(
                person(1, keepInTouch = due(today.minusDays(40)), ringPosition = 2),
                person(2, keepInTouch = due(today.minusDays(31)), ringPosition = 0),
                person(3, keepInTouch = due(today.minusDays(90)), ringPosition = 2),
                // Never talked: counted from the day they were added (300 days ago).
                person(4, keepInTouch = due(null), ringPosition = 2),
                person(5, keepInTouch = due(today.minusDays(29)), ringPosition = 0),
            ),
            today,
        )

        assertEquals(listOf(2L, 4L, 3L, 1L), feed.due.map { it.person.id })
        assertTrue(feed.due.all { it.state.isDue })
    }

    @Test
    fun snoozedArchivedAndNoCadencePeopleAreNotDue() {
        val feed = UpcomingFeed.of(
            listOf(
                person(1, keepInTouch = due(today.minusDays(40), snoozedUntil = today.plusDays(1))),
                person(2, keepInTouch = due(today.minusDays(40)), archived = true),
                person(3, keepInTouch = due(today.minusDays(400), cadence = null)),
                // The Snooze ended today: Due again.
                person(4, keepInTouch = due(today.minusDays(40), snoozedUntil = today)),
            ),
            today,
        )

        assertEquals(listOf(4L), feed.due.map { it.person.id })
    }
}
