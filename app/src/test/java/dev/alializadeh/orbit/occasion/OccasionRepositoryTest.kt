package dev.alializadeh.orbit.occasion

import androidx.room3.useWriterConnection
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.reminders.LeadTime
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Occasions in Room (plain JVM, bundled SQLite): one transaction per save, links CASCADE both ways. */
class OccasionRepositoryTest {

    private lateinit var database: OrbitDatabase
    private lateinit var repository: RoomOccasionRepository

    @Before
    fun setUp() {
        database = TestDatabases.inMemory()
        repository = RoomOccasionRepository(database, database.occasionDao())
    }

    @After
    fun tearDown() = database.close()

    private suspend fun person(name: String, ringId: Long): Long =
        database.personDao().insert(Person(name = name, ringId = ringId, createdAt = Instant.EPOCH))

    @Test
    fun aBuiltInOccasionKeepsItsOwnDayAndNoLabel() = runTest {
        val id = repository.save(
            OccasionDraft.builtIn(BuiltInOccasion.Nowruz).copy(label = "ignored", date = RecurringDate(CalendarSystem.Gregorian, 1, 1)),
        )
        val stored = repository.get(id)!!.occasion
        assertEquals(BuiltInOccasion.Nowruz, stored.builtIn)
        assertNull(stored.label)
        assertEquals(RecurringDate(CalendarSystem.SolarHijri, 1, 1), stored.date)
        assertTrue(stored.forEveryone)
    }

    @Test
    fun savingReplacesWhoItsForAndDeletingTakesTheLinks() = runTest {
        val ring = database.insertTestRing()
        val other = database.insertTestRing("Close", 1)
        val sara = person("Sara", ring)
        val draft = OccasionDraft(
            label = "  Book club  ",
            date = RecurringDate(CalendarSystem.LunarHijri, 9, 1, year = 1440),
            ringIds = setOf(ring),
            personIds = setOf(sara),
            leadTimes = setOf(LeadTime.OnTheDay),
        )
        val id = repository.save(draft)
        val saved = repository.observeOccasions().first().single()
        assertEquals("Book club", saved.occasion.label)
        assertNull(saved.occasion.date.year)
        assertEquals(setOf(ring), saved.ringIds)
        assertEquals(setOf(sara), saved.personIds)

        repository.save(OccasionDraft.of(saved).copy(ringIds = setOf(other), personIds = emptySet()))
        assertEquals(setOf(other), repository.get(id)!!.ringIds)
        assertTrue(repository.get(id)!!.personIds.isEmpty())

        // A Ring removed takes its link with it (CASCADE).
        database.useWriterConnection { it.usePrepared("DELETE FROM ring WHERE id = $other") { s -> s.step() } }
        assertTrue(repository.get(id)!!.ringIds.isEmpty())

        repository.delete(id)
        assertTrue(repository.observeOccasions().first().isEmpty())
    }

    @Test
    fun everyoneDropsTheLists() = runTest {
        val ring = database.insertTestRing()
        val id = repository.save(OccasionDraft(label = "Party", date = RecurringDate(CalendarSystem.Gregorian, 6, 1), forEveryone = true, ringIds = setOf(ring)))
        assertTrue(repository.get(id)!!.ringIds.isEmpty())
    }
}
