package dev.alializadeh.orbit.person

import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.testing.FakePhotoStore
import dev.alializadeh.orbit.upcoming.RoomUpcomingRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Follow-ups and Loans through the Room repository and DAO, on the plain JVM. */
class FollowUpsAndLoansRepositoryTest {

    private val now = Instant.parse("2026-10-28T09:15:30Z")
    private val today = LocalDate.of(2026, 10, 28)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var database: OrbitDatabase
    private lateinit var repository: RoomPersonRepository
    private var personId = 0L

    @Before
    fun setUp() = runTest {
        database = TestDatabases.inMemory()
        repository = RoomPersonRepository(
            database = database,
            personDao = database.personDao(),
            tagDao = database.tagDao(),
            ringDao = database.ringDao(),
            photoStore = FakePhotoStore(),
            clock = clock,
        )
        personId = repository.save(PersonDraft(name = "Sara", ringId = database.insertTestRing()))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun profile() = repository.getProfile(personId)!!

    private suspend fun upcomingPerson() =
        RoomUpcomingRepository(database.personDao(), database.ringDao(), database.interactionDao(), clock).observePeople().first().single()

    @Test
    fun aFollowUpIsTrimmedSavedEditedSoonestFirstAndNeedsText() = runTest {
        val later = repository.saveFollowUp(personId, FollowUpDraft(text = "  Ask about the move ", dueOn = today.plusDays(9)))
        repository.saveFollowUp(personId, FollowUpDraft(text = "Interview", dueOn = today.plusDays(1)))
        assertEquals(listOf("Interview", "Ask about the move"), profile().followUps.map { it.text })
        assertEquals(now, profile().followUps.last().createdAt)

        repository.saveFollowUp(personId, FollowUpDraft(id = later, text = "New flat", dueOn = today))
        assertEquals(listOf("New flat", "Interview"), profile().followUps.map { it.text })

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.saveFollowUp(personId, FollowUpDraft(text = "  ", dueOn = today)) }
        }
    }

    @Test
    fun doneFollowUpsLeaveOpenButStayAndUndoBringsThemBack() = runTest {
        val id = repository.saveFollowUp(personId, FollowUpDraft(text = "Interview", dueOn = today))
        assertEquals(1, upcomingPerson().openFollowUps.size)

        repository.setFollowUpDone(id, today)
        assertTrue(profile().openFollowUps.isEmpty())
        assertEquals(today, profile().doneFollowUps.single().doneOn)
        assertTrue(upcomingPerson().openFollowUps.isEmpty())

        repository.setFollowUpDone(id, null)
        assertEquals(id, profile().openFollowUps.single().id)
    }

    @Test
    fun aLoanKeepsItsDirectionAndOptionalDayAndThoseWithADayComeFirst() = runTest {
        repository.saveLoan(personId, LoanDraft(direction = LoanDirection.Borrowed, text = "Drill"))
        val book = repository.saveLoan(personId, LoanDraft(direction = LoanDirection.Lent, text = " Book ", returnBy = today.plusDays(3)))
        assertEquals(listOf("Book", "Drill"), profile().loans.map { it.text })
        assertEquals(LoanDirection.Lent, profile().loans.first().direction)
        assertNull(profile().loans.last().returnBy)

        repository.saveLoan(personId, LoanDraft(id = book, direction = LoanDirection.Borrowed, text = "Book", returnBy = null))
        assertEquals(setOf(LoanDirection.Borrowed), profile().loans.map { it.direction }.toSet())
    }

    /** Only what has a day is a task for Upcoming and Reminders. */
    @Test
    fun onlyOpenLoansWithADayBecomeTasks() = runTest {
        repository.saveLoan(personId, LoanDraft(direction = LoanDirection.Lent, text = "No day"))
        val dated = repository.saveLoan(personId, LoanDraft(direction = LoanDirection.Lent, text = "Book", returnBy = today))
        assertEquals(listOf("Book"), upcomingPerson().tasks.map { it.text })
        assertEquals(2, upcomingPerson().openLoans.size)

        repository.setLoanReturned(dated, today)
        assertTrue(upcomingPerson().tasks.isEmpty())
        assertEquals("Book", profile().returnedLoans.single().text)
    }

    @Test
    fun theyGoWithThePerson() = runTest {
        repository.saveFollowUp(personId, FollowUpDraft(text = "Interview", dueOn = today))
        val loan = repository.saveLoan(personId, LoanDraft(direction = LoanDirection.Lent, text = "Book"))
        repository.delete(personId)
        assertNull(database.personDao().getLoan(loan))
    }

    /** Search finds them, whichever state they're in. */
    @Test
    fun theirTextIsSearchable() = runTest {
        repository.saveFollowUp(personId, FollowUpDraft(text = "Interview", dueOn = today))
        repository.saveLoan(personId, LoanDraft(direction = LoanDirection.Lent, text = "Book"))
        val summary = repository.observeEveryone().first().single()
        assertEquals(listOf("Interview"), summary.search.followUps)
        assertEquals(listOf("Book"), summary.search.loans)
    }
}
