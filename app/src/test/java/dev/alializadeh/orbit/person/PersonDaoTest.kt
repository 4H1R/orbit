package dev.alializadeh.orbit.person

import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** DAO tests run on the plain JVM: see [TestDatabases]. */
class PersonDaoTest {

    private lateinit var database: OrbitDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() {
        database = TestDatabases.inMemory()
        dao = database.personDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertedPeopleComeBackSortedByNameIgnoringCase() = runTest {
        dao.insert(Person(name = "sara", createdAt = Instant.EPOCH))
        dao.insert(Person(name = "Ali", createdAt = Instant.EPOCH))
        dao.insert(Person(name = "Babak", createdAt = Instant.EPOCH))

        val names = dao.observeAll().first().map(Person::name)

        assertEquals(listOf("Ali", "Babak", "sara"), names)
    }

    @Test
    fun observeReturnsThePersonWithTheirCreationTime() = runTest {
        val createdAt = Instant.parse("2026-10-28T09:15:30.123Z")
        val id = dao.insert(Person(name = "Sara", createdAt = createdAt))

        assertEquals(Person(id = id, name = "Sara", createdAt = createdAt), dao.observe(id).first())
    }

    @Test
    fun observeReturnsNullForAnUnknownId() = runTest {
        assertNull(dao.observe(42).first())
    }

    @Test
    fun persianNamesRoundTrip() = runTest {
        val id = dao.insert(Person(name = "سارا احمدی", createdAt = Instant.EPOCH))

        assertEquals("سارا احمدی", dao.observe(id).first()?.name)
    }

    @Test
    fun theBackedUpPreferencesRowIsSeededOnCreate() = runTest {
        assertEquals(1, database.backedUpPreferencesDao().observe().first().id)
    }
}
