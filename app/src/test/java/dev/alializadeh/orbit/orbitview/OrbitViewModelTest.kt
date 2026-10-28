package dev.alializadeh.orbit.orbitview

import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.search.PeopleFilters
import dev.alializadeh.orbit.testing.FakePersonRepository
import dev.alializadeh.orbit.testing.FakePhotoStore
import dev.alializadeh.orbit.testing.FakeRingRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import dev.alializadeh.orbit.testing.TestRings
import dev.alializadeh.orbit.testing.profileOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Orbit tab's ViewModel: data → model, its own filters dimming People, Rings removed under a filter. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrbitViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 28)
    private val gym = Tag(10, "Gym")

    // Added in 1970: with a Cadence and no Interaction, they're Due.
    private val sara = profileOf(1, "Sara Ahmadi", ring = TestRings.inner).copy(tags = listOf(gym))
    private val reza = profileOf(2, "Reza", ring = TestRings.close)
    private val mina = profileOf(3, "Mina", ring = TestRings.acquaintances)
    private val ali = profileOf(4, "Ali", ring = TestRings.inner).let { it.copy(person = it.person.copy(archivedAt = Instant.EPOCH)) }

    private val people = FakePersonRepository(initial = listOf(sara, reza, mina, ali)).apply {
        tags.value = listOf(gym)
        // Reza talked to yesterday: not Due.
        lastInteractions.value = mapOf(2L to today.minusDays(1))
    }
    private val rings = FakeRingRepository()

    private fun TestScope.viewModel(): OrbitViewModel {
        val viewModel = OrbitViewModel(people, rings, FakePhotoStore(), clock, mainDispatcherRule.dispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val OrbitViewModel.loaded get() = uiState.value as OrbitUiState.Loaded

    private val OrbitViewModel.matching get() = loaded.people.filter(OrbitPerson::matches).map { it.names.name }.toSet()

    @Test
    fun everyoneButTheArchivedOnTheirRings() {
        runTest {
            val viewModel = viewModel()

            assertEquals(listOf("Sara Ahmadi", "Reza", "Mina"), viewModel.loaded.people.map { it.names.name })
            assertEquals(listOf(0, 1, 3), viewModel.loaded.people.map(OrbitPerson::ringIndex))
            assertEquals(listOf(true, false, false), viewModel.loaded.people.map(OrbitPerson::isDue))
            assertEquals(1f, viewModel.loaded.people.first().drift, 0f)
            assertEquals(today, viewModel.loaded.today)
            assertEquals(4, viewModel.loaded.rings.size)
        }
    }

    @Test
    fun filtersDimTheOthersAndClearWithClear() {
        runTest {
            val viewModel = viewModel()

            viewModel.toggleDue()
            assertEquals(3, viewModel.loaded.people.size)
            assertEquals(setOf("Sara Ahmadi"), viewModel.matching)

            viewModel.toggleDue()
            viewModel.setTagFilter(setOf(gym.id))
            assertEquals(setOf("Sara Ahmadi"), viewModel.matching)

            viewModel.setRingFilter(setOf(TestRings.close.id))
            assertTrue(viewModel.loaded.nobodyMatches)

            viewModel.clearFilters()
            assertFalse(viewModel.loaded.filters.isActive)
            assertEquals(setOf("Sara Ahmadi", "Reza", "Mina"), viewModel.matching)
        }
    }

    @Test
    fun aLoggedInteractionTakesThemOffDue() {
        runTest {
            val viewModel = viewModel()
            assertTrue(viewModel.loaded.people.first { it.id == 1L }.isDue)

            people.lastInteractions.value += 1L to today

            val sara = viewModel.loaded.people.first { it.id == 1L }
            assertFalse(sara.isDue)
            assertEquals(0f, sara.drift, 0f)
        }
    }

    @Test
    fun aRemovedRingDropsOutOfTheFilter() {
        runTest {
            val viewModel = viewModel()
            viewModel.setRingFilter(setOf(TestRings.friends.id, TestRings.close.id))

            rings.rings.value = rings.rings.value.filterNot { it.id == TestRings.friends.id }

            assertEquals(PeopleFilters(ringIds = setOf(TestRings.close.id)), viewModel.loaded.filters)
            assertEquals(setOf("Reza"), viewModel.matching)
        }
    }

    @Test
    fun eachViewModelHasItsOwnFilters() {
        runTest {
            val orbit = viewModel()
            val another = viewModel()

            orbit.toggleDateSoon()

            assertTrue(orbit.loaded.filters.dateSoon)
            assertFalse(another.loaded.filters.isActive)
        }
    }
}
