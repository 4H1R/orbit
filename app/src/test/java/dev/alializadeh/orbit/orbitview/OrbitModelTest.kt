package dev.alializadeh.orbit.orbitview

import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.DateSoon
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.search.PeopleFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Orbit view's model: drift, the badge's date, dimming, the TalkBack order and the names under Avatars. */
class OrbitModelTest {

    private val today = OrbitToday
    private val rings = EnOrbitRings

    private fun drift(cadenceDays: Int?, lastDaysAgo: Long, snoozedForDays: Long? = null): Float {
        val keep = KeepInTouch(
            cadence = cadenceDays?.let(Cadence::days),
            addedOn = today.minusDays(1000),
            lastInteraction = today.minusDays(lastDaysAgo),
            snoozedUntil = snoozedForDays?.let(today::plusDays),
        )
        return driftOf(keep, keep.on(today))
    }

    @Test
    fun driftIsDaysPastDueOverTheCadenceCappedAtOne() {
        assertEquals(0f, drift(30, lastDaysAgo = 10), 0f)
        // Due today: on the Ring, glowing.
        assertEquals(0f, drift(30, lastDaysAgo = 30), 0f)
        assertEquals(0.5f, drift(30, lastDaysAgo = 45), 1e-6f)
        assertEquals(1f, drift(30, lastDaysAgo = 60), 0f)
        assertEquals(1f, drift(30, lastDaysAgo = 400), 0f)
    }

    @Test
    fun snoozedPeopleAndPeopleWithoutACadenceNeverDrift() {
        assertEquals(0f, drift(30, lastDaysAgo = 45, snoozedForDays = 3), 0f)
        assertEquals(0f, drift(null, lastDaysAgo = 400), 0f)
    }

    @Test
    fun theNearestImportantDateWithinThirtyDays() {
        val soon = orbitSummary(1, "Sara", rings[0], 1, birthdayInDays = 5)
        assertEquals(NearestDate(ImportantDateType.Birthday, null, 5), nearestDate(soon.importantDates, today))
        assertEquals(0, nearestDate(orbitSummary(2, "Reza", rings[0], 1, birthdayInDays = 0).importantDates, today)!!.daysAway)
        assertNull(nearestDate(orbitSummary(3, "Mina", rings[0], 1, birthdayInDays = 31).importantDates, today))
        assertNull(nearestDate(emptyList(), today))
    }

    @Test
    fun everyoneButTheArchivedOnTheirRingWithDueAndTheBadge() {
        val state = orbitState(orbitFewPeople(persian = false), rings, EnOrbitTags, today, PeopleFilters()) { null }

        assertEquals(20, state.people.size)
        assertEquals(listOf("Inner", "Close", "Friends", "Acquaintances"), state.rings.map(OrbitRing::name))
        val sara = state.people.first { it.id == 1L }
        assertEquals(0, sara.ringIndex)
        assertTrue(sara.isDue)
        assertEquals(6f / 14f, sara.drift, 1e-6f)
        assertEquals(DateSoon.Soon, sara.dateSoon)
        assertEquals(DateSoon.Today, state.people.first { it.id == 5L }.dateSoon)
        // Snoozed: not Due, no drift.
        val snoozed = state.people.first { it.id == 7L }
        assertFalse(snoozed.isDue)
        assertEquals(0f, snoozed.drift, 0f)
        assertTrue(state.people.all(OrbitPerson::matches))
        assertFalse(state.nobodyMatches)
    }

    @Test
    fun filtersDimInsteadOfHidingAndForgetRemovedRingsAndTags() {
        val filters = PeopleFilters(ringIds = setOf(1, 99), tagIds = setOf(42), due = true)
        val state = orbitState(orbitFewPeople(persian = false), rings, EnOrbitTags, today, filters) { null }

        assertEquals(20, state.people.size)
        assertEquals(PeopleFilters(ringIds = setOf(1), due = true), state.filters)
        assertEquals(setOf(1L, 3L), state.people.filter(OrbitPerson::matches).map(OrbitPerson::id).toSet())
    }

    @Test
    fun filtersMatchingNobodyDimEveryone() {
        val state = orbitState(orbitFewPeople(persian = false), rings, EnOrbitTags, today, PeopleFilters(ringIds = setOf(4), due = true)) { null }
        assertTrue(state.nobodyMatches)
        assertTrue(state.people.none(OrbitPerson::matches))
    }

    @Test
    fun noPeopleIsTheEmptyState() {
        val state = orbitState(emptyList(), rings, emptyList(), today, PeopleFilters()) { null }
        assertFalse(state.hasPeople)
        assertFalse(state.nobodyMatches)
        assertEquals(4, state.rings.size)
    }

    @Test
    fun talkBackReadsRingByRingDueFirstLongestAgoThenByName() {
        val people = listOf(
            orbitSummary(1, "Zoe", rings[1], 5),
            orbitSummary(2, "Adam", rings[1], 2),
            orbitSummary(3, "Mina", rings[1], 40),
            orbitSummary(4, "Bita", rings[1], 90),
            orbitSummary(5, "Reza", rings[0], 1),
            orbitSummary(6, "Dimmed", rings[0], 100, tagIds = setOf(2)),
        )
        val state = orbitState(people, rings, EnOrbitTags, today, PeopleFilters(tagIds = emptySet())) { null }
            .let { loaded -> loaded.copy(people = loaded.people.map { if (it.id == 6L) it.copy(matches = false) else it }) }

        val order = talkBackOrder(state.rings, state.people, { it.names.name }, naturalOrder())

        assertEquals(listOf("Inner", "Close", "Friends", "Acquaintances"), order.map { it.ring.name })
        assertEquals(listOf("Reza"), order[0].people.map { it.names.name })
        assertEquals(listOf("Bita", "Mina", "Adam", "Zoe"), order[1].people.map { it.names.name })
        assertEquals(2, order[1].dueCount)
        assertTrue(order[2].people.isEmpty())
    }

    @Test
    fun theNameUnderAnAvatarIsANicknameInTheUiScriptOrTheFirstName() {
        assertEquals("Sara", PersonNames("Sara Ahmadi").orbitLabel(OrbitLanguage.English))
        assertEquals("Sari", PersonNames("Sara Ahmadi", nickname = "Sari").orbitLabel(OrbitLanguage.English))
        // A Persian nickname in the English UI: the first name instead.
        assertEquals("Sara", PersonNames("Sara Ahmadi", nickname = "سارا جان").orbitLabel(OrbitLanguage.English))
        // The Persian UI shows the Persian-script name.
        assertEquals("سارا", PersonNames("Sara Ahmadi", otherScriptName = "سارا احمدی").orbitLabel(OrbitLanguage.Persian))
    }
}
