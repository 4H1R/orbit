package dev.alializadeh.orbit.reminders

import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.CalendarSystem.SolarHijri
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.testing.NeverDue
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every rule of ticket 07, on a fixed clock and zone (the planner is pure). */
class ReminderPlannerTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 28)
    private val nine = LocalTime.of(9, 0)

    private var nextDateId = 1L

    private fun at(day: LocalDate, hour: Int, minute: Int = 0, zone: ZoneId = utc): Instant =
        day.atTime(hour, minute).atZone(zone).toInstant()

    private fun birthday(month: Int, day: Int, year: Int? = null, leadTimes: Set<LeadTime>? = null) =
        date(RecurringDate(Gregorian, month, day, year), ImportantDateType.Birthday, leadTimes)

    private fun date(
        date: RecurringDate,
        type: ImportantDateType = ImportantDateType.Custom,
        leadTimes: Set<LeadTime>? = null,
        label: String? = if (type == ImportantDateType.Custom) "Graduation" else null,
    ) = ImportantDate(id = nextDateId++, personId = 0, type = type, label = label, date = date, leadTimes = leadTimes, position = 0)

    private fun person(
        id: Long,
        vararg dates: ImportantDate,
        keepInTouch: KeepInTouch = NeverDue,
        archived: Boolean = false,
    ) = UpcomingPerson(
        id = id,
        names = PersonNames("Person $id"),
        photoFileName = null,
        isArchived = archived,
        primaryContactMethod = null,
        importantDates = dates.map { it.copy(personId = id) },
        openGiftIdeas = emptyList(),
        keepInTouch = keepInTouch,
    )

    /** Due since [dueOn] (every 30 days, last contact 30 days before), notified on [notifiedOn]. */
    private fun dueSince(
        dueOn: LocalDate,
        notifiedOn: LocalDate? = null,
        snoozedUntil: LocalDate? = null,
    ) = KeepInTouch(
        cadence = Cadence.days(30),
        addedOn = dueOn.minusDays(30),
        lastInteraction = null,
        snoozedUntil = snoozedUntil,
        lastNotifiedDueOn = notifiedOn,
    )

    private fun plan(
        people: List<UpcomingPerson>,
        now: Instant,
        state: NotificationState = NotificationState(),
        defaults: Set<LeadTime> = LeadTime.DEFAULT,
        time: LocalTime = nine,
        zone: ZoneId = utc,
        channels: Set<ReminderChannel> = ReminderChannel.entries.toSet(),
        rendering: NotificationRendering = NotificationRendering(),
    ) = ReminderPlanner.plan(PlannerInput(people, time, defaults, now, zone, state, channels, rendering))

    private val ReminderPlan.reminders get() = post.filterIsInstance<PlannedReminder>()
    private val ReminderPlan.digests get() = post.filterIsInstance<PlannedDigest>()

    /** Runs the plan's result forward, as `reconcile()` would (the marker included). */
    private fun ReminderPlan.applyTo(people: List<UpcomingPerson>): List<UpcomingPerson> = people.map {
        if (it.id in markNotified) it.copy(keepInTouch = it.keepInTouch.copy(lastNotifiedDueOn = today)) else it
    }

    // Reminders: timing

    @Test
    fun onTheDayReminderIsPostedAtTheReminderTime() {
        val sara = person(1, birthday(10, 28, year = 1991))
        val plan = plan(listOf(sara), now = at(today, 9))
        val reminder = plan.reminders.single()
        assertEquals(LeadTime.OnTheDay, reminder.leadTime)
        assertEquals(0, reminder.daysAway)
        assertTrue(reminder.isOnTheDay)
        assertEquals(35, reminder.occurrence.yearsCount)
        assertEquals(at(today.plusDays(1), 0), reminder.expiresAt)
        // Next: next year's "1 week before".
        assertEquals(at(LocalDate.of(2027, 10, 21), 9), plan.nextAlarm)
    }

    @Test
    fun nothingBeforeTheReminderTimeAndTheAlarmIsSetForIt() {
        val sara = person(1, birthday(10, 28))
        val plan = plan(listOf(sara), now = at(today, 8, 59))
        assertTrue(plan.post.isEmpty())
        assertEquals(at(today, 9), plan.nextAlarm)
    }

    @Test
    fun aWeekBeforeReminderComesSevenDaysAhead() {
        val sara = person(1, birthday(11, 4))
        val plan = plan(listOf(sara), now = at(today, 9))
        val reminder = plan.reminders.single()
        assertEquals(LeadTime.OneWeek, reminder.leadTime)
        assertEquals(7, reminder.daysAway)
        assertEquals(LocalDate.of(2026, 11, 4), reminder.occurrence.date)
        // It expires at the end of the date's own day.
        assertEquals(at(LocalDate.of(2026, 11, 5), 0), reminder.expiresAt)
        assertEquals(at(LocalDate.of(2026, 11, 4), 9), plan.nextAlarm)
    }

    @Test
    fun aLateReminderIsPostedTheSameDayButOnlyOnce() {
        val sara = person(1, birthday(10, 28))
        val first = plan(listOf(sara), now = at(today, 15, 30))
        assertEquals(1, first.reminders.size)
        val again = plan(listOf(sara), now = at(today, 16), state = first.state)
        assertTrue(again.post.isEmpty())
        // Even once dismissed (no longer showing), it isn't posted again.
        val dismissed = plan(listOf(sara), now = at(today, 17), state = first.state.retainShowing(emptySet()))
        assertTrue(dismissed.post.isEmpty())
    }

    @Test
    fun aReminderMissedOnAnEarlierDayIsDropped() {
        // The "1 week before" was yesterday; the phone was off.
        val sara = person(1, birthday(11, 3))
        val plan = plan(listOf(sara), now = at(today, 10))
        assertTrue(plan.post.isEmpty())
        assertEquals(at(LocalDate.of(2026, 11, 3), 9), plan.nextAlarm)
    }

    @Test
    fun aDateAddedAfterALeadTimePassedGetsOnlyItsLaterReminders() {
        val sara = person(1, birthday(10, 31, leadTimes = setOf(LeadTime.OneWeek, LeadTime.OneDay, LeadTime.OnTheDay)))
        val plan = plan(listOf(sara), now = at(today, 12))
        assertTrue(plan.post.isEmpty())
        assertEquals(at(LocalDate.of(2026, 10, 30), 9), plan.nextAlarm)
        val dayBefore = plan(listOf(sara), now = at(LocalDate.of(2026, 10, 30), 9), state = plan.state)
        assertEquals(LeadTime.OneDay, dayBefore.reminders.single().leadTime)
        assertEquals(1, dayBefore.reminders.single().daysAway)
    }

    @Test
    fun eachLeadTimeOfADateGetsItsReminder() {
        val leads = LeadTime.entries.toSet()
        val graduation = date(RecurringDate(Gregorian, 12, 31), leadTimes = leads)
        val people = listOf(person(1, graduation))
        val days = LeadTime.entries.map { it.reminderDate(LocalDate.of(2026, 12, 31)) }
        assertEquals(
            listOf("2026-12-31", "2026-12-30", "2026-12-28", "2026-12-24", "2026-12-17", "2026-11-30").map(LocalDate::parse),
            days,
        )
        var state = NotificationState()
        for (day in days.sorted()) {
            val plan = plan(people, now = at(day, 9), state = state)
            val reminder = plan.reminders.single()
            assertEquals(day, reminder.leadTime.reminderDate(reminder.occurrence.date))
            state = plan.state
        }
    }

    @Test
    fun aMonthBeforeCountsBackCalendarMonths() {
        // 30 March 2027 minus a month is 28 February (no 30th).
        val date = date(RecurringDate(Gregorian, 3, 30), leadTimes = setOf(LeadTime.OneMonth))
        val plan = plan(listOf(person(1, date)), now = at(LocalDate.of(2027, 2, 28), 9))
        assertEquals(30, plan.reminders.single().daysAway)
    }

    @Test
    fun defaultLeadTimesComeFromThePreferenceAndAnEmptySetIsNoReminders() {
        val withDefault = person(1, birthday(10, 31))
        val noReminders = person(2, birthday(10, 28, leadTimes = emptySet()))
        val plan = plan(
            listOf(withDefault, noReminders),
            now = at(today, 9),
            defaults = setOf(LeadTime.ThreeDays),
        )
        assertEquals(listOf(1L), plan.reminders.map { it.person.id })
        assertEquals(LeadTime.ThreeDays, plan.reminders.single().leadTime)
        // Nothing more this year for either; next year's "3 days before" for the first.
        assertEquals(at(LocalDate.of(2027, 10, 28), 9), plan.nextAlarm)
    }

    @Test
    fun nothingAtAllMeansNoAlarm() {
        val plan = plan(listOf(person(1, birthday(10, 28, leadTimes = emptySet()))), now = at(today, 9))
        assertTrue(plan.post.isEmpty())
        assertNull(plan.nextAlarm)
    }

    // Reminders: dates that move

    @Test
    fun aMissingDayFallsOnTheMonthsLastDayAndLeadTimesCountFromThere() {
        // 29 Feb without a year: 28 Feb in 2027.
        val leap = person(1, birthday(2, 29))
        val weekBefore = plan(listOf(leap), now = at(LocalDate.of(2027, 2, 21), 9))
        assertEquals(LocalDate.of(2027, 2, 28), weekBefore.reminders.single().occurrence.date)
        assertEquals(7, weekBefore.reminders.single().daysAway)
        val onTheDay = plan(listOf(leap), now = at(LocalDate.of(2027, 2, 28), 9), state = weekBefore.state)
        assertTrue(onTheDay.reminders.single().isOnTheDay)
    }

    @Test
    fun solarHijriDatesRemindOnTheirOwnCalendarsDay() {
        // 14 Farvardin 1406 = 3 April 2027.
        val nowruzBirthday = person(1, date(RecurringDate(SolarHijri, 1, 14, 1370), ImportantDateType.Birthday))
        val plan = plan(listOf(nowruzBirthday), now = at(LocalDate.of(2027, 3, 27), 9))
        val reminder = plan.reminders.single()
        assertEquals(LocalDate.of(2027, 4, 3), reminder.occurrence.date)
        assertEquals(36, reminder.occurrence.yearsCount)
        assertEquals(at(LocalDate.of(2027, 4, 3), 9), plan.nextAlarm)
    }

    @Test
    fun aSolarHijriMissingDayFallsOnEsfandsLastDay() {
        // 30 Esfand without a year: 1405 is a common year, so 29 Esfand 1405 = 20 March 2027.
        val esfand = person(1, date(RecurringDate(SolarHijri, 12, 30)))
        val plan = plan(listOf(esfand), now = at(LocalDate.of(2027, 3, 13), 9))
        assertEquals(LocalDate.of(2027, 3, 20), plan.reminders.single().occurrence.date)
        assertEquals(at(LocalDate.of(2027, 3, 20), 9), plan.nextAlarm)
    }

    // Reminders: who gets them, and cleanup

    @Test
    fun archivedPeopleProduceNothing() {
        val archived = person(1, birthday(10, 28), keepInTouch = dueSince(today.minusDays(3)), archived = true)
        val plan = plan(listOf(archived), now = at(today, 9))
        assertTrue(plan.post.isEmpty())
        assertTrue(plan.markNotified.isEmpty())
        assertNull(plan.nextAlarm)
    }

    @Test
    fun archivingDeletingOrEditingADateCancelsItsReminder() {
        val date = birthday(11, 4)
        val sara = person(1, date)
        val posted = plan(listOf(sara), now = at(today, 9))
        val key = NotificationKey.DateReminder(date.id)
        assertEquals(listOf(key), posted.post.map { it.key })

        val later = at(today, 12)
        assertEquals(listOf(key), plan(listOf(sara.copy(isArchived = true)), later, posted.state).cancel)
        assertEquals(listOf(key), plan(listOf(sara.copy(importantDates = emptyList())), later, posted.state).cancel)
        assertEquals(listOf(key), plan(emptyList(), later, posted.state).cancel)
        val moved = sara.copy(importantDates = listOf(date.copy(date = RecurringDate(Gregorian, 11, 5))))
        assertEquals(listOf(key), plan(listOf(moved), later, posted.state).cancel)
        val ownLeadTimes = sara.copy(importantDates = listOf(date.copy(leadTimes = setOf(LeadTime.OnTheDay))))
        assertEquals(listOf(key), plan(listOf(ownLeadTimes), later, posted.state).cancel)
        // Unchanged: it stays.
        val unchanged = plan(listOf(sara), later, posted.state)
        assertTrue(unchanged.cancel.isEmpty())
        assertEquals(posted.state.showingReminders, unchanged.state.showingReminders)
        // A cancelled Reminder isn't posted again that day, even though the edited date has it too.
        val withYear = sara.copy(importantDates = listOf(date.copy(date = RecurringDate(Gregorian, 11, 4, 1990))))
        val edited = plan(listOf(withYear), later, posted.state)
        assertEquals(listOf(key), edited.cancel)
        assertTrue(edited.post.isEmpty())
        assertTrue(edited.state.showingReminders.isEmpty())
    }

    @Test
    fun aReminderExpiresAtTheEndOfTheDatesDay() {
        val date = birthday(11, 4)
        val sara = person(1, date)
        val posted = plan(listOf(sara), now = at(today, 9))
        val onTheDay = plan(listOf(sara), now = at(LocalDate.of(2026, 11, 4), 8), state = posted.state)
        assertTrue(onTheDay.cancel.isEmpty())
        val dayAfter = plan(listOf(sara), now = at(LocalDate.of(2026, 11, 5), 8), state = posted.state)
        assertEquals(listOf(NotificationKey.DateReminder(date.id)), dayAfter.cancel)
        assertTrue(dayAfter.state.showingReminders.isEmpty())
        // The posted record stays a week past the date, then goes.
        assertEquals(1, dayAfter.state.postedReminders.size)
        val weekAfter = plan(listOf(sara), now = at(LocalDate.of(2026, 11, 12), 8), state = dayAfter.state)
        assertTrue(weekAfter.state.postedReminders.isEmpty())
    }

    @Test
    fun settingTheClockBackADayDoesNotRepostAReminder() {
        val sara = person(1, birthday(10, 28))
        val posted = plan(listOf(sara), now = at(today, 9))
        val tomorrow = plan(listOf(sara), now = at(today.plusDays(1), 9), state = posted.state)
        val back = plan(listOf(sara), now = at(today, 10), state = tomorrow.state)
        assertTrue(back.reminders.isEmpty())
    }

    @Test
    fun aLaterReminderReplacesTheEarlierOneInTheDatesSlot() {
        val date = birthday(11, 4)
        val sara = person(1, date)
        val weekBefore = plan(listOf(sara), now = at(today, 9))
        val onTheDay = plan(listOf(sara), now = at(LocalDate.of(2026, 11, 4), 9), state = weekBefore.state)
        assertTrue(onTheDay.cancel.isEmpty())
        assertEquals(LeadTime.OnTheDay, onTheDay.reminders.single().leadTime)
        assertEquals(1, onTheDay.state.showingReminders.size)
        assertEquals(2, onTheDay.state.postedReminders.size)
    }

    @Test
    fun aMutedImportantDatesChannelPostsAndRecordsNothing() {
        val sara = person(1, birthday(10, 28))
        val muted = plan(listOf(sara), now = at(today, 9), channels = setOf(ReminderChannel.KeepInTouch))
        assertTrue(muted.post.isEmpty())
        assertTrue(muted.state.postedReminders.isEmpty())
        // Turned back on the same day: it catches up.
        val back = plan(listOf(sara), now = at(today, 18), state = muted.state)
        assertEquals(1, back.reminders.size)
    }

    // The Cadence digest

    @Test
    fun theDigestNamesWhoBecameDueAndMarksThem() {
        val reza = person(1, keepInTouch = dueSince(today.minusDays(2)))
        val mina = person(2, keepInTouch = dueSince(today))
        val notYet = person(3, keepInTouch = dueSince(today.plusDays(4)))
        val people = listOf(reza, mina, notYet)
        val plan = plan(people, now = at(today, 9))
        val digest = plan.digests.single()
        assertEquals(listOf(1L, 2L), digest.people.map { it.person.id })
        assertEquals(setOf(1L, 2L), plan.markNotified)
        assertEquals(listOf(1L, 2L), plan.state.digestPersonIds)
        assertEquals(today.toEpochDay(), plan.state.lastDigestEpochDay)
        // Next: the day the third one becomes Due.
        assertEquals(at(today.plusDays(4), 9), plan.nextAlarm)
    }

    @Test
    fun theDigestWaitsForTheReminderTime() {
        val reza = person(1, keepInTouch = dueSince(today.minusDays(2)))
        val plan = plan(listOf(reza), now = at(today, 7), state = NotificationState(lastDigestEpochDay = today.minusDays(1).toEpochDay()))
        assertTrue(plan.post.isEmpty())
        assertTrue(plan.markNotified.isEmpty())
        assertEquals(at(today, 9), plan.nextAlarm)
    }

    @Test
    fun theDigestComesAtMostOnceADay() {
        val reza = person(1, keepInTouch = dueSince(today.minusDays(2)))
        val first = plan(listOf(reza), now = at(today, 9))
        // Mina becomes Due at 15:00 (her Cadence was shortened): she waits for tomorrow's digest.
        val mina = person(2, keepInTouch = dueSince(today))
        val people = first.applyTo(listOf(reza)) + mina
        val afternoon = plan(people, now = at(today, 15), state = first.state)
        assertTrue(afternoon.post.isEmpty())
        assertEquals(at(today.plusDays(1), 9), afternoon.nextAlarm)
        val tomorrow = plan(people, now = at(today.plusDays(1), 9), state = afternoon.state)
        assertEquals(listOf(2L), tomorrow.digests.single().people.map { it.person.id })
    }

    @Test
    fun peopleAlreadyNotifiedThisPeriodAreNotNamedAgain() {
        val reza = person(1, keepInTouch = dueSince(today.minusDays(5), notifiedOn = today.minusDays(5)))
        val plan = plan(listOf(reza), now = at(today, 9))
        assertTrue(plan.post.isEmpty())
        assertTrue(plan.markNotified.isEmpty())
        assertNull(plan.nextAlarm)
    }

    @Test
    fun aMissedDigestGoesOutOnTheNextRun() {
        val reza = person(1, keepInTouch = dueSince(today.minusDays(4)))
        // The phone was off from three days ago until 08:00 today, before the Reminder time.
        val state = NotificationState(lastDigestEpochDay = today.minusDays(3).toEpochDay())
        val catchUp = plan(listOf(reza), now = at(today, 8), state = state)
        assertEquals(listOf(1L), catchUp.digests.single().people.map { it.person.id })
        // Today's own slot then finds nobody new: no second digest.
        val nine = plan(catchUp.applyTo(listOf(reza)), now = at(today, 9), state = catchUp.state)
        assertTrue(nine.post.isEmpty())
        assertEquals(today.toEpochDay(), nine.state.lastDigestEpochDay)
    }

    @Test
    fun aPersonWithAnOnTheDayReminderIsLeftOutOfTheDigestAndCountsAsNotified() {
        val sara = person(1, birthday(10, 28), keepInTouch = dueSince(today.minusDays(1)))
        val reza = person(2, keepInTouch = dueSince(today.minusDays(1)))
        val plan = plan(listOf(sara, reza), now = at(today, 9))
        assertEquals(listOf(1L), plan.reminders.map { it.person.id })
        assertEquals(listOf(2L), plan.digests.single().people.map { it.person.id })
        assertEquals(setOf(1L, 2L), plan.markNotified)
        // Tomorrow she isn't named either: she was notified for this Due period.
        val tomorrow = plan(plan.applyTo(listOf(sara, reza)), now = at(today.plusDays(1), 9), state = plan.state)
        assertTrue(tomorrow.digests.isEmpty())
    }

    @Test
    fun aloneWithAnOnTheDayReminderThereIsNoDigest() {
        val sara = person(1, birthday(10, 28), keepInTouch = dueSince(today.minusDays(1)))
        val plan = plan(listOf(sara), now = at(today, 9))
        assertTrue(plan.digests.isEmpty())
        assertEquals(setOf(1L), plan.markNotified)
        assertEquals(today.toEpochDay(), plan.state.lastDigestEpochDay)
    }

    @Test
    fun aDateWithoutAnOnTheDayReminderDoesntKeepThemOutOfTheDigest() {
        val noReminders = person(1, birthday(10, 28, leadTimes = emptySet()), keepInTouch = dueSince(today.minusDays(1)))
        val weekOnly = person(2, birthday(10, 28, leadTimes = setOf(LeadTime.OneWeek)), keepInTouch = dueSince(today))
        val plan = plan(listOf(noReminders, weekOnly), now = at(today, 9))
        assertEquals(listOf(1L, 2L), plan.digests.single().people.map { it.person.id })
    }

    @Test
    fun aMutedDatesChannelDoesntKeepThemOutOfTheDigest() {
        val sara = person(1, birthday(10, 28), keepInTouch = dueSince(today.minusDays(1)))
        val plan = plan(listOf(sara), now = at(today, 9), channels = setOf(ReminderChannel.KeepInTouch))
        assertEquals(listOf(1L), plan.digests.single().people.map { it.person.id })
    }

    @Test
    fun whenASnoozeEndsTheyAreNotifiedAgain() {
        // Notified 10 days ago, then snoozed until today.
        val reza = person(1, keepInTouch = dueSince(today.minusDays(10), notifiedOn = today.minusDays(10), snoozedUntil = today))
        val yesterday = plan(listOf(reza), now = at(today.minusDays(1), 9))
        assertTrue(yesterday.post.isEmpty())
        assertEquals(at(today, 9), yesterday.nextAlarm)
        val plan = plan(listOf(reza), now = at(today, 9), state = yesterday.state)
        assertEquals(listOf(1L), plan.digests.single().people.map { it.person.id })
    }

    @Test
    fun aMutedKeepInTouchChannelPostsNothingAndMarksNobody() {
        val reza = person(1, keepInTouch = dueSince(today.minusDays(2)))
        val plan = plan(listOf(reza), now = at(today, 9), channels = setOf(ReminderChannel.ImportantDates))
        assertTrue(plan.post.isEmpty())
        assertTrue(plan.markNotified.isEmpty())
        assertNull(plan.state.lastDigestEpochDay)
        assertNull(plan.nextAlarm)
    }

    @Test
    fun theDigestShrinksAsPeopleStopBeingDueAndGoesWithTheLast() {
        val reza = person(1, keepInTouch = dueSince(today.minusDays(2)))
        val mina = person(2, keepInTouch = dueSince(today.minusDays(1)))
        val posted = plan(listOf(reza, mina), now = at(today, 9))
        val people = posted.applyTo(listOf(reza, mina))

        // Reza: an Interaction logged today, so not Due.
        val contacted = people[0].copy(keepInTouch = people[0].keepInTouch.copy(lastInteraction = today))
        val shrunk = plan(listOf(contacted, people[1]), now = at(today, 10), state = posted.state)
        val update = shrunk.digests.single()
        assertTrue(update.silent)
        assertEquals(listOf(2L), update.people.map { it.person.id })
        assertEquals(listOf(2L), shrunk.state.digestPersonIds)

        // Mina snoozed: nobody left, so it's cancelled.
        val snoozed = people[1].copy(keepInTouch = people[1].keepInTouch.copy(snoozedUntil = today.plusWeeks(1)))
        val gone = plan(listOf(contacted, snoozed), now = at(today, 11), state = shrunk.state)
        assertEquals(listOf(NotificationKey.Digest), gone.cancel)
        assertTrue(gone.state.digestPersonIds.isEmpty())
    }

    @Test
    fun archivingOrDeletingADigestPersonTakesThemOut() {
        val reza = person(1, keepInTouch = dueSince(today.minusDays(2)))
        val posted = plan(listOf(reza), now = at(today, 9))
        val people = posted.applyTo(listOf(reza))
        assertEquals(listOf(NotificationKey.Digest), plan(listOf(people[0].copy(isArchived = true)), at(today, 10), posted.state).cancel)
        assertEquals(listOf(NotificationKey.Digest), plan(emptyList(), at(today, 10), posted.state).cancel)
    }

    @Test
    fun aDismissedDigestIsNotBroughtBack() {
        val reza = person(1, keepInTouch = dueSince(today.minusDays(2)))
        val mina = person(2, keepInTouch = dueSince(today.minusDays(1)))
        val posted = plan(listOf(reza, mina), now = at(today, 9))
        val people = posted.applyTo(listOf(reza, mina))
        val contacted = people[0].copy(keepInTouch = people[0].keepInTouch.copy(lastInteraction = today))
        val dismissed = posted.state.retainShowing(emptySet())
        val plan = plan(listOf(contacted, people[1]), now = at(today, 10), state = dismissed)
        assertTrue(plan.post.isEmpty())
        assertTrue(plan.cancel.isEmpty())
    }

    // Rendering (App lock)

    private val discreet = NotificationRendering(NotificationDetail.Discreet)

    @Test
    fun aNewRenderingQuietlyRepostsWhatsShowing() {
        val sara = person(1, birthday(11, 4)) // 1 week before: today
        val reza = person(2, keepInTouch = dueSince(today.minusDays(2)))
        val posted = plan(listOf(sara, reza), now = at(today, 9))
        assertEquals(NotificationRendering(), posted.state.renderedAs)
        val people = posted.applyTo(listOf(sara, reza))

        // App lock turned on an hour later.
        val rerendered = plan(people, now = at(today, 10), state = posted.state, rendering = discreet)

        val reminder = rerendered.reminders.single()
        assertTrue(reminder.silent)
        assertEquals(LeadTime.OneWeek, reminder.leadTime)
        assertEquals(7, reminder.daysAway)
        val digest = rerendered.digests.single()
        assertTrue(digest.silent)
        assertEquals(listOf(2L), digest.people.map { it.person.id })
        assertTrue(rerendered.cancel.isEmpty())
        assertTrue(rerendered.markNotified.isEmpty())
        assertEquals(discreet, rerendered.state.renderedAs)
        assertEquals(posted.state.showingReminders, rerendered.state.showingReminders)

        // Once re-rendered, nothing more until it changes again.
        assertTrue(plan(people, now = at(today, 11), state = rerendered.state, rendering = discreet).post.isEmpty())
    }

    @Test
    fun aDismissedNotificationIsNotBroughtBackByANewRendering() {
        val sara = person(1, birthday(10, 28))
        val posted = plan(listOf(sara), now = at(today, 9))
        val plan = plan(listOf(sara), now = at(today, 10), state = posted.state.retainShowing(emptySet()), rendering = discreet)
        assertTrue(plan.post.isEmpty())
    }

    @Test
    fun stateFromBeforeAppLockCountsAsTheDefaultRendering() {
        val sara = person(1, birthday(10, 28))
        val posted = plan(listOf(sara), now = at(today, 9))
        val old = posted.state.copy(renderedAs = null)
        assertTrue(plan(listOf(sara), now = at(today, 10), state = old).post.isEmpty())
    }

    // Time and zones

    @Test
    fun theNextAlarmIsTheEarliestOfRemindersAndDigest() {
        val sara = person(1, birthday(11, 10)) // 1 week before: 3 Nov
        val reza = person(2, keepInTouch = dueSince(today.plusDays(2))) // Due 30 Oct
        val plan = plan(listOf(sara, reza), now = at(today, 9))
        assertEquals(at(LocalDate.of(2026, 10, 30), 9), plan.nextAlarm)
    }

    @Test
    fun reminderTimesStayLocalAcrossDaylightSaving() {
        val london = ZoneId.of("Europe/London")
        // Summer time ends on 25 Oct 2026: 09:00 is 08:00 UTC before, 09:00 UTC after.
        val sara = person(1, birthday(10, 25))
        val before = plan(listOf(sara), now = at(LocalDate.of(2026, 10, 24), 12, zone = london), zone = london)
        assertEquals(Instant.parse("2026-10-25T09:00:00Z"), before.nextAlarm)
        val dayOf = plan(listOf(sara), now = Instant.parse("2026-10-25T09:00:00Z"), zone = london)
        assertEquals(1, dayOf.reminders.size)
        // 01:30 doesn't exist on 28 Mar 2027 in London: it moves to 02:30 BST (01:30 UTC).
        val spring = person(2, birthday(3, 28, leadTimes = setOf(LeadTime.OnTheDay)))
        val gap = plan(
            listOf(spring),
            now = at(LocalDate.of(2027, 3, 27), 12, zone = london),
            time = LocalTime.of(1, 30),
            zone = london,
        )
        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), gap.nextAlarm)
    }

    @Test
    fun theZoneDecidesWhatTodayIs() {
        val sara = person(1, birthday(10, 28))
        val now = Instant.parse("2026-10-27T21:00:00Z")
        // In Tehran (UTC+3:30) it's already 28 Oct, 00:30: the birthday is today, at 09:00 there.
        val tehran = plan(listOf(sara), now = now, zone = ZoneId.of("Asia/Tehran"))
        assertEquals(Instant.parse("2026-10-28T05:30:00Z"), tehran.nextAlarm)
        // In UTC it's still the 27th.
        val utcPlan = plan(listOf(sara), now = now)
        assertEquals(Instant.parse("2026-10-28T09:00:00Z"), utcPlan.nextAlarm)
        // Moving east after 09:00 local: the same day's Reminder is posted late.
        val lateInTehran = plan(listOf(sara), now = Instant.parse("2026-10-28T10:00:00Z"), zone = ZoneId.of("Asia/Tehran"))
        assertEquals(1, lateInTehran.reminders.size)
    }

    @Test
    fun changingTheReminderTimeMovesTheAlarm() {
        val sara = person(1, birthday(10, 28))
        assertEquals(at(today, 18, 30), plan(listOf(sara), now = at(today, 12), time = LocalTime.of(18, 30)).nextAlarm)
        // Already posted at 09:00, then the time moved later: not posted again.
        val posted = plan(listOf(sara), now = at(today, 9))
        assertTrue(plan(listOf(sara), now = at(today, 19), state = posted.state, time = LocalTime.of(18, 30)).post.isEmpty())
    }
}
