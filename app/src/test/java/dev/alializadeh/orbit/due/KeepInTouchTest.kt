package dev.alializadeh.orbit.due

import dev.alializadeh.orbit.person.CadenceSetting
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.withCadenceSetting
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Due's rules (glossary: Cadence, Due, Snooze), on a fixed day and zone. */
class KeepInTouchTest {

    private val today = LocalDate.of(2026, 10, 28)

    private fun facts(
        cadence: Cadence? = Cadence.days(30),
        addedOn: LocalDate = today.minusDays(100),
        lastInteraction: LocalDate? = null,
        snoozedUntil: LocalDate? = null,
        lastNotifiedDueOn: LocalDate? = null,
        archived: Boolean = false,
    ) = KeepInTouch(cadence, addedOn, lastInteraction, snoozedUntil, lastNotifiedDueOn, archived)

    @Test
    fun withoutAnInteractionTheDaysCountFromTheDayTheyWereAdded() {
        val justAdded = facts(addedOn = today.minusDays(29)).on(today)
        assertFalse(justAdded.isDue)
        assertEquals(today.plusDays(1), justAdded.dueOn)
        assertNull(justAdded.lastInteraction)

        val due = facts(addedOn = today.minusDays(30)).on(today)
        assertTrue("due the day the days reach the Cadence", due.isDue)
        assertEquals(30, due.daysSinceContact)
        assertEquals(0, due.daysOverdue)
    }

    @Test
    fun theLatestInteractionOfAnyTypeCountsAndThereIsNoGracePeriod() {
        val dayBefore = facts(lastInteraction = today.minusDays(29)).on(today)
        assertFalse(dayBefore.isDue)

        val onTheDay = facts(lastInteraction = today.minusDays(30)).on(today)
        assertTrue(onTheDay.isDue)

        val later = facts(lastInteraction = today.minusDays(37)).on(today)
        assertTrue(later.isDue)
        assertEquals(7, later.daysOverdue)
        assertEquals(today.minusDays(7), later.dueOn)
    }

    @Test
    fun anInteractionBeforeTheyWereAddedStillCounts() {
        // Backdated: the Interaction is what counts, not the added date.
        val state = facts(addedOn = today.minusDays(5), lastInteraction = today.minusDays(40)).on(today)
        assertTrue(state.isDue)
        assertEquals(40, state.daysSinceContact)
    }

    @Test
    fun monthCadencesFollowTheCalendarMonth() {
        // 31 Jan + 1 month is 28 Feb 2026 (not a leap year).
        val cadence = Cadence.months(1)
        val lastJan31 = facts(cadence = cadence, lastInteraction = LocalDate.of(2026, 1, 31))
        assertFalse(lastJan31.on(LocalDate.of(2026, 2, 27)).isDue)
        assertTrue(lastJan31.on(LocalDate.of(2026, 2, 28)).isDue)
        assertEquals(LocalDate.of(2026, 2, 28), lastJan31.dueOn)

        val quarterly = facts(cadence = Cadence.months(3), lastInteraction = LocalDate.of(2026, 7, 28))
        assertTrue(quarterly.on(today).isDue)
        assertFalse(quarterly.on(today.minusDays(1)).isDue)
    }

    @Test
    fun weekCadences() {
        val state = facts(cadence = Cadence.weeks(2), lastInteraction = today.minusDays(14)).on(today)
        assertTrue(state.isDue)
        assertFalse(facts(cadence = Cadence.weeks(2), lastInteraction = today.minusDays(13)).on(today).isDue)
    }

    @Test
    fun noCadenceIsNeverDue() {
        val state = facts(cadence = null, lastInteraction = today.minusDays(4000)).on(today)
        assertFalse(state.isDue)
        assertNull(state.dueOn)
        assertEquals(0f, state.progress)
        assertNull(state.cadence)
    }

    @Test
    fun archivedPeopleAreNeverDue() {
        val state = facts(lastInteraction = today.minusDays(400), archived = true).on(today)
        assertFalse(state.isDue)
        assertNull(state.dueOn)
        assertNull("no Cadence line for the Archived", state.cadence)
    }

    @Test
    fun aSnoozeHoldsThemBackUntilItEndsThenTheyAreDueAgain() {
        val snoozed = facts(lastInteraction = today.minusDays(40), snoozedUntil = today.plusDays(7))
        val during = snoozed.on(today)
        assertFalse(during.isDue)
        assertTrue(during.isSnoozed)
        assertEquals(today.plusDays(7), during.snoozedUntil)
        assertEquals(today.plusDays(7), during.dueOn)

        val lastDay = snoozed.on(today.plusDays(6))
        assertFalse(lastDay.isDue)

        val ended = snoozed.on(today.plusDays(7))
        assertTrue("Due again the day the Snooze ends", ended.isDue)
        assertFalse(ended.isSnoozed)
        assertEquals(0, ended.daysOverdue)
    }

    @Test
    fun aSnoozeIsNotAnInteractionAndDoesNotChangeTheCadence() {
        val state = facts(lastInteraction = today.minusDays(40), snoozedUntil = today.plusDays(3)).on(today)
        assertEquals(today.minusDays(40), state.lastInteraction)
        assertEquals(40, state.daysSinceContact)
        assertEquals(Cadence.days(30), state.cadence)
    }

    @Test
    fun aSnoozeOvertakenByANewInteractionNoLongerShows() {
        // Snoozed for a week, then they talked: the Cadence is now what's further away.
        val state = facts(lastInteraction = today, snoozedUntil = today.plusDays(7)).on(today)
        assertFalse(state.isDue)
        assertFalse(state.isSnoozed)
        assertEquals(today.plusDays(30), state.dueOn)
    }

    @Test
    fun anOldSnoozeChangesNothing() {
        val state = facts(lastInteraction = today.minusDays(40), snoozedUntil = today.minusDays(20)).on(today)
        assertTrue(state.isDue)
        assertEquals(today.minusDays(10), state.dueOn)
        assertFalse(state.isSnoozed)
    }

    @Test
    fun progressRunsThroughTheCadence() {
        assertEquals(0.5f, facts(lastInteraction = today.minusDays(15)).on(today).progress, 0.001f)
        assertEquals(0f, facts(lastInteraction = today).on(today).progress, 0.001f)
        assertEquals(1f, facts(lastInteraction = today.minusDays(90)).on(today).progress, 0.001f)
    }

    @Test
    fun notifiedOncePerDuePeriod() {
        val due = facts(lastInteraction = today.minusDays(35))
        assertFalse(due.on(today).notifiedThisPeriod)
        assertTrue(due.copy(lastNotifiedDueOn = today.minusDays(5)).on(today).notifiedThisPeriod)
        // Notified in an earlier period (before the latest Interaction made a new one).
        assertFalse(due.copy(lastNotifiedDueOn = today.minusDays(60)).on(today).notifiedThisPeriod)
        // After a Snooze ends it's a new period: they can be notified once more.
        val afterSnooze = due.copy(snoozedUntil = today.minusDays(1), lastNotifiedDueOn = today.minusDays(5))
        assertFalse(afterSnooze.on(today).notifiedThisPeriod)
    }

    @Test
    fun aCadenceChangeAloneDoesntStartANewDuePeriod() {
        // Every 30 days, last talked 60 days ago: Due since 30 days ago, and notified then.
        val notified = facts(lastInteraction = today.minusDays(60), lastNotifiedDueOn = today.minusDays(30))
        assertTrue(notified.on(today).notifiedThisPeriod)
        assertEquals(today.minusDays(59), notified.periodStart)
        // Lengthened to every 45 days: Due since 15 days ago, but still the same period.
        val longer = notified.copy(cadence = Cadence.days(45))
        assertEquals(today.minusDays(15), longer.on(today).dueOn)
        assertTrue(longer.on(today).notifiedThisPeriod)
        assertTrue(notified.copy(cadence = Cadence.days(20)).on(today).notifiedThisPeriod)
        // An Interaction after it (even on the day they were notified) starts a new period.
        val contacted = notified.copy(cadence = Cadence.days(20), lastInteraction = today.minusDays(30))
        assertTrue(contacted.on(today).isDue)
        assertFalse(contacted.on(today).notifiedThisPeriod)
        // A Snooze that ended starts one too; one that never held them back doesn't.
        assertFalse(notified.copy(snoozedUntil = today.minusDays(2)).on(today).notifiedThisPeriod)
        assertTrue(notified.copy(snoozedUntil = today.minusDays(40)).on(today).notifiedThisPeriod)
    }

    // From a Person and their Ring

    private val friends = Ring(id = 1, name = "Friends", position = 2, cadence = Cadence.days(90))
    private val inner = Ring(id = 2, name = "Inner", position = 0, cadence = Cadence.days(14))
    private val person = Person(
        id = 1,
        name = "Sara",
        ringId = friends.id,
        createdAt = Instant.parse("2026-01-01T09:00:00Z"),
    )

    @Test
    fun theRingsCadenceAppliesAndARingChangeTakesEffectAtOnce() {
        val last = today.minusDays(20)
        assertFalse(person.keepInTouch(friends, last, ZoneOffset.UTC).on(today).isDue)
        val moved = person.copy(ringId = inner.id)
        assertTrue(moved.keepInTouch(inner, last, ZoneOffset.UTC).on(today).isDue)
        // The Ring's own Cadence changing does the same.
        assertTrue(person.keepInTouch(friends.copy(cadence = Cadence.days(7)), last, ZoneOffset.UTC).on(today).isDue)
    }

    @Test
    fun anOverrideWinsOverTheRingIncludingNoCadence() {
        val last = today.minusDays(20)
        val own = person.withCadenceSetting(CadenceSetting.Own(Cadence.weeks(2)))
        assertTrue(own.keepInTouch(friends, last, ZoneOffset.UTC).on(today).isDue)

        val none = person.withCadenceSetting(CadenceSetting.Own(null))
        val state = none.keepInTouch(inner, today.minusDays(400), ZoneOffset.UTC).on(today)
        assertFalse(state.isDue)
        assertNull(state.cadence)
    }

    @Test
    fun theAddedDayIsTheDayInThePhonesZone() {
        // 22:00 UTC on 27 Sep is already 28 Sep in Tehran.
        val added = person.copy(createdAt = Instant.parse("2026-09-27T22:00:00Z"))
        val ring = friends.copy(cadence = Cadence.days(30))
        assertEquals(LocalDate.of(2026, 9, 28), added.keepInTouch(ring, null, ZoneId.of("Asia/Tehran")).addedOn)
        assertTrue(added.keepInTouch(ring, null, ZoneId.of("Asia/Tehran")).on(today).isDue)
        assertFalse(added.keepInTouch(ring, null, ZoneId.of("Asia/Tehran")).on(today.minusDays(1)).isDue)
    }

    @Test
    fun snoozeDurationsEndFromToday() {
        assertEquals(LocalDate.of(2026, 10, 31), SnoozeDuration.ThreeDays.endFrom(today))
        assertEquals(LocalDate.of(2026, 11, 4), SnoozeDuration.OneWeek.endFrom(today))
        assertEquals(LocalDate.of(2026, 11, 28), SnoozeDuration.OneMonth.endFrom(today))
        assertEquals(LocalDate.of(2026, 2, 28), SnoozeDuration.OneMonth.endFrom(LocalDate.of(2026, 1, 31)))
    }
}
