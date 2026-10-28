package dev.alializadeh.orbit.interaction

import app.cash.turbine.test
import dev.alializadeh.orbit.data.DeviceState
import dev.alializadeh.orbit.due.SnoozeDuration
import dev.alializadeh.orbit.person.detail.PersonDetailUiState
import dev.alializadeh.orbit.person.detail.PersonDetailViewModel
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.testing.FakeInteractionRepository
import dev.alializadeh.orbit.testing.FakePersonRepository
import dev.alializadeh.orbit.testing.FakePhotoStore
import dev.alializadeh.orbit.testing.FakePreferencesRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeepInTouchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 28)
    private val clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC)
    private val sara = profileOf(1, "Sara Ahmadi", ring = TestRings.close)
    private val reza = profileOf(2, "Reza Karimi")
    private val archived = profileOf(3, "Old Friend").let { it.copy(person = it.person.copy(archivedAt = Instant.EPOCH)) }
    private val people = FakePersonRepository(initial = listOf(sara, reza, archived))
    private val interactions = FakeInteractionRepository(people, clock)
    private val preferences = FakePreferencesRepository(initialDeviceState = DeviceState(lastInteractionType = InteractionType.Message))

    private fun TestScope.viewModel(): KeepInTouchViewModel {
        val viewModel = KeepInTouchViewModel(people, interactions, preferences, FakePhotoStore(), clock)
        // Keep the state flowing, as the screen does.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun logStartsFromThePersonTodayAndTheLastTypeWithoutArchivedCandidates() = runTest {
        val viewModel = viewModel()
        viewModel.logInteraction(sara.person.id)
        runCurrent()

        val log = viewModel.uiState.value.log!!
        assertNull(log.interactionId)
        assertEquals(InteractionType.Message, log.type)
        assertEquals(today, log.date)
        assertEquals(listOf(sara.person.id), log.people.map { it.id })
        assertEquals(setOf(1L, 2L), log.candidates.map { it.id }.toSet())
    }

    @Test
    fun savingLogsTheInteractionAndRemembersItsType() = runTest {
        val viewModel = viewModel()
        viewModel.logInteraction(sara.person.id)
        runCurrent()
        viewModel.dismiss()
        viewModel.save(
            InteractionDraft(type = InteractionType.InPerson, date = today.minusDays(1), note = " Coffee ", personIds = listOf(1, 2)),
        )
        runCurrent()

        val saved = interactions.interactions.value.values.single()
        assertEquals(InteractionType.InPerson, saved.first.type)
        assertEquals("Coffee", saved.first.note)
        assertEquals(listOf(1L, 2L), saved.second)
        assertEquals(InteractionType.InPerson, preferences.device.value.lastInteractionType)
        assertNull(viewModel.uiState.value.log)
    }

    @Test
    fun aFutureDateOrNobodyIsNotSaved() = runTest {
        val viewModel = viewModel()
        viewModel.save(InteractionDraft(type = InteractionType.Call, date = today.plusDays(1), personIds = listOf(1)))
        viewModel.save(InteractionDraft(type = InteractionType.Call, date = today, personIds = emptyList()))
        runCurrent()
        assertTrue(interactions.interactions.value.isEmpty())
    }

    @Test
    fun editReopensTheInteractionAndSavesItForEveryone() = runTest {
        val id = interactions.save(InteractionDraft(type = InteractionType.Call, date = today.minusDays(3), note = "Hi", personIds = listOf(1, 2)))
        val viewModel = viewModel()

        viewModel.editInteraction(id)
        runCurrent()
        val log = viewModel.uiState.value.log!!
        assertEquals(id, log.interactionId)
        assertEquals(InteractionType.Call, log.type)
        assertEquals(today.minusDays(3), log.date)
        assertEquals("Hi", log.note)
        assertEquals(listOf(1L, 2L), log.people.map { it.id })

        viewModel.save(InteractionDraft(id = id, type = InteractionType.Message, date = today, note = "Edited", personIds = listOf(1, 2)))
        runCurrent()
        assertEquals(1, interactions.interactions.value.size)
        val edited = interactions.interactions.value.getValue(id).first
        assertEquals(InteractionType.Message, edited.type)
        assertEquals("Edited", edited.note)
    }

    @Test
    fun deleteRemovesItAndItsSheetCloses() = runTest {
        val id = interactions.save(InteractionDraft(type = InteractionType.Call, date = today, personIds = listOf(1, 2)))
        val viewModel = viewModel()
        viewModel.editInteraction(id)
        runCurrent()

        viewModel.dismiss()
        viewModel.delete(id)
        runCurrent()

        assertTrue(interactions.interactions.value.isEmpty())
        assertNull(viewModel.uiState.value.log)
    }

    @Test
    fun snoozeSetsTheEndAndUndoRestoresWhatWasThere() = runTest {
        people.setSnoozedUntil(sara.person.id, today.minusDays(20)) // an old, ended Snooze
        val viewModel = viewModel()
        viewModel.openSnooze(sara.person.id)
        runCurrent()
        assertEquals(sara.person.id, viewModel.uiState.value.snooze?.personId)

        var undo: (suspend () -> Unit)? = null
        viewModel.dismiss()
        viewModel.snooze(sara.person.id, SnoozeDuration.OneWeek) { undo = it }
        runCurrent()
        assertEquals(today.plusDays(7), people.profiles.value.getValue(1).person.snoozedUntil)
        assertNotNull(undo)

        undo!!.invoke()
        assertEquals(today.minusDays(20), people.profiles.value.getValue(1).person.snoozedUntil)
    }

    @Test
    fun cancelSnoozeEndsIt() = runTest {
        people.setSnoozedUntil(sara.person.id, today.plusDays(3))
        val viewModel = viewModel()
        viewModel.cancelSnooze(sara.person.id)
        runCurrent()
        assertNull(people.profiles.value.getValue(1).person.snoozedUntil)
    }

    @Test
    fun personDetailShowsTheInteractionsAndWhereTheyStand() = runTest {
        // Sara is in Close (every 30 days); her latest Interaction was 31 days ago.
        interactions.save(InteractionDraft(type = InteractionType.Call, date = today.minusDays(40), personIds = listOf(1)))
        interactions.save(InteractionDraft(type = InteractionType.Other, date = today.minusDays(31), personIds = listOf(1, 2)))
        val detail = PersonDetailViewModel(1, people, interactions, FakePhotoStore(), preferences, clock)

        detail.uiState.test {
            var state = awaitItem()
            while (state !is PersonDetailUiState.Loaded) state = awaitItem()
            assertEquals(listOf(today.minusDays(31), today.minusDays(40)), state.interactions.map { it.interaction.date })
            val due = state.due!!
            assertTrue(due.isDue)
            assertEquals(Cadence.days(30), due.cadence)
            assertEquals(today.minusDays(31), due.lastInteraction)

            // Snoozed: not Due until it ends.
            people.setSnoozedUntil(1, today.plusDays(7))
            state = awaitItem() as PersonDetailUiState.Loaded
            assertEquals(today.plusDays(7), state.due!!.snoozedUntil)
            assertFalse(state.due!!.isDue)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
