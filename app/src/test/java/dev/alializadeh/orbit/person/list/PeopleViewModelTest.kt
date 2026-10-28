package dev.alializadeh.orbit.person.list

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import dev.alializadeh.orbit.person.PeopleSort
import dev.alializadeh.orbit.person.PersonDraft
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.search.InteractionNote
import dev.alializadeh.orbit.search.PeopleFilters
import dev.alializadeh.orbit.search.PeopleRequest
import dev.alializadeh.orbit.search.PeopleRequests
import dev.alializadeh.orbit.search.SearchField
import dev.alializadeh.orbit.testing.FakePersonRepository
import dev.alializadeh.orbit.testing.FakePhotoStore
import dev.alializadeh.orbit.testing.FakePreferencesRepository
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The People tab: search over the index, filters, the Archived view, sort and how long it all lasts. */
@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 28)
    private val gym = Tag(10, "Gym")
    private val work = Tag(11, "Work")

    // Everyone was added in 1970: with a Cadence and no Interaction, they're Due.
    private val sara = profileOf(1, "Sara Ahmadi", ring = TestRings.inner).copy(tags = listOf(gym), canHelpWith = listOf("Photography"))
    private val reza = profileOf(2, "Reza", ring = TestRings.close).copy(tags = listOf(work))
    private val mina = profileOf(3, "Mina", ring = TestRings.acquaintances).copy(tags = listOf(gym, work))
    private val ali = profileOf(4, "Ali", ring = TestRings.inner).let { it.copy(person = it.person.copy(archivedAt = Instant.EPOCH), likes = listOf("Photography")) }

    private val people = FakePersonRepository(initial = listOf(sara, reza, mina, ali)).apply {
        tags.value = listOf(gym, work)
        // Reza talked to recently: not Due.
        lastInteractions.value = mapOf(2L to today.minusDays(1))
    }
    private val preferences = FakePreferencesRepository()
    private val requests = PeopleRequests()

    private fun TestScope.viewModel(): PeopleViewModel {
        val viewModel = PeopleViewModel(
            personRepository = people,
            ringRepository = FakeRingRepository(),
            preferences = preferences,
            photoStore = FakePhotoStore(),
            clock = clock,
            requests = requests,
            indexDispatcher = mainDispatcherRule.dispatcher,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val PeopleViewModel.loaded get() = uiState.value as PeopleUiState.Loaded

    private val PeopleViewModel.names get() = loaded.people.map { it.summary.names.name }.toSet()

    private fun TestScope.type(viewModel: PeopleViewModel, text: String) {
        viewModel.query.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun everyoneButTheArchivedWithHowManyAreArchived() = runTest {
        val viewModel = viewModel()

        assertEquals(setOf("Sara Ahmadi", "Reza", "Mina"), viewModel.names)
        assertEquals(1, viewModel.loaded.archivedCount)
        assertFalse(viewModel.loaded.searching)
        assertTrue(viewModel.loaded.hasPeople)
    }

    @Test
    fun searchKeepsWhoMatchesWithHowAndCountsArchivedMatches() = runTest {
        val viewModel = viewModel()

        type(viewModel, "photo")

        assertTrue(viewModel.loaded.searching)
        assertEquals(setOf("Sara Ahmadi"), viewModel.names)
        assertEquals(SearchField.CanHelpWith, viewModel.loaded.people.single().hit!!.matches.first().field)
        // Ali likes photography but is Archived.
        assertEquals(1, viewModel.loaded.archivedMatches)

        viewModel.openArchived()
        assertEquals(setOf("Ali"), viewModel.names)
        assertEquals(0, viewModel.loaded.archivedMatches)

        type(viewModel, "")
        assertFalse(viewModel.loaded.searching)
        assertEquals(setOf("Ali"), viewModel.names)
        viewModel.closeArchived()
        assertEquals(setOf("Sara Ahmadi", "Reza", "Mina"), viewModel.names)
    }

    @Test
    fun filtersCombineWithAndAndSearchAppliesOnTop() = runTest {
        val viewModel = viewModel()

        viewModel.setTagFilter(setOf(gym.id, work.id))
        assertEquals(setOf("Sara Ahmadi", "Reza", "Mina"), viewModel.names)
        viewModel.setRingFilter(setOf(TestRings.inner.id, TestRings.close.id))
        assertEquals(setOf("Sara Ahmadi", "Reza"), viewModel.names)
        viewModel.toggleDue()
        assertEquals(setOf("Sara Ahmadi"), viewModel.names)
        assertTrue(viewModel.loaded.filters.isActive)

        type(viewModel, "reza")
        assertTrue(viewModel.loaded.people.isEmpty())

        viewModel.clearFilters()
        assertEquals(setOf("Reza"), viewModel.names)
        assertFalse(viewModel.loaded.filters.isActive)
    }

    @Test
    fun dateSoonKeepsPeopleWithAnImportantDateInTheNext30Days() = runTest {
        val viewModel = viewModel()
        people.saveImportantDate(
            reza.person.id,
            dev.alializadeh.orbit.person.ImportantDateDraft(
                type = dev.alializadeh.orbit.person.ImportantDateType.Birthday,
                date = dev.alializadeh.orbit.calendar.RecurringDate(dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian, 11, 20),
            ),
        )

        viewModel.toggleDateSoon()

        assertEquals(setOf("Reza"), viewModel.names)
    }

    @Test
    fun theIndexIsRebuiltWhenDataChanges() = runTest {
        val viewModel = viewModel()
        type(viewModel, "tennis")
        assertTrue(viewModel.loaded.people.isEmpty())

        people.save(PersonDraft.of(reza).copy(likes = listOf("Tennis")))
        assertEquals(setOf("Reza"), viewModel.names)

        type(viewModel, "berlin")
        assertTrue(viewModel.loaded.people.isEmpty())
        people.interactionNotes.value = mapOf(3L to listOf(InteractionNote(1, today, "Moving to Berlin")))
        assertEquals(setOf("Mina"), viewModel.names)
    }

    @Test
    fun aRemovedTagDropsOutOfTheFilter() = runTest {
        val viewModel = viewModel()
        viewModel.setTagFilter(setOf(work.id))
        assertEquals(setOf("Reza", "Mina"), viewModel.names)

        people.tags.value = listOf(gym)

        assertEquals(PeopleFilters(), viewModel.loaded.filters)
        assertEquals(setOf("Sara Ahmadi", "Reza", "Mina"), viewModel.names)
    }

    @Test
    fun theSortIsTheBackedUpPreference() = runTest {
        val viewModel = viewModel()
        assertEquals(PeopleSort.Name, viewModel.loaded.sort)

        viewModel.setSort(PeopleSort.LastInteraction)

        assertEquals(PeopleSort.LastInteraction, preferences.backedUp.value.peopleSort)
        assertEquals(PeopleSort.LastInteraction, viewModel.loaded.sort)
    }

    @Test
    fun queryFiltersAndViewLastAsLongAsTheViewModelButTheSortOutlivesIt() = runTest {
        val viewModel = viewModel()
        type(viewModel, "sara")
        viewModel.toggleDue()
        viewModel.setSort(PeopleSort.Ring)
        viewModel.openArchived()

        // Same ViewModel (another tab, Person detail and back): all still there.
        assertEquals("sara", viewModel.query.text.toString())
        assertTrue(viewModel.loaded.filters.due)
        assertTrue(viewModel.loaded.archivedView)

        // A new process makes a new ViewModel: nothing about the search was kept.
        val restarted = viewModel()
        assertEquals("", restarted.query.text.toString())
        assertEquals(PeopleFilters(), restarted.loaded.filters)
        assertFalse(restarted.loaded.archivedView)
        assertEquals(PeopleSort.Ring, restarted.loaded.sort)
    }

    @Test
    fun aChipSearchReplacesTheQueryAndFilters() = runTest {
        val viewModel = viewModel()
        viewModel.toggleDue()
        viewModel.openArchived()

        requests.send(PeopleRequest.Search("Photography"))
        Snapshot.sendApplyNotifications()
        runCurrent()

        assertEquals("Photography", viewModel.query.text.toString())
        assertEquals(PeopleFilters(), viewModel.loaded.filters)
        assertFalse(viewModel.loaded.archivedView)
        assertEquals(setOf("Sara Ahmadi"), viewModel.names)
        assertNull(requests.pending.value)
    }

    @Test
    fun showPeopleForATagFiltersByItAndClearsTheQuery() = runTest {
        val viewModel = viewModel()
        type(viewModel, "sara")

        requests.send(PeopleRequest.ShowTag(work.id))
        Snapshot.sendApplyNotifications()
        runCurrent()

        assertEquals("", viewModel.query.text.toString())
        assertEquals(PeopleFilters(tagIds = setOf(work.id)), viewModel.loaded.filters)
        assertEquals(setOf("Reza", "Mina"), viewModel.names)
    }

    @Test
    fun aRequestSentBeforeTheTabExistsWaitsForIt() = runTest {
        requests.send(PeopleRequest.ShowTag(gym.id))

        val viewModel = viewModel()

        assertEquals(setOf("Sara Ahmadi", "Mina"), viewModel.names)
        assertNull(requests.pending.value)
    }
}
