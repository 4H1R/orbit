package dev.alializadeh.orbit.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The filter definitions the People list and the Orbit view share. */
class PeopleFiltersTest {

    private val innerGymDue = FilterFacts(ringId = 1, tagIds = setOf(10, 11), isDue = true, hasDateSoon = false)
    private val closeWorkSoon = FilterFacts(ringId = 2, tagIds = setOf(12), isDue = false, hasDateSoon = true)
    private val friendsNothing = FilterFacts(ringId = 3, tagIds = emptySet(), isDue = false, hasDateSoon = false)
    private val everyone = listOf(innerGymDue, closeWorkSoon, friendsNothing)

    private fun PeopleFilters.keep() = everyone.filter(::matches)

    @Test
    fun noFiltersKeepEveryone() {
        assertFalse(PeopleFilters().isActive)
        assertEquals(everyone, PeopleFilters().keep())
    }

    @Test
    fun ringsAndTagsMatchAnyOfThoseChosen() {
        assertEquals(listOf(innerGymDue, closeWorkSoon), PeopleFilters(ringIds = setOf(1, 2)).keep())
        assertEquals(listOf(innerGymDue, closeWorkSoon), PeopleFilters(tagIds = setOf(11, 12)).keep())
        assertEquals(listOf(innerGymDue), PeopleFilters(tagIds = setOf(10)).keep())
    }

    @Test
    fun dueAndDateSoon() {
        assertEquals(listOf(innerGymDue), PeopleFilters(due = true).keep())
        assertEquals(listOf(closeWorkSoon), PeopleFilters(dateSoon = true).keep())
    }

    @Test
    fun differentFiltersCombineWithAnd() {
        assertEquals(listOf(closeWorkSoon), PeopleFilters(ringIds = setOf(1, 2), dateSoon = true).keep())
        assertEquals(emptyList<FilterFacts>(), PeopleFilters(tagIds = setOf(12), due = true).keep())
        assertEquals(listOf(innerGymDue), PeopleFilters(ringIds = setOf(1, 3), tagIds = setOf(10), due = true).keep())
    }

    @Test
    fun ringsAndTagsThatNoLongerExistDropOut() {
        val filters = PeopleFilters(ringIds = setOf(1, 9), tagIds = setOf(10), due = true)
        assertEquals(PeopleFilters(ringIds = setOf(1), tagIds = emptySet(), due = true), filters.existing(setOf(1, 2), setOf(11)))
        assertSame(filters, filters.existing(setOf(1, 9), setOf(10)))
    }

    @Test
    fun theStateTogglesAndClears() {
        val state = PeopleFilterState()
        state.setRings(setOf(1))
        state.setTags(setOf(10))
        state.toggleDue()
        state.toggleDateSoon()
        assertEquals(PeopleFilters(setOf(1), setOf(10), due = true, dateSoon = true), state.filters.value)
        assertTrue(state.filters.value.isActive)
        state.toggleDue()
        assertFalse(state.filters.value.due)
        state.clear()
        assertEquals(PeopleFilters(), state.filters.value)
    }
}
