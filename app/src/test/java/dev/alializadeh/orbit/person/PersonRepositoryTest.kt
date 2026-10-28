package dev.alializadeh.orbit.person

import androidx.room3.useWriterConnection
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.testing.FakePhotoStore
import java.time.Clock
import java.time.Instant
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

/** The Room repository and DAOs on the plain JVM (see TestDatabases). */
class PersonRepositoryTest {

    private val now = Instant.parse("2026-10-28T09:15:30Z")
    private lateinit var database: OrbitDatabase
    private lateinit var photos: FakePhotoStore
    private lateinit var repository: RoomPersonRepository
    private var ringId = 0L

    @Before
    fun setUp() = runTest {
        database = TestDatabases.inMemory()
        photos = FakePhotoStore()
        repository = RoomPersonRepository(
            database = database,
            personDao = database.personDao(),
            tagDao = database.tagDao(),
            ringDao = database.ringDao(),
            photoStore = photos,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        ringId = database.insertTestRing()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun draft(name: String = "Sara Ahmadi") = PersonDraft(name = name, ringId = ringId)

    @Test
    fun aSavedPersonComesBackWithEverythingInOrder() = runTest {
        val id = repository.save(
            draft().copy(
                nickname = " Sari ",
                otherScriptName = "سارا احمدی",
                howWeMet = "University",
                notes = "Allergic to cats",
                cadence = CadenceSetting.Own(Cadence.weeks(2)),
                tags = listOf("University", "Book club"),
                contactMethods = listOf(
                    ContactMethodDraft(ContactType.Phone, "+49 151 2345 6789", "mobile", isPrimary = true),
                    ContactMethodDraft(ContactType.Email, "sara@example.com"),
                ),
                canHelpWith = listOf("Photography", "German"),
                likes = listOf("Jazz", "Hiking", "Persian food"),
                dislikes = listOf("Cilantro"),
            ),
        )

        val profile = repository.getProfile(id)!!
        assertEquals("Sara Ahmadi", profile.person.name)
        assertEquals("Sari", profile.person.nickname)
        assertEquals("سارا احمدی", profile.person.otherScriptName)
        assertEquals(now, profile.person.createdAt)
        assertEquals(CadenceSetting.Own(Cadence.weeks(2)), profile.person.cadenceSetting)
        assertEquals(listOf("University", "Book club"), profile.tags.map(Tag::name))
        assertEquals(listOf("+49 151 2345 6789", "sara@example.com"), profile.contactMethods.map(ContactMethod::value))
        assertEquals(listOf("mobile", null), profile.contactMethods.map(ContactMethod::label))
        assertEquals(listOf("Photography", "German"), profile.canHelpWith)
        assertEquals(listOf("Jazz", "Hiking", "Persian food"), profile.likes)
        assertEquals(listOf("Cilantro"), profile.dislikes)
        assertEquals("Friends", profile.ring?.name)
        assertEquals(profile, repository.observeProfile(id).first())
    }

    @Test
    fun cadenceSettingsRoundTripIncludingNoCadence() = runTest {
        val fromRing = repository.save(draft("A"))
        val none = repository.save(draft("B").copy(cadence = CadenceSetting.Own(null)))
        val own = repository.save(draft("C").copy(cadence = CadenceSetting.Own(Cadence.months(1))))

        assertEquals(CadenceSetting.FromRing, repository.getProfile(fromRing)!!.person.cadenceSetting)
        assertEquals(CadenceSetting.Own(null), repository.getProfile(none)!!.person.cadenceSetting)
        assertEquals(CadenceSetting.Own(Cadence.months(1)), repository.getProfile(own)!!.person.cadenceSetting)
        assertEquals(Cadence.days(30), repository.getProfile(fromRing)!!.let { it.person.cadenceIn(it.ring) })
        assertNull(repository.getProfile(none)!!.let { it.person.cadenceIn(it.ring) })
    }

    @Test
    fun atMostOneContactMethodIsPrimary() = runTest {
        val twoStars = repository.save(
            draft("A").copy(
                contactMethods = listOf(
                    ContactMethodDraft(ContactType.Email, "a@example.com", isPrimary = false),
                    ContactMethodDraft(ContactType.Phone, "123456", isPrimary = true),
                    ContactMethodDraft(ContactType.WhatsApp, "123456", isPrimary = true),
                ),
            ),
        )
        val noStar = repository.save(
            draft("B").copy(
                contactMethods = listOf(
                    ContactMethodDraft(ContactType.Email, "b@example.com"),
                    ContactMethodDraft(ContactType.Phone, "123456"),
                ),
            ),
        )

        assertEquals(listOf(false, true, false), repository.getProfile(twoStars)!!.contactMethods.map { it.isPrimary })
        assertEquals(listOf(true, false), repository.getProfile(noStar)!!.contactMethods.map { it.isPrimary })
    }

    @Test
    fun aBlankContactMethodIsDroppedAndItsStarPassesOn() = runTest {
        val id = repository.save(
            draft().copy(
                contactMethods = listOf(
                    ContactMethodDraft(ContactType.Phone, "   ", isPrimary = true),
                    ContactMethodDraft(ContactType.Email, "sara@example.com"),
                ),
            ),
        )

        val methods = repository.getProfile(id)!!.contactMethods
        assertEquals(listOf("sara@example.com"), methods.map(ContactMethod::value))
        assertTrue(methods.single().isPrimary)
    }

    @Test
    fun persianDigitsInPhoneNumbersAreSavedAsAscii() = runTest {
        val id = repository.save(
            draft().copy(
                contactMethods = listOf(
                    ContactMethodDraft(ContactType.Phone, "۰۹۱۲ ۳۴۵ ۶۷۸۹"),
                    ContactMethodDraft(ContactType.WhatsApp, "+٩٨ ٩١٢"),
                    ContactMethodDraft(ContactType.Address, "تهران، پلاک ۱۲"),
                ),
            ),
        )

        assertEquals(
            listOf("0912 345 6789", "+98 912", "تهران، پلاک ۱۲"),
            repository.getProfile(id)!!.contactMethods.map(ContactMethod::value),
        )
    }

    @Test
    fun tagsAreSharedIgnoringCase() = runTest {
        val sara = repository.save(draft("Sara").copy(tags = listOf("Gym", "Work")))
        val reza = repository.save(draft("Reza").copy(tags = listOf("gym")))

        assertEquals(listOf("Gym", "Work"), repository.observeTags().first().map(Tag::name))
        val saraGym = repository.getProfile(sara)!!.tags.first()
        assertEquals(saraGym, repository.getProfile(reza)!!.tags.single())
    }

    @Test
    fun editingReplacesTheListsInsteadOfAddingToThem() = runTest {
        val id = repository.save(
            draft().copy(
                likes = listOf("Jazz", "Tea"),
                tags = listOf("Gym"),
                contactMethods = listOf(ContactMethodDraft(ContactType.Phone, "1234567")),
            ),
        )
        val edited = PersonDraft.of(repository.getProfile(id)!!).copy(
            likes = listOf("Tea", "Coffee"),
            tags = listOf("Work", "Gym"),
            contactMethods = listOf(
                ContactMethodDraft(ContactType.Email, "sara@example.com"),
                ContactMethodDraft(ContactType.Phone, "1234567", isPrimary = true),
            ),
        )

        assertEquals(id, repository.save(edited))

        val profile = repository.getProfile(id)!!
        assertEquals(listOf("Tea", "Coffee"), profile.likes)
        assertEquals(listOf("Work", "Gym"), profile.tags.map(Tag::name))
        assertEquals(listOf(false, true), profile.contactMethods.map(ContactMethod::isPrimary))
        assertEquals(now, profile.person.createdAt)
    }

    @Test
    fun deletingAPersonCascadesToWhatTheyOwnButKeepsSharedTags() = runTest {
        val sara = repository.save(
            draft("Sara").copy(
                photoFileName = "sara.jpg",
                likes = listOf("Jazz"),
                tags = listOf("Gym"),
                contactMethods = listOf(ContactMethodDraft(ContactType.Phone, "1234567")),
            ),
        )
        photos.files += "sara.jpg"
        val reza = repository.save(draft("Reza").copy(likes = listOf("Chess"), tags = listOf("Gym")))

        repository.delete(sara)

        assertNull(repository.getProfile(sara))
        assertEquals(1L, count("person_item"))
        assertEquals(0L, count("contact_method"))
        assertEquals(1L, count("person_tag"))
        assertEquals(listOf("Gym"), repository.getProfile(reza)!!.tags.map(Tag::name))
        assertFalse("the photo goes with the Person", "sara.jpg" in photos.files)
    }

    @Test
    fun replacingOrRemovingThePhotoDeletesTheOldFile() = runTest {
        photos.files += listOf("old.jpg", "new.jpg")
        val id = repository.save(draft().copy(photoFileName = "old.jpg"))

        repository.save(PersonDraft.of(repository.getProfile(id)!!).copy(photoFileName = "new.jpg"))
        assertEquals(setOf("new.jpg"), photos.files)

        repository.save(PersonDraft.of(repository.getProfile(id)!!).copy(photoFileName = null))
        assertTrue(photos.files.isEmpty())
    }

    @Test
    fun archivedPeopleLeaveTheListUntilUnarchived() = runTest {
        val sara = repository.save(draft("Sara"))
        repository.save(draft("Reza"))

        repository.setArchived(sara, archived = true)
        assertEquals(listOf("Reza"), repository.observeEveryone().first().filterNot { it.isArchived }.map { it.names.name })
        assertEquals(now, repository.getProfile(sara)!!.person.archivedAt)

        repository.setArchived(sara, archived = false)
        assertEquals(setOf("Sara", "Reza"), repository.observeEveryone().first().filterNot { it.isArchived }.map { it.names.name }.toSet())
    }

    @Test
    fun theListCarriesEachPersonsRingName() = runTest {
        repository.save(draft("Sara"))

        assertEquals("Friends", repository.observeEveryone().first().single().ringName)
    }

    @Test
    fun nameClashesIgnoreCaseSpacingAndTheSamePerson() = runTest {
        val sara = repository.save(draft("Sara  Ahmadi").copy(otherScriptName = "سارا احمدي"))
        repository.setArchived(sara, archived = true)

        assertEquals("sara ahmadi", repository.findNameClash(listOf("sara ahmadi"), excludePersonId = null))
        // Arabic ي folds into Persian ی.
        assertEquals("سارا احمدی", repository.findNameClash(listOf("Mina", "سارا احمدی"), excludePersonId = null))
        assertNull(repository.findNameClash(listOf("Sara Ahmadi"), excludePersonId = sara))
        assertNull(repository.findNameClash(listOf("Sara"), excludePersonId = null))
    }

    @Test
    fun itemSuggestionsComeFromOtherPeopleOnly() = runTest {
        val sara = repository.save(draft("Sara").copy(likes = listOf("Jazz", "Tea")))
        repository.save(draft("Reza").copy(likes = listOf("jazz piano", "Tea"), dislikes = listOf("Rain")))

        assertEquals(listOf("jazz piano", "Tea"), repository.observeItemSuggestions(PersonItemKind.Like, sara).first())
        assertEquals(
            listOf("Jazz", "jazz piano", "Tea"),
            repository.observeItemSuggestions(PersonItemKind.Like, excludePersonId = null).first(),
        )
    }

    @Test
    fun aRingHoldingPeopleCantBeRemoved() = runTest {
        repository.save(draft())

        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking {
                database.useWriterConnection { connection ->
                    connection.usePrepared("DELETE FROM ring WHERE id = ?") {
                        it.bindLong(1, ringId)
                        it.step()
                    }
                }
            }
        }
    }

    @Test
    fun itemsAreCutToOneLine() = runTest {
        val id = repository.save(draft().copy(likes = listOf("x".repeat(80))))

        assertEquals(PersonItem.MAX_LENGTH, repository.getProfile(id)!!.likes.single().length)
    }

    private suspend fun count(table: String): Long = database.useWriterConnection { connection ->
        connection.usePrepared("SELECT COUNT(*) FROM $table") {
            it.step()
            it.getLong(0)
        }
    }
}
