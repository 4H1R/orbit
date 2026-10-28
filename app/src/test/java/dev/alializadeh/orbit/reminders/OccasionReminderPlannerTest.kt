package dev.alializadeh.orbit.reminders

import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.occasion.OccasionWithAudience
import dev.alializadeh.orbit.occasion.occasionOf
import dev.alializadeh.orbit.occasion.occasionPerson
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Occasion Reminders follow the Important dates' rules (PRD v1.1). */
class OccasionReminderPlannerTest {

    private val today = LocalDate.of(2026, 10, 28)
    private val nine = LocalTime.of(9, 0)
    private val inAWeek = RecurringDate(Gregorian, 11, 4)
    private val people = listOf(occasionPerson(1), occasionPerson(2))

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(ZoneOffset.UTC)

    private fun plan(
        occasions: List<OccasionWithAudience>,
        now: Instant,
        state: NotificationState = NotificationState(),
        everyone: List<UpcomingPerson> = people,
        channels: Set<ReminderChannel> = ReminderChannel.entries.toSet(),
        rendering: NotificationRendering = NotificationRendering(),
    ) = ReminderPlanner.plan(
        PlannerInput(
            people = everyone,
            reminderTime = nine,
            defaultLeadTimes = LeadTime.DEFAULT,
            now = now,
            zone = ZoneOffset.UTC,
            state = state,
            channelsOn = channels,
            rendering = rendering,
            occasions = occasions,
        ),
    )

    @Test
    fun aWeekBeforeAtTheReminderTimeOnceForEveryoneItReaches() {
        val occasion = occasionOf(1, inAWeek, forEveryone = true)
        assertTrue(plan(listOf(occasion), at(today, 8)).post.isEmpty())
        val first = plan(listOf(occasion), at(today, 9))
        val reminder = first.post.single() as PlannedOccasionReminder
        assertEquals(7, reminder.daysAway)
        assertEquals(LeadTime.OneWeek, reminder.leadTime)
        assertEquals(2, reminder.people.size)
        assertEquals(NotificationKey.OccasionReminder(1), reminder.key)
        // Not again, even later that day.
        assertTrue(plan(listOf(occasion), at(today, 18), first.state).post.isEmpty())
        // The next wake: midnight, so "in 7 days" becomes "in 6 days".
        assertEquals(at(today.plusDays(1), 0), first.nextAlarm)
    }

    @Test
    fun nothingForAnOccasionThatReachesNobodyOrWithItsChannelOff() {
        val nobody = occasionOf(1, inAWeek, personIds = setOf(99))
        assertTrue(plan(listOf(nobody), at(today, 9)).post.isEmpty())
        val everyone = occasionOf(2, inAWeek, forEveryone = true)
        val off = plan(listOf(everyone), at(today, 9), channels = setOf(ReminderChannel.ImportantDates))
        assertTrue(off.post.isEmpty())
        assertTrue(off.state.postedOccasionReminders.isEmpty())
    }

    @Test
    fun aShowingReminderIsQuietlyRepostedWhenItsDaysOrReachChange() {
        val occasion = occasionOf(1, inAWeek, forEveryone = true)
        val first = plan(listOf(occasion), at(today, 9))
        // The next morning: "in 6 days", quietly.
        val nextDay = plan(listOf(occasion), at(today.plusDays(1), 0), first.state)
        val again = nextDay.post.single() as PlannedOccasionReminder
        assertTrue(again.silent)
        assertEquals(6, again.daysAway)
        // Someone else is added: "3 people", quietly.
        val more = plan(listOf(occasion), at(today.plusDays(1), 1), nextDay.state, everyone = people + occasionPerson(3))
        assertEquals(3, (more.post.single() as PlannedOccasionReminder).people.size)
        assertTrue(more.post.single().silent)
    }

    @Test
    fun editingOrDeletingItCancelsItsReminder() {
        val occasion = occasionOf(1, inAWeek, forEveryone = true)
        val first = plan(listOf(occasion), at(today, 9))
        val moved = occasion.copy(occasion = occasion.occasion.copy(date = RecurringDate(Gregorian, 11, 5)))
        assertEquals(listOf(NotificationKey.OccasionReminder(1)), plan(listOf(moved), at(today, 10), first.state).cancel)
        assertEquals(listOf(NotificationKey.OccasionReminder(1)), plan(emptyList(), at(today, 10), first.state).cancel)
        // Nobody left in it: down too.
        assertEquals(listOf(NotificationKey.OccasionReminder(1)), plan(listOf(occasion), at(today, 10), first.state, everyone = emptyList()).cancel)
    }

    @Test
    fun onTheDayAndItsOwnLeadTimes() {
        val occasion = occasionOf(1, RecurringDate(Gregorian, 10, 28), forEveryone = true, leadTimes = setOf(LeadTime.OnTheDay))
        val reminder = plan(listOf(occasion), at(today, 9)).post.single() as PlannedOccasionReminder
        assertTrue(reminder.isOnTheDay)
        assertEquals(at(today.plusDays(1), 0), reminder.expiresAt)
        val none = occasionOf(2, inAWeek, forEveryone = true, leadTimes = emptySet())
        assertTrue(plan(listOf(none), at(today, 9)).post.isEmpty())
    }

    @Test
    fun aReminderThatWasntPostedStaysToBePostedNextTime() {
        val occasion = occasionOf(1, inAWeek, forEveryone = true)
        val first = plan(listOf(occasion), at(today, 9))
        val stored = first.stateAfter(emptySet())
        assertTrue(stored.postedOccasionReminders.isEmpty())
        assertTrue(stored.showingOccasionReminders.isEmpty())
        assertEquals(1, plan(listOf(occasion), at(today, 10), stored).post.size)
    }
}
