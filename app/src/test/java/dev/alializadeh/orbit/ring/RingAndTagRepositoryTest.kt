package dev.alializadeh.orbit.ring

import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.person.PersonDraft
import dev.alializadeh.orbit.person.RoomPersonRepository
import dev.alializadeh.orbit.person.RoomTagRepository
import dev.alializadeh.orbit.testing.FakePhotoStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Settings → Rings and Tags through the Room repositories, on the plain JVM. */
class RingAndTagRepositoryTest {

    private lateinit var database: OrbitDatabase
    private lateinit var rings: RoomRingRepository
    private lateinit var tags: RoomTagRepository
    private lateinit var people: RoomPersonRepository

    @Before
    fun setUp() {
        database = TestDatabases.inMemory()
        rings = RoomRingRepository(database, database.ringDao())
        tags = RoomTagRepository(database, database.tagDao())
        people = RoomPersonRepository(
            database = database,
            personDao = database.personDao(),
            tagDao = database.tagDao(),
            ringDao = database.ringDao(),
            photoStore = FakePhotoStore(),
            clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun names() = rings.observeRings().first().map(Ring::name)

    // Rings

    @Test
    fun ringsAreAddedOutermostUpToSix() = runTest {
        val first = rings.add(" Inner ", Cadence.days(14))
        rings.add("Close", null)
        assertEquals(listOf("Inner", "Close"), names())
        assertEquals(Cadence.days(14), database.ringDao().get(first)!!.cadence)

        repeat(4) { rings.add("Ring $it", Cadence.weeks(2)) }
        assertEquals(Ring.MAX_COUNT, names().size)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { rings.add("Seventh", null) } }
        assertEquals((0 until Ring.MAX_COUNT).toList(), rings.observeRings().first().map(Ring::position))
    }

    @Test
    fun aRingIsRenamedAndGetsANewDefaultCadence() = runTest {
        val id = rings.add("Friends", Cadence.days(90))
        rings.update(id, "Good friends", Cadence.months(2))
        assertEquals(Ring(id, "Good friends", 0, Cadence.months(2)), database.ringDao().get(id))
        rings.update(id, "Good friends", null)
        assertNull(database.ringDao().get(id)!!.cadence)
    }

    @Test
    fun aRingWithPeopleCantBeRemovedUntilTheyAreMoved() = runTest {
        val inner = rings.add("Inner", Cadence.days(14))
        val outer = rings.add("Outer", null)
        val sara = people.save(PersonDraft(name = "Sara", ringId = inner))
        val reza = people.save(PersonDraft(name = "Reza", ringId = inner))
        people.setArchived(reza, archived = true)

        assertEquals(listOf(2, 0), rings.observeRingsWithCounts().first().map { it.peopleCount })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { rings.remove(inner) } }

        rings.movePeople(inner, outer)
        assertEquals("Archived People move too", listOf(0, 2), rings.observeRingsWithCounts().first().map { it.peopleCount })
        assertEquals(outer, people.getProfile(sara)!!.person.ringId)

        rings.remove(inner)
        assertEquals(listOf(Ring(outer, "Outer", 0, null)), rings.observeRings().first())
    }

    @Test
    fun theLastRingCantBeRemoved() = runTest {
        val only = rings.add("Everyone", null)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { rings.remove(only) } }
        assertEquals(listOf("Everyone"), names())
    }

    @Test
    fun reorderStoresPositionsAndRemovalCloseTheGap() = runTest {
        val a = rings.add("A", null)
        val b = rings.add("B", null)
        val c = rings.add("C", null)

        rings.reorder(listOf(c, a, b))
        assertEquals(listOf("C", "A", "B"), names())
        assertEquals(listOf(0, 1, 2), rings.observeRings().first().map(Ring::position))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { rings.reorder(listOf(c, a)) } }

        rings.remove(a)
        assertEquals(listOf("C" to 0, "B" to 1), rings.observeRings().first().map { it.name to it.position })
    }

    // Tags

    @Test
    fun tagsCountTheirPeopleAndRename() = runTest {
        val ring = rings.add("Friends", null)
        people.save(PersonDraft(name = "Sara", ringId = ring, tags = listOf("Gym", "Work")))
        people.save(PersonDraft(name = "Reza", ringId = ring, tags = listOf("Gym")))

        assertEquals(listOf("Gym" to 2, "Work" to 1), tags.observeTags().first().map { it.tag.name to it.peopleCount })

        val gym = tags.find("gym")!!
        tags.rename(gym.id, " Climbing ")
        tags.rename(gym.id, "climbing") // only the case: still a rename
        assertEquals(listOf("climbing", "Work"), tags.observeTags().first().map { it.tag.name })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { tags.rename(gym.id, "WORK") } }
    }

    @Test
    fun mergingMovesEveryoneOntoTheOtherTagOnce() = runTest {
        val ring = rings.add("Friends", null)
        val sara = people.save(PersonDraft(name = "Sara", ringId = ring, tags = listOf("Gym", "Fitness")))
        val reza = people.save(PersonDraft(name = "Reza", ringId = ring, tags = listOf("Work", "Gym")))
        val gym = tags.find("Gym")!!
        val fitness = tags.find("Fitness")!!

        tags.merge(gym.id, fitness.id)

        assertNull(tags.find("Gym"))
        assertEquals(listOf("Fitness" to 2, "Work" to 1), tags.observeTags().first().map { it.tag.name to it.peopleCount })
        assertEquals(listOf("Fitness"), people.getProfile(sara)!!.tags.map { it.name })
        assertEquals(listOf("Work", "Fitness"), people.getProfile(reza)!!.tags.map { it.name })
    }

    @Test
    fun deletingATagTakesItOffEveryone() = runTest {
        val ring = rings.add("Friends", null)
        val sara = people.save(PersonDraft(name = "Sara", ringId = ring, tags = listOf("Gym", "Work")))
        tags.delete(tags.find("Gym")!!.id)
        assertEquals(listOf("Work"), people.getProfile(sara)!!.tags.map { it.name })
        assertEquals(listOf("Work"), tags.observeTags().first().map { it.tag.name })
    }
}
