package dev.alializadeh.orbit.upcoming

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.testing.FakePhotoStore
import dev.alializadeh.orbit.testing.FakeUpcomingRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import dev.alializadeh.orbit.testing.MutableClock
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tehran = ZoneId.of("Asia/Tehran")

    private fun person(id: Long, month: Int, day: Int, photo: String? = null, archived: Boolean = false) = UpcomingPerson(
        id = id,
        names = PersonNames("Person $id"),
        photoFileName = photo,
        isArchived = archived,
        primaryContactMethod = null,
        importantDates = listOf(
            ImportantDate(id = id, personId = id, type = ImportantDateType.Birthday, date = RecurringDate(Gregorian, month, day), position = 0),
        ),
        openGiftIdeas = emptyList(),
    )

    private suspend fun ReceiveTurbine<UpcomingUiState>.awaitLoaded(): UpcomingUiState.Loaded {
        while (true) {
            val state = awaitItem()
            if (state is UpcomingUiState.Loaded) return state
        }
    }

    @Test
    fun theFeedIsForTodayInTheClocksZone() = runTest {
        // 22:00 UTC on 27 Oct is already 28 Oct in Tehran.
        val clock = Clock.fixed(Instant.parse("2026-10-27T22:00:00Z"), tehran)
        val viewModel = UpcomingViewModel(FakeUpcomingRepository(listOf(person(1, 10, 28, photo = "sara.jpg"))), FakePhotoStore(), clock)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(LocalDate.of(2026, 10, 28), state.today)
            assertEquals(listOf(1L), state.feed.today.map { it.person.id })
            assertEquals(mapOf(1L to File("photos", "sara.jpg")), state.photos)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun peopleChangesRecomputeTheFeed() = runTest {
        val repository = FakeUpcomingRepository(listOf(person(1, 10, 30)))
        val clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), tehran)
        val viewModel = UpcomingViewModel(repository, FakePhotoStore(), clock)

        viewModel.uiState.test {
            assertEquals(1, awaitLoaded().feed.comingUp.size)
            repository.people.value = listOf(person(1, 10, 30, archived = true))
            val state = awaitLoaded()
            assertTrue(state.isEmpty)
            assertTrue(!state.feed.hasPeople)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun passingMidnightWhileOpenMovesTheFeedToTheNewDay() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-28T20:29:00Z"), tehran) // 23:59 in Tehran
        val viewModel = UpcomingViewModel(FakeUpcomingRepository(listOf(person(1, 10, 29))), FakePhotoStore(), clock)

        viewModel.uiState.test {
            val before = awaitLoaded()
            assertEquals(1, before.feed.comingUp.single().daysAway)

            clock.advance(Duration.ofMinutes(2))
            advanceTimeBy(Duration.ofMinutes(2).toMillis())

            val after = awaitLoaded()
            assertEquals(LocalDate.of(2026, 10, 29), after.today)
            assertEquals(listOf(1L), after.feed.today.map { it.person.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun resumingOnAnotherDayRecomputes() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-28T09:00:00Z"), tehran)
        val viewModel = UpcomingViewModel(FakeUpcomingRepository(listOf(person(1, 10, 31))), FakePhotoStore(), clock)

        viewModel.uiState.test {
            assertEquals(3, awaitLoaded().feed.comingUp.single().daysAway)
            // The phone slept through two midnights: the screen resuming notices.
            clock.advance(Duration.ofDays(2))
            viewModel.checkDate()
            assertEquals(1, awaitLoaded().feed.comingUp.single().daysAway)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theWaitForMidnightCountsInTheClocksZone() {
        val clock = Clock.fixed(Instant.parse("2026-10-28T20:00:00Z"), tehran) // 23:30 in Tehran
        assertEquals(Duration.ofMinutes(30).plusMillis(500), untilNextMidnight(clock))
    }
}
