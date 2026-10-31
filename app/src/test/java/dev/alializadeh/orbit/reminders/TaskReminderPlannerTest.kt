package dev.alializadeh.orbit.reminders

import dev.alializadeh.orbit.person.FollowUp
import dev.alializadeh.orbit.person.Loan
import dev.alializadeh.orbit.person.LoanDirection
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.testing.NeverDue
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Follow-ups and Loans are brought up, on a fixed clock (the planner is pure). */
class TaskReminderPlannerTest {

    private val today = LocalDate.of(2026, 10, 28)

    private fun at(day: LocalDate, hour: Int): Instant = day.atTime(hour, 0).toInstant(ZoneOffset.UTC)

    private fun followUp(id: Long, on: LocalDate, text: String = "Interview") =
        FollowUp(id = id, personId = 1, text = text, dueOn = on, createdAt = Instant.EPOCH)

    private fun loan(id: Long, on: LocalDate?) =
        Loan(id = id, personId = 1, direction = LoanDirection.Lent, text = "Book", returnBy = on, createdAt = Instant.EPOCH)

    private fun person(followUps: List<FollowUp> = emptyList(), loans: List<Loan> = emptyList(), archived: Boolean = false) = UpcomingPerson(
        id = 1,
        names = PersonNames("Sara"),
        photoFileName = null,
        isArchived = archived,
        primaryContactMethod = null,
        importantDates = emptyList(),
        openGiftIdeas = emptyList(),
        keepInTouch = NeverDue,
        openFollowUps = followUps,
        openLoans = loans,
    )

    private fun plan(
        person: UpcomingPerson?,
        now: Instant,
        state: NotificationState = NotificationState(),
        channels: Set<ReminderChannel> = ReminderChannel.entries.toSet(),
        rendering: NotificationRendering = NotificationRendering(),
    ) = ReminderPlanner.plan(PlannerInput(listOfNotNull(person), LocalTime.of(9, 0), LeadTime.DEFAULT, now, ZoneOffset.UTC, state, channels, rendering))

    private val ReminderPlan.tasks get() = post.filterIsInstance<PlannedTaskReminder>()

    @Test
    fun itWaitsForTheReminderTimeOnItsDayWithAnAlarmSet() {
        val sara = person(listOf(followUp(1, today)))
        val before = plan(sara, at(today, 8))
        assertTrue(before.tasks.isEmpty())
        assertEquals(at(today, 9), before.nextAlarm)

        val plan = plan(sara, at(today, 9))
        assertEquals(listOf<NotificationKey>(NotificationKey.FollowUp(1)), plan.tasks.map { it.key })
        assertTrue(!plan.tasks.single().silent)
    }

    @Test
    fun aFutureDaySetsTheAlarmForThatDay() {
        val plan = plan(person(listOf(followUp(1, today.plusDays(5)))), at(today, 10))
        assertTrue(plan.tasks.isEmpty())
        assertEquals(at(today.plusDays(5), 9), plan.nextAlarm)
    }

    /** The phone was off on the day: as soon as Orbit runs, whatever the time. */
    @Test
    fun aMissedDayIsCaughtUpAtOnce() {
        val plan = plan(person(listOf(followUp(1, today.minusDays(3)))), at(today, 2))
        assertEquals(1, plan.tasks.size)
    }

    @Test
    fun itsNeverPostedTwiceEvenAfterBeingDismissed() {
        val sara = person(listOf(followUp(1, today)))
        val first = plan(sara, at(today, 9))
        val dismissed = first.state.retainShowing(emptySet())
        assertTrue(dismissed.showingTaskReminders.isEmpty())
        assertTrue(plan(sara, at(today, 10), dismissed).post.isEmpty())
        assertTrue(plan(sara, at(today.plusDays(4), 10), dismissed).post.isEmpty())
    }

    @Test
    fun aLoanWithADayIsBroughtUpAndOneWithoutNever() {
        val plan = plan(person(loans = listOf(loan(1, today), loan(2, null))), at(today, 9))
        assertEquals(listOf<NotificationKey>(NotificationKey.Loan(1)), plan.tasks.map { it.key })
    }

    @Test
    fun aMutedChannelPostsNothingAndCatchesUpLater() {
        val sara = person(listOf(followUp(1, today)))
        val muted = plan(sara, at(today, 9), channels = setOf(ReminderChannel.ImportantDates))
        assertTrue(muted.tasks.isEmpty())
        assertEquals(1, plan(sara, at(today.plusDays(1), 8), muted.state).tasks.size)
    }

    @Test
    fun doneDeletedOrArchivedTakesItDown() {
        val first = plan(person(listOf(followUp(1, today))), at(today, 9))
        val key = listOf<NotificationKey>(NotificationKey.FollowUp(1))
        assertEquals(key, plan(person(), at(today, 10), first.state).cancel)
        assertEquals(key, plan(null, at(today, 10), first.state).cancel)
        assertEquals(key, plan(person(listOf(followUp(1, today)), archived = true), at(today, 10), first.state).cancel)
    }

    /** Undo on Done, while its Reminder is still remembered: no second alert. */
    @Test
    fun undoingDoneDoesntAlertAgain() {
        val first = plan(person(listOf(followUp(1, today))), at(today, 9))
        val done = plan(person(), at(today, 10), first.state)
        assertTrue(plan(person(listOf(followUp(1, today))), at(today, 10), done.state).tasks.isEmpty())
    }

    @Test
    fun movingItToAnotherDayTakesItDownAndBringsItUpThen() {
        val first = plan(person(listOf(followUp(1, today))), at(today, 9))
        val moved = person(listOf(followUp(1, today.plusDays(2))))
        val after = plan(moved, at(today, 10), first.state)
        assertEquals(listOf<NotificationKey>(NotificationKey.FollowUp(1)), after.cancel)
        assertEquals(1, plan(moved, at(today.plusDays(2), 9), after.state).tasks.size)
    }

    @Test
    fun editingWhatItSaysOrAppLockRepostsQuietly() {
        val first = plan(person(listOf(followUp(1, today))), at(today, 9))

        val edited = plan(person(listOf(followUp(1, today, text = "Second interview"))), at(today, 10), first.state)
        assertTrue(edited.tasks.single().silent)

        val discreet = NotificationRendering(detail = NotificationDetail.Discreet)
        val locked = plan(person(listOf(followUp(1, today))), at(today, 10), first.state, rendering = discreet)
        assertTrue(locked.tasks.single().silent)

        assertTrue(plan(person(listOf(followUp(1, today))), at(today, 10), first.state).post.isEmpty())
    }

    /** A run that stops part way: what wasn't posted is posted by the next. */
    @Test
    fun whatWasntPostedIsLeftForTheNextRun() {
        val sara = person(listOf(followUp(1, today), followUp(2, today)))
        val plan = plan(sara, at(today, 9))
        val partial = plan.stateAfter(setOf(NotificationKey.FollowUp(1)))
        assertEquals(listOf("orbit.followup.1"), partial.showingTaskReminders.map { it.tag })
        assertEquals(listOf<NotificationKey>(NotificationKey.FollowUp(2)), plan(sara, at(today, 9), partial).tasks.map { it.key })
    }

    @Test
    fun theirTagsRoundTrip() {
        for (key in listOf(NotificationKey.FollowUp(7), NotificationKey.Loan(8))) {
            assertEquals(key, NotificationKey.fromTag(key.tag))
        }
    }
}
