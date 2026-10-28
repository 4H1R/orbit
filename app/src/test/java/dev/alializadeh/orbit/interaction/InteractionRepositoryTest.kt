package dev.alializadeh.orbit.interaction

import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.person.ContactMethodDraft
import dev.alializadeh.orbit.person.GiftIdeaDraft
import dev.alializadeh.orbit.person.PersonDraft
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.person.RoomPersonRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Interactions, Snooze and Due's inputs through the Room repositories, on the plain JVM. */
class InteractionRepositoryTest {

    private val now = Instant.parse("2026-10-28T09:15:30Z")
    private val today = LocalDate.of(2026, 10, 28)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var database: OrbitDatabase
    private lateinit var people: RoomPersonRepository
    private lateinit var interactions: RoomInteractionRepository
    private var ringId = 0L

    @Before
    fun setUp() = runTest {
        database = TestDatabases.inMemory()
        people = RoomPersonRepository(
            database = database,
            personDao = database.personDao(),
            tagDao = database.tagDao(),
            ringDao = database.ringDao(),
            photoStore = FakePhotoStore(),
            clock = clock,
        )
        interactions = RoomInteractionRepository(database, database.interactionDao(), clock)
        // Every 30 days.
        ringId = database.insertTestRing()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun person(name: String) = people.save(PersonDraft(name = name, ringId = ringId))

    private suspend fun log(vararg personIds: Long, date: LocalDate = today, type: InteractionType = InteractionType.Call, note: String? = null) =
        interactions.save(InteractionDraft(type = type, date = date, note = note, personIds = personIds.toList()))

    @Test
    fun anInteractionWithTwoPeopleShowsForBoth() = runTest {
        val sara = person("Sara")
        val reza = person("Reza")
        val id = log(sara, reza, type = InteractionType.InPerson, note = "  Dinner  ")

        for (who in listOf(sara, reza)) {
            val list = interactions.observeForPerson(who).first()
            assertEquals(listOf(id), list.map { it.interaction.id })
            assertEquals(setOf("Sara", "Reza"), list.single().people.map { it.name }.toSet())
            assertEquals("Dinner", list.single().interaction.note)
            assertEquals(InteractionType.InPerson, list.single().interaction.type)
        }
    }

    @Test
    fun theyAreListedNewestFirst() = runTest {
        val sara = person("Sara")
        val old = log(sara, date = today.minusDays(10))
        val newest = log(sara, date = today)
        val sameDayLater = log(sara, date = today)
        val middle = log(sara, date = today.minusDays(3))

        assertEquals(listOf(sameDayLater, newest, middle, old), interactions.observeForPerson(sara).first().map { it.interaction.id })
    }

    @Test
    fun editingASharedInteractionAppliesForEveryoneInIt() = runTest {
        val sara = person("Sara")
        val reza = person("Reza")
        val mina = person("Mina")
        val id = log(sara, reza)

        interactions.save(
            InteractionDraft(id = id, type = InteractionType.Message, date = today.minusDays(1), note = "Edited", personIds = listOf(sara, mina)),
        )

        val forSara = interactions.observeForPerson(sara).first().single()
        assertEquals(InteractionType.Message, forSara.interaction.type)
        assertEquals(today.minusDays(1), forSara.interaction.date)
        assertEquals("Edited", forSara.interaction.note)
        assertEquals(forSara, interactions.observeForPerson(mina).first().single())
        assertTrue("Reza was taken out of it", interactions.observeForPerson(reza).first().isEmpty())
        assertEquals(1, database.interactionDao().count())
    }

    @Test
    fun deletingAnInteractionTakesItAwayForEveryone() = runTest {
        val sara = person("Sara")
        val reza = person("Reza")
        val id = log(sara, reza)

        interactions.delete(id)

        assertTrue(interactions.observeForPerson(sara).first().isEmpty())
        assertTrue(interactions.observeForPerson(reza).first().isEmpty())
        assertNull(interactions.observeInteraction(id).first())
    }

    @Test
    fun aSharedInteractionSurvivesOnePersonsDeletionAndOrphansGo() = runTest {
        val sara = person("Sara")
        val reza = person("Reza")
        val shared = log(sara, reza, note = "Both")
        log(sara, note = "Only Sara")

        people.delete(sara)

        val forReza = interactions.observeForPerson(reza).first()
        assertEquals(listOf(shared), forReza.map { it.interaction.id })
        assertEquals(listOf("Reza"), forReza.single().people.map { it.name })
        assertEquals("the one only Sara was in is gone", 1, database.interactionDao().count())
    }

    @Test
    fun anInteractionNeedsSomeoneAndCantBeInTheFuture() = runTest {
        val sara = person("Sara")
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { interactions.save(InteractionDraft(type = InteractionType.Call, date = today, personIds = emptyList())) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { log(sara, date = today.plusDays(1)) }
        }
        assertEquals(0, database.interactionDao().count())
    }

    @Test
    fun markAsContactedLogsOtherTodayWithNoNoteForThatPersonOnly() = runTest {
        val sara = person("Sara")
        person("Reza")

        val id = interactions.markAsContacted(sara)

        val logged = interactions.observeInteraction(id).first()!!
        assertEquals(InteractionType.Other, logged.interaction.type)
        assertEquals(today, logged.interaction.date)
        assertNull(logged.interaction.note)
        assertEquals(listOf(sara), logged.people.map { it.id })
    }

    @Test
    fun theLatestInteractionOfAnyTypeFeedsDue() = runTest {
        val sara = person("Sara")
        val reza = person("Reza")
        log(sara, date = today.minusDays(40), type = InteractionType.Call)
        log(sara, date = today.minusDays(31), type = InteractionType.Other)
        log(reza, date = today.minusDays(5), type = InteractionType.Message)

        val summaries = people.observeEveryone().first().associateBy { it.id }
        assertEquals(today.minusDays(31), summaries.getValue(sara).keepInTouch.lastInteraction)
        assertTrue(summaries.getValue(sara).keepInTouch.on(today).isDue)
        assertFalse(summaries.getValue(reza).keepInTouch.on(today).isDue)

        // Upcoming reads the same facts.
        val upcoming = RoomUpcomingRepository(database.personDao(), database.ringDao(), database.interactionDao(), clock)
            .observePeople().first().associateBy { it.id }
        assertEquals(today.minusDays(31), upcoming.getValue(sara).keepInTouch.lastInteraction)
        assertEquals("Friends", upcoming.getValue(sara).ringName)
    }

    @Test
    fun withoutAnInteractionDueCountsFromTheDayTheyWereAdded() = runTest {
        val sara = person("Sara")
        val facts = people.observeEveryone().first().single { it.id == sara }.keepInTouch
        assertEquals(today, facts.addedOn)
        assertNull(facts.lastInteraction)
        assertEquals(today.plusDays(30), facts.dueOn)
    }

    @Test
    fun snoozeIsStoredAndReturnsWhatItWasForUndo() = runTest {
        val sara = person("Sara")
        assertNull(people.setSnoozedUntil(sara, today.plusDays(7)))
        assertEquals(today.plusDays(7), people.getProfile(sara)!!.person.snoozedUntil)
        assertEquals(today.plusDays(7), people.setSnoozedUntil(sara, null))
        assertNull(people.getProfile(sara)!!.person.snoozedUntil)
    }

    @Test
    fun editingAPersonKeepsTheirSnooze() = runTest {
        val sara = person("Sara")
        people.setSnoozedUntil(sara, today.plusDays(3))
        people.save(PersonDraft(id = sara, name = "Sara Ahmadi", ringId = ringId))
        assertEquals(today.plusDays(3), people.getProfile(sara)!!.person.snoozedUntil)
    }

    @Test
    fun everyoneCarriesWhatSearchAndTheFiltersNeed() = runTest {
        val reza = person("Reza")
        val sara = people.save(
            PersonDraft(
                name = "Sara",
                otherScriptName = "سارا",
                nickname = "Sari",
                ringId = ringId,
                tags = listOf("Gym"),
                canHelpWith = listOf("Photography"),
                likes = listOf("Jazz"),
                dislikes = listOf("Olives"),
                howWeMet = "University",
                notes = "Allergic to cats",
                contactMethods = listOf(ContactMethodDraft(ContactType.Phone, "+98 912 345 6789")),
            ),
        )
        people.saveGiftIdea(sara, GiftIdeaDraft(text = "Record player"))
        log(sara, reza, date = today.minusDays(9), note = "Dinner in town")
        log(sara, date = today.minusDays(2), note = "Moving to Berlin")
        log(sara, date = today.minusDays(1))
        people.setArchived(reza, archived = true)

        val everyone = people.observeEveryone().first().associateBy { it.id }
        val summary = everyone.getValue(sara)
        val document = summary.search
        assertEquals(listOf("Gym"), document.tags)
        assertEquals(listOf("Photography"), document.canHelpWith)
        assertEquals(listOf("Jazz"), document.likes)
        assertEquals(listOf("Olives"), document.dislikes)
        assertEquals(listOf("Record player"), document.giftIdeas)
        assertEquals("University", document.howWeMet)
        assertEquals("Allergic to cats", document.notes)
        assertEquals(listOf("+98 912 345 6789"), document.contactMethods.map { it.value })
        assertEquals(listOf("Moving to Berlin", "Dinner in town"), document.interactionNotes.map { it.note })
        assertEquals(today.minusDays(2), document.interactionNotes.first().date)
        assertEquals(ringId, summary.ringId)
        assertEquals(1, summary.tagIds.size)
        assertEquals(now, summary.addedAt)
        assertFalse(summary.isArchived)
        // Archived People are there too, flagged; shared notes are theirs as well.
        assertTrue(everyone.getValue(reza).isArchived)
        assertEquals(listOf("Dinner in town"), everyone.getValue(reza).search.interactionNotes.map { it.note })
    }
}
