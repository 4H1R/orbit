package dev.alializadeh.orbit.person

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import dev.alializadeh.orbit.person.add.AddPersonUiState
import dev.alializadeh.orbit.person.add.AddPersonViewModel
import dev.alializadeh.orbit.person.detail.PersonDetailUiState
import dev.alializadeh.orbit.person.detail.PersonDetailViewModel
import dev.alializadeh.orbit.person.list.PeopleUiState
import dev.alializadeh.orbit.person.list.PeopleViewModel
import dev.alializadeh.orbit.testing.FakePersonRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sara = Person(id = 1, name = "Sara", createdAt = Instant.EPOCH)

    @Test
    fun peopleListGoesFromLoadingToThePeople() = runTest {
        val viewModel = PeopleViewModel(FakePersonRepository(listOf(sara)))

        assertEquals(PeopleUiState.Loading, viewModel.uiState.value)
        viewModel.uiState.test {
            assertEquals(PeopleUiState.Loaded(listOf(sara)), awaitItem())
        }
    }

    @Test
    fun peopleListFollowsTheRepository() = runTest {
        val repository = FakePersonRepository()
        val viewModel = PeopleViewModel(repository)

        viewModel.uiState.test {
            assertEquals(PeopleUiState.Loaded(emptyList()), awaitItem())
            repository.addPerson("Sara")
            assertEquals(listOf("Sara"), (awaitItem() as PeopleUiState.Loaded).people.map(Person::name))
        }
    }

    @Test
    fun saveIsEnabledOnlyOnceTheNameIsNotBlank() = runTest {
        val viewModel = AddPersonViewModel(FakePersonRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertFalse(viewModel.uiState.value.canSave)
        viewModel.typeName("   ")
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.typeName("Sara")
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun savingStoresTheTrimmedNameAndReportsSaved() = runTest {
        val repository = FakePersonRepository()
        val viewModel = AddPersonViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.typeName("  Sara Ahmadi ")
        viewModel.save()

        assertEquals(listOf("Sara Ahmadi"), repository.people.value.map(Person::name))
        assertEquals(AddPersonUiState(canSave = false, isSaving = false, isSaved = true), viewModel.uiState.value)
    }

    @Test
    fun savingABlankNameDoesNothing() = runTest {
        val repository = FakePersonRepository()
        val viewModel = AddPersonViewModel(repository)

        viewModel.typeName(" ")
        viewModel.save()

        assertTrue(repository.people.value.isEmpty())
    }

    @Test
    fun saveTwiceStoresOnePerson() = runTest {
        val repository = FakePersonRepository()
        val viewModel = AddPersonViewModel(repository)

        viewModel.typeName("Sara")
        viewModel.save()
        viewModel.save()

        assertEquals(1, repository.people.value.size)
    }

    @Test
    fun personDetailShowsThePersonThenNotFoundOnceTheyAreGone() = runTest {
        val repository = FakePersonRepository(listOf(sara))
        val viewModel = PersonDetailViewModel(personId = sara.id, personRepository = repository)

        viewModel.uiState.test {
            assertEquals(PersonDetailUiState.Loaded(sara), awaitItem())
            repository.people.value = emptyList()
            assertEquals(PersonDetailUiState.NotFound, awaitItem())
        }
    }

    /** Types into the Name field as the UI would, then lets snapshot observers see it. */
    private fun AddPersonViewModel.typeName(text: String) {
        name.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }
}
