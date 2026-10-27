package dev.alializadeh.orbit.person

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.person.detail.DetailExit
import dev.alializadeh.orbit.person.detail.PersonDetailUiState
import dev.alializadeh.orbit.person.detail.PersonDetailViewModel
import dev.alializadeh.orbit.person.edit.CropUiState
import dev.alializadeh.orbit.person.edit.FormDialog
import dev.alializadeh.orbit.person.edit.PersonFormViewModel
import dev.alializadeh.orbit.person.edit.TagSuggestion
import dev.alializadeh.orbit.person.list.PeopleUiState
import dev.alializadeh.orbit.person.list.PeopleViewModel
import dev.alializadeh.orbit.person.photo.CropRect
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.testing.FakePersonRepository
import dev.alializadeh.orbit.testing.FakePhotoStore
import dev.alializadeh.orbit.testing.FakeRingRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import dev.alializadeh.orbit.testing.TestRings
import dev.alializadeh.orbit.testing.profileOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sara = profileOf(1, "Sara Ahmadi")
    private val photos = FakePhotoStore()

    // People list

    @Test
    fun peopleListShowsEveryoneButTheArchived() = runTest {
        val repository = FakePersonRepository(initial = listOf(sara, profileOf(2, "Reza")))
        val viewModel = PeopleViewModel(repository, photos)

        assertEquals(PeopleUiState.Loading, viewModel.uiState.value)
        viewModel.uiState.test {
            assertEquals(setOf("Sara Ahmadi", "Reza"), namesIn(awaitItem()))
            repository.setArchived(1, archived = true)
            assertEquals(setOf("Reza"), namesIn(awaitItem()))
        }
    }

    private fun namesIn(state: PeopleUiState) =
        (state as PeopleUiState.Loaded).people.map { it.summary.names.name }.toSet()

    // Person detail

    @Test
    fun personDetailShowsThePersonThenNotFoundOnceTheyAreGone() = runTest {
        val repository = FakePersonRepository(initial = listOf(sara))
        val viewModel = PersonDetailViewModel(sara.person.id, repository, photos)

        viewModel.uiState.test {
            assertEquals(sara, (awaitItem() as PersonDetailUiState.Loaded).profile)
            repository.profiles.value = emptyMap()
            assertEquals(PersonDetailUiState.NotFound, awaitItem())
        }
    }

    @Test
    fun archiveLeavesAndUndoBringsThemBack() = runTest {
        val repository = FakePersonRepository(initial = listOf(sara))
        val viewModel = PersonDetailViewModel(sara.person.id, repository, photos)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.archive()

        assertEquals(DetailExit.Archived, (viewModel.uiState.value as PersonDetailUiState.Loaded).exit)
        assertTrue(repository.people.single().isArchived)
        viewModel.undoArchive()
        assertFalse(repository.people.single().isArchived)
    }

    @Test
    fun deleteAsksFirstThenLeavesWithoutFlashingNotFound() = runTest {
        val repository = FakePersonRepository(initial = listOf(sara))
        val viewModel = PersonDetailViewModel(sara.person.id, repository, photos)
        val states = mutableListOf<PersonDetailUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { states += it } }

        viewModel.askToDelete()
        assertTrue((viewModel.uiState.value as PersonDetailUiState.Loaded).confirmingDelete)
        viewModel.delete()

        assertTrue(repository.people.isEmpty())
        assertEquals(DetailExit.Deleted, (viewModel.uiState.value as PersonDetailUiState.Loaded).exit)
        assertFalse(PersonDetailUiState.NotFound in states)
    }

    @Test
    fun addDetailsOffersTheEmptyFields() = runTest {
        val repository = FakePersonRepository(initial = listOf(sara.copy(likes = listOf("Jazz"))))
        val viewModel = PersonDetailViewModel(sara.person.id, repository, photos)

        viewModel.uiState.test {
            val missing = (awaitItem() as PersonDetailUiState.Loaded).missingFields
            assertFalse(PersonField.Likes in missing)
            assertTrue(PersonField.Dislikes in missing)
            assertTrue(PersonField.Photo in missing)
        }
    }

    // Add/Edit form

    private fun TestScope.form(
        repository: FakePersonRepository = FakePersonRepository(),
        personId: Long? = null,
        focus: PersonField? = null,
    ): PersonFormViewModel {
        val viewModel = PersonFormViewModel(personId, focus, repository, FakeRingRepository(), photos)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        settle()
        return viewModel
    }

    /** Lets snapshot observers see what the test changed, as a frame would in the app. */
    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.type(field: TextFieldState, text: String) {
        field.setTextAndPlaceCursorAtEnd(text)
        settle()
    }

    private val PersonFormViewModel.state get() = uiState.value

    @Test
    fun aNewPersonStartsInTheOutermostRingAndNeedsAName() = runTest {
        val viewModel = form()

        assertFalse(viewModel.state.isLoading)
        assertEquals(TestRings.acquaintances.id, viewModel.state.ringId)
        assertFalse(viewModel.state.canSave)
        type(viewModel.fields.name, "   ")
        assertFalse(viewModel.state.canSave)
        type(viewModel.fields.name, "Sara")
        assertTrue(viewModel.state.canSave)
    }

    @Test
    fun savingANewPersonStoresTheDraftAndReportsTheirId() = runTest {
        val repository = FakePersonRepository()
        val viewModel = form(repository)
        type(viewModel.fields.name, "  Sara Ahmadi ")
        viewModel.selectRing(TestRings.inner.id)
        viewModel.setCadence(CadenceSetting.Own(Cadence.weeks(3)))

        viewModel.save()
        settle()

        val person = repository.people.single()
        assertEquals("Sara Ahmadi", person.name)
        assertEquals(TestRings.inner.id, person.ringId)
        assertEquals(CadenceSetting.Own(Cadence.weeks(3)), person.cadenceSetting)
        assertEquals(person.id, viewModel.state.savedPersonId)
        // Saving twice stores one Person.
        viewModel.save()
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun leavingAsksOnlyWhenSomethingChanged() = runTest {
        val viewModel = form()

        assertFalse(viewModel.state.hasChanges)
        assertTrue(viewModel.requestClose())

        type(viewModel.fields.notes, "Met at the gym")
        assertTrue(viewModel.state.hasChanges)
        assertFalse(viewModel.requestClose())
        settle()
        assertEquals(FormDialog.Discard, viewModel.state.dialog)

        // Back to how it opened: no changes again, even with stray spaces.
        type(viewModel.fields.notes, "  ")
        assertFalse(viewModel.state.hasChanges)
    }

    @Test
    fun anEmptyContactRowIsNoChange() = runTest {
        val viewModel = form()

        viewModel.addContact()
        settle()

        assertFalse(viewModel.state.hasChanges)
    }

    @Test
    fun editOpensWithTheFieldsThatHaveContent() = runTest {
        val repository = FakePersonRepository(
            initial = listOf(
                sara.copy(
                    person = sara.person.copy(nickname = "Sari", notes = "Allergic to cats"),
                    likes = listOf("Jazz"),
                ),
            ),
        )
        val viewModel = form(repository, personId = sara.person.id)

        assertEquals("Sara Ahmadi", viewModel.fields.name.text.toString())
        assertFalse(viewModel.state.hasChanges)
        val visible = viewModel.state.visibleFields
        assertTrue(PersonField.Nickname in visible)
        assertTrue(PersonField.Likes in visible)
        assertTrue(PersonField.Notes in visible)
        assertFalse(PersonField.Dislikes in visible)
        assertTrue(viewModel.state.showsMoreDetails)

        viewModel.showMoreDetails()
        settle()
        assertTrue(PersonField.Dislikes in viewModel.state.visibleFields)
        assertFalse(viewModel.state.showsMoreDetails)
    }

    @Test
    fun anAddDetailsChipRevealsAndFocusesItsField() = runTest {
        val repository = FakePersonRepository(initial = listOf(sara))
        val viewModel = form(repository, personId = sara.person.id, focus = PersonField.HowWeMet)

        assertTrue(PersonField.HowWeMet in viewModel.state.visibleFields)
        assertEquals(PersonField.HowWeMet, viewModel.state.focus)
        viewModel.focusDone()
        settle()
        assertNull(viewModel.state.focus)
    }

    @Test
    fun aDuplicateNameAsksBeforeSaving() = runTest {
        val repository = FakePersonRepository(initial = listOf(sara))
        val viewModel = form(repository)
        type(viewModel.fields.name, "sara  ahmadi")

        viewModel.save()
        settle()

        assertEquals(FormDialog.DuplicateName("sara  ahmadi"), viewModel.state.dialog)
        assertEquals(1, repository.people.size)
        viewModel.saveAnyway()
        settle()
        assertEquals(2, repository.people.size)
    }

    @Test
    fun goBackOnTheDuplicateNoticeKeepsEditing() = runTest {
        val repository = FakePersonRepository(initial = listOf(sara))
        val viewModel = form(repository)
        type(viewModel.fields.name, "Sara Ahmadi")
        viewModel.save()
        settle()

        viewModel.dismissDialog()
        settle()

        assertNull(viewModel.state.dialog)
        assertTrue(viewModel.state.canSave)
        assertEquals(1, repository.people.size)
    }

    @Test
    fun editingSomeoneWithoutRenamingThemNeverAsksAboutDuplicates() = runTest {
        val repository = FakePersonRepository(initial = listOf(sara, profileOf(2, "Sara Ahmadi")))
        val viewModel = form(repository, personId = sara.person.id)
        type(viewModel.fields.notes, "New note")

        viewModel.save()
        settle()

        assertNull(viewModel.state.dialog)
        assertEquals(sara.person.id, viewModel.state.savedPersonId)
    }

    @Test
    fun theFirstContactMethodGetsTheStarAndRemovingItPassesItOn() = runTest {
        val viewModel = form()
        repeat(3) { viewModel.addContact() }
        settle()
        val (first, second, third) = viewModel.state.contacts.map { it.key }

        assertEquals(listOf(true, false, false), viewModel.state.contacts.map { it.isPrimary })

        viewModel.setPrimary(second)
        settle()
        assertEquals(listOf(false, true, false), viewModel.state.contacts.map { it.isPrimary })

        // Removing the primary passes the star to the next one…
        viewModel.removeContact(second)
        settle()
        assertEquals(listOf(first to false, third to true), viewModel.state.contacts.map { it.key to it.isPrimary })

        // …or, when it was last, to the one before.
        viewModel.removeContact(third)
        settle()
        assertEquals(listOf(first to true), viewModel.state.contacts.map { it.key to it.isPrimary })
    }

    @Test
    fun contactMethodsAreSavedWithTheirTypeAndStar() = runTest {
        val repository = FakePersonRepository()
        val viewModel = form(repository)
        type(viewModel.fields.name, "Sara")
        viewModel.addContact()
        viewModel.addContact()
        settle()
        val (phone, email) = viewModel.state.contacts
        type(phone.value, "۰۹۱۲ ۱۲۳ ۴۵۶۷")
        viewModel.setContactType(email.key, ContactType.Email)
        type(email.value, "sara@example.com")
        viewModel.setPrimary(email.key)

        viewModel.save()
        settle()

        val methods = repository.profiles.value.values.single().contactMethods
        assertEquals(listOf(ContactType.Phone, ContactType.Email), methods.map { it.type })
        assertEquals(listOf("0912 123 4567", "sara@example.com"), methods.map { it.value })
        assertEquals(listOf(false, true), methods.map { it.isPrimary })
    }

    @Test
    fun tagsAutocompleteAndOfferToCreate() = runTest {
        val repository = FakePersonRepository()
        repository.tags.value = listOf(Tag(1, "Gym"), Tag(2, "Work"), Tag(3, "Book club"))
        val viewModel = form(repository)

        type(viewModel.fields.tagInput, "o")
        assertEquals(
            listOf(TagSuggestion("Work", false), TagSuggestion("Book club", false), TagSuggestion("o", true)),
            viewModel.state.tagSuggestions,
        )

        type(viewModel.fields.tagInput, "Climbing")
        assertEquals(listOf(TagSuggestion("Climbing", isNew = true)), viewModel.state.tagSuggestions)

        // An existing Tag keeps its own spelling, and isn't offered twice.
        type(viewModel.fields.tagInput, "gym")
        viewModel.submitTag()
        settle()
        assertEquals(listOf("Gym"), viewModel.state.tags)
        type(viewModel.fields.tagInput, "gy")
        assertEquals(listOf(TagSuggestion("gy", isNew = true)), viewModel.state.tagSuggestions)
    }

    @Test
    fun itemsAreAddedEditedAndRemoved() = runTest {
        val repository = FakePersonRepository(initial = listOf(profileOf(2, "Reza").copy(likes = listOf("Jazz piano"))))
        val viewModel = form(repository)
        val likes = viewModel.fields.itemInputs.getValue(PersonItemKind.Like)

        type(likes, "jazz")
        assertEquals(listOf("Jazz piano"), viewModel.state.itemSuggestions[PersonItemKind.Like])
        viewModel.submitItem(PersonItemKind.Like)
        type(likes, "Tea")
        viewModel.submitItem(PersonItemKind.Like)
        settle()
        assertEquals(listOf("jazz", "Tea"), viewModel.state.items[PersonItemKind.Like])

        viewModel.editItem(PersonItemKind.Like, 0)
        settle()
        assertEquals("jazz", likes.text.toString())
        type(likes, "Jazz")
        viewModel.submitItem(PersonItemKind.Like)
        settle()
        assertEquals(listOf("Jazz", "Tea"), viewModel.state.items[PersonItemKind.Like])

        viewModel.removeItem(PersonItemKind.Like, 1)
        settle()
        assertEquals(listOf("Jazz"), viewModel.state.items[PersonItemKind.Like])
    }

    @Test
    fun textLeftInAnAddFieldIsSavedToo() = runTest {
        val repository = FakePersonRepository()
        val viewModel = form(repository)
        type(viewModel.fields.name, "Sara")
        type(viewModel.fields.itemInputs.getValue(PersonItemKind.Dislike), "Rain")
        type(viewModel.fields.tagInput, "Gym")

        viewModel.save()
        settle()

        val profile = repository.profiles.value.values.single()
        assertEquals(listOf("Rain"), profile.dislikes)
        assertEquals(listOf("Gym"), profile.tags.map(Tag::name))
    }

    @Test
    fun croppedPhotosNotSavedAreDeleted() = runTest {
        val viewModel = form()

        viewModel.startCrop("content://picked/1")
        settle()
        assertTrue(viewModel.state.crop is CropUiState.Ready)
        viewModel.finishCrop(CropRect(0f, 0f, 800f))
        settle()
        assertNull(viewModel.state.crop)
        assertEquals("photo-1.jpg", viewModel.state.photo?.name)

        // A second crop replaces the first, whose file goes at once.
        viewModel.startCrop("content://picked/2")
        settle()
        viewModel.finishCrop(CropRect(0f, 0f, 800f))
        settle()
        assertEquals(setOf("photo-2.jpg"), photos.files)

        viewModel.discard()
        assertTrue(photos.files.isEmpty())
    }

    @Test
    fun aSavedPhotoIsKept() = runTest {
        val repository = FakePersonRepository()
        val viewModel = form(repository)
        type(viewModel.fields.name, "Sara")
        viewModel.startCrop("content://picked/1")
        settle()
        viewModel.finishCrop(CropRect(0f, 0f, 800f))
        settle()

        viewModel.save()
        settle()

        assertEquals("photo-1.jpg", repository.people.single().photoFileName)
        assertEquals(setOf("photo-1.jpg"), photos.files)
    }

    @Test
    fun anUnreadablePhotoShowsTheFailure() = runTest {
        photos.image = null
        val viewModel = form()

        viewModel.startCrop("content://picked/broken")
        settle()

        assertEquals(CropUiState.Failed, viewModel.state.crop)
        viewModel.cancelCrop()
        settle()
        assertNull(viewModel.state.crop)
    }

    @Test
    fun editingSomeoneWhoIsGoneLeaves() = runTest {
        val viewModel = form(personId = 42)

        assertTrue(viewModel.state.notFound)
    }
}
