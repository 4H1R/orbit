package dev.alializadeh.orbit.settings

import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.TagWithCount
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.testing.FakeRingRepository
import dev.alializadeh.orbit.testing.FakeTagRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import dev.alializadeh.orbit.testing.TestRings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RingsAndTagsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ringsCanBeAddedUntilSixAndReordered() = runTest {
        val repository = FakeRingRepository()
        val viewModel = RingsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.add("Colleagues", Cadence.months(2))
        viewModel.add("Neighbours", null)
        runCurrent()
        val full = viewModel.uiState.value as RingsUiState.Loaded
        assertEquals(Ring.MAX_COUNT, full.rings.size)
        assertFalse(full.canAdd)

        viewModel.add("Seventh", null)
        runCurrent()
        assertEquals(Ring.MAX_COUNT, repository.rings.value.size)

        val reversed = full.rings.map { it.ring.id }.reversed()
        viewModel.reorder(reversed)
        runCurrent()
        assertEquals(reversed, (viewModel.uiState.value as RingsUiState.Loaded).rings.map { it.ring.id })
    }

    @Test
    fun aRingWithPeopleIsOnlyRemovedOnceTheyAreMoved() = runTest {
        val repository = FakeRingRepository().apply { peopleCounts.value = mapOf(TestRings.inner.id to 3) }
        val viewModel = RingsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.remove(TestRings.inner.id)
        runCurrent()
        assertTrue(repository.rings.value.any { it.id == TestRings.inner.id })

        viewModel.movePeople(TestRings.inner.id, TestRings.close.id)
        runCurrent()
        viewModel.remove(TestRings.inner.id)
        runCurrent()
        assertEquals(listOf("Close", "Friends", "Acquaintances"), repository.rings.value.map(Ring::name))
        assertEquals(3, repository.peopleCounts.value[TestRings.close.id])
    }

    @Test
    fun renamingToAnExistingTagAsksToMergeThenMerges() = runTest {
        val repository = FakeTagRepository(listOf(TagWithCount(Tag(1, "Gym"), 12), TagWithCount(Tag(2, "Fitness"), 3)))
        val viewModel = TagsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.rename(2, " gym ")
        runCurrent()
        val asking = viewModel.uiState.value as TagsUiState.Loaded
        assertEquals(PendingMerge(Tag(2, "Fitness"), Tag(1, "Gym")), asking.pendingMerge)
        assertEquals("nothing changes until confirmed", 2, repository.tags.value.size)

        viewModel.confirmMerge()
        runCurrent()
        val merged = viewModel.uiState.value as TagsUiState.Loaded
        assertNull(merged.pendingMerge)
        assertEquals(listOf("Gym"), merged.tags.map { it.tag.name })
    }

    @Test
    fun aPlainRenameAndADeclinedMerge() = runTest {
        val repository = FakeTagRepository(listOf(TagWithCount(Tag(1, "Gym"), 2), TagWithCount(Tag(2, "Work"), 1)))
        val viewModel = TagsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.rename(1, "Climbing")
        viewModel.rename(2, "climbing")
        runCurrent()
        viewModel.dismissMerge()
        runCurrent()

        assertEquals(listOf("Climbing", "Work"), repository.tags.value.map { it.tag.name })
        assertNull((viewModel.uiState.value as TagsUiState.Loaded).pendingMerge)

        viewModel.delete(2)
        runCurrent()
        assertEquals(listOf("Climbing"), repository.tags.value.map { it.tag.name })
    }
}
