package dev.alializadeh.orbit.person

import androidx.room3.useWriterConnection
import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.CalendarSystem.SolarHijri
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.reminders.LeadTime
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

/** Important dates and Gift ideas through the Room repository and DAO, on the plain JVM. */
class DatesAndGiftsRepositoryTest {

    private val now = Instant.parse("2026-10-28T09:15:30Z")
    private lateinit var database: OrbitDatabase
    private lateinit var repository: RoomPersonRepository
    private var ringId = 0L

    private val birthday = ImportantDateDraft(type = ImportantDateType.Birthday, date = RecurringDate(SolarHijri, 1, 14, 1370))
    private val anniversary = ImportantDateDraft(type = ImportantDateType.Anniversary, date = RecurringDate(Gregorian, 6, 1))

    @Before
    fun setUp() = runTest {
        database = TestDatabases.inMemory()
        repository = RoomPersonRepository(
            database = database,
            personDao = database.personDao(),
            tagDao = database.tagDao(),
            ringDao = database.ringDao(),
            photoStore = FakePhotoStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        ringId = database.insertTestRing()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun draft(name: String = "Sara") = PersonDraft(name = name, ringId = ringId)

    @Test
    fun importantDatesAreSavedWithThePersonBirthdayFirst() = runTest {
        val custom = ImportantDateDraft(type = ImportantDateType.Custom, label = "  Graduation ", date = RecurringDate(Gregorian, 7, 1, 2015))
        val id = repository.save(draft().copy(importantDates = listOf(anniversary, custom, birthday)))

        val dates = repository.getProfile(id)!!.importantDates
        assertEquals(listOf(ImportantDateType.Birthday, ImportantDateType.Anniversary, ImportantDateType.Custom), dates.map { it.type })
        assertEquals(RecurringDate(SolarHijri, 1, 14, 1370), dates.first().date)
        assertEquals("Graduation", dates.last().label)
        assertNull("only Custom dates keep a label", dates[1].label)
    }

    @Test
    fun aSecondBirthdayIsRefused() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.save(draft().copy(importantDates = listOf(birthday, birthday))) }
        }
        val id = repository.save(draft().copy(importantDates = listOf(birthday)))
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.saveImportantDate(id, birthday) }
        }
        // Editing the Birthday itself is fine.
        val existing = repository.getProfile(id)!!.birthday!!
        repository.saveImportantDate(id, ImportantDateDraft.of(existing).copy(date = RecurringDate(Gregorian, 4, 3, 1991)))
        assertEquals(RecurringDate(Gregorian, 4, 3, 1991), repository.getProfile(id)!!.birthday!!.date)
        assertEquals(1, repository.getProfile(id)!!.importantDates.size)
    }

    @Test
    fun editingThePersonKeepsTheIdsOfDatesThatStay() = runTest {
        val id = repository.save(draft().copy(importantDates = listOf(birthday, anniversary)))
        val before = repository.getProfile(id)!!.importantDates

        val edited = PersonDraft.of(repository.getProfile(id)!!).let { d ->
            d.copy(importantDates = listOf(d.importantDates[0].copy(date = RecurringDate(SolarHijri, 1, 15, 1370))))
        }
        repository.save(edited)

        val after = repository.getProfile(id)!!.importantDates
        assertEquals(listOf(before[0].id), after.map { it.id })
        assertEquals(RecurringDate(SolarHijri, 1, 15, 1370), after.single().date)
    }

    @Test
    fun defaultLeadTimesAndNoRemindersAreDifferent() = runTest {
        val id = repository.save(
            draft().copy(
                importantDates = listOf(
                    anniversary,
                    ImportantDateDraft(type = ImportantDateType.Custom, label = "A", date = RecurringDate(Gregorian, 1, 1), leadTimes = emptySet()),
                    ImportantDateDraft(
                        type = ImportantDateType.Custom,
                        label = "B",
                        date = RecurringDate(Gregorian, 1, 2),
                        leadTimes = setOf(LeadTime.OnTheDay, LeadTime.ThreeDays),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(null, emptySet(), setOf(LeadTime.OnTheDay, LeadTime.ThreeDays)),
            repository.getProfile(id)!!.importantDates.map { it.leadTimes },
        )
    }

    @Test
    fun datesAddedFromPersonDetailGoLastAndCanBeDeleted() = runTest {
        val id = repository.save(draft().copy(importantDates = listOf(anniversary)))
        val custom = repository.saveImportantDate(
            id,
            ImportantDateDraft(type = ImportantDateType.Custom, label = "Graduation", date = RecurringDate(Gregorian, 7, 1)),
        )

        assertEquals(listOf(0, 1), repository.getProfile(id)!!.importantDates.map { it.position })
        repository.deleteImportantDate(custom)
        assertEquals(listOf(ImportantDateType.Anniversary), repository.getProfile(id)!!.importantDates.map { it.type })
    }

    @Test
    fun giftIdeasAreAddedEditedGivenAndDeleted() = runTest {
        val id = repository.save(draft())
        val book = repository.saveGiftIdea(id, GiftIdeaDraft(text = " Fountain pen ", link = " lamy.com ", note = ""))
        val scarf = repository.saveGiftIdea(id, GiftIdeaDraft(text = "Scarf"))

        repository.saveGiftIdea(id, GiftIdeaDraft(id = book, text = "Lamy 2000", link = "lamy.com", note = "Black"))
        repository.setGiftIdeaGiven(scarf, LocalDate.of(2026, 3, 20))

        val profile = repository.getProfile(id)!!
        assertEquals(listOf("Lamy 2000", "Scarf"), profile.giftIdeas.map { it.text })
        assertEquals(listOf("Lamy 2000"), profile.openGiftIdeas.map { it.text })
        assertEquals("lamy.com", profile.giftIdeas.first().link)
        assertEquals("Black", profile.giftIdeas.first().note)
        assertEquals(LocalDate.of(2026, 3, 20), profile.givenGiftIdeas.single().givenOn)
        assertEquals(now, profile.giftIdeas.first().createdAt)

        repository.setGiftIdeaGiven(scarf, null)
        assertEquals(2, repository.getProfile(id)!!.openGiftIdeas.size)
        repository.deleteGiftIdea(book)
        assertEquals(listOf("Scarf"), repository.getProfile(id)!!.giftIdeas.map { it.text })
    }

    @Test
    fun aGiftIdeaNeedsItsText() = runTest {
        val id = repository.save(draft())
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.saveGiftIdea(id, GiftIdeaDraft(text = "  ")) }
        }
    }

    @Test
    fun deletingAPersonTakesTheirDatesAndGiftIdeas() = runTest {
        val sara = repository.save(draft("Sara").copy(importantDates = listOf(birthday, anniversary)))
        repository.saveGiftIdea(sara, GiftIdeaDraft(text = "Book"))
        val reza = repository.save(draft("Reza").copy(importantDates = listOf(birthday)))
        repository.saveGiftIdea(reza, GiftIdeaDraft(text = "Chess set"))

        repository.delete(sara)

        assertEquals(1L, count("important_date"))
        assertEquals(1L, count("gift_idea"))
    }

    @Test
    fun upcomingSeesEveryoneWithTheirDatesOpenIdeasAndPrimaryContact() = runTest {
        val sara = repository.save(
            draft("Sara").copy(
                importantDates = listOf(anniversary, birthday),
                contactMethods = listOf(
                    ContactMethodDraft(ContactType.Email, "sara@example.com"),
                    ContactMethodDraft(ContactType.Phone, "123456", isPrimary = true),
                ),
            ),
        )
        repository.saveGiftIdea(sara, GiftIdeaDraft(text = "Book"))
        val given = repository.saveGiftIdea(sara, GiftIdeaDraft(text = "Scarf"))
        repository.setGiftIdeaGiven(given, LocalDate.of(2026, 1, 1))
        val reza = repository.save(draft("Reza"))
        repository.setArchived(reza, archived = true)

        val people = RoomUpcomingRepository(database.personDao()).observePeople().first().associateBy { it.id }

        val saraRow = people.getValue(sara)
        assertEquals(listOf(ImportantDateType.Birthday, ImportantDateType.Anniversary), saraRow.importantDates.map { it.type })
        assertEquals(listOf("Book"), saraRow.openGiftIdeas)
        assertEquals("123456", saraRow.primaryContactMethod?.value)
        assertTrue(people.getValue(reza).isArchived)
    }

    private suspend fun count(table: String): Long = database.useWriterConnection { connection ->
        connection.usePrepared("SELECT COUNT(*) FROM $table") {
            it.step()
            it.getLong(0)
        }
    }
}
