package dev.alializadeh.orbit.testing

import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.GiftIdea
import dev.alializadeh.orbit.person.GiftIdeaDraft
import dev.alializadeh.orbit.person.GiftIdeaOrder
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateDraft
import dev.alializadeh.orbit.person.ImportantDateOrder
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.PersonDraft
import dev.alializadeh.orbit.person.PersonItemKind
import dev.alializadeh.orbit.person.PersonProfile
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.nameKey
import dev.alializadeh.orbit.person.requireOneBirthdayAtMost
import dev.alializadeh.orbit.person.names
import dev.alializadeh.orbit.person.withCadenceSetting
import dev.alializadeh.orbit.ring.Ring
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [PersonRepository] with the Room one's rules, over [rings]. */
class FakePersonRepository(
    private val rings: List<Ring> = TestRings.all,
    initial: List<PersonProfile> = emptyList(),
) : PersonRepository {

    val profiles = MutableStateFlow(initial.associateBy { it.person.id })
    val tags = MutableStateFlow<List<Tag>>(emptyList())

    /** Every draft passed to [save], as given. */
    val saved = mutableListOf<PersonDraft>()

    val people: List<Person> get() = profiles.value.values.map(PersonProfile::person)

    override fun observePeople(): Flow<List<PersonSummary>> = profiles.map { all ->
        all.values.filter { !it.person.isArchived }.map {
            PersonSummary(it.person.id, it.person.names, it.person.photoFileName, it.ring?.name)
        }
    }

    override fun observeProfile(id: Long): Flow<PersonProfile?> = profiles.map { it[id] }

    override suspend fun getProfile(id: Long): PersonProfile? = profiles.value[id]

    override fun observeTags(): Flow<List<Tag>> = tags

    override fun observeItemSuggestions(kind: PersonItemKind, excludePersonId: Long?): Flow<List<String>> =
        profiles.map { all ->
            all.values.filter { it.person.id != excludePersonId }.flatMap { it.items(kind) }.distinct().sorted()
        }

    override suspend fun findNameClash(names: List<String>, excludePersonId: Long?): String? {
        val taken = people.filter { it.id != excludePersonId }
            .flatMap { listOfNotNull(it.name, it.otherScriptName) }
            .mapTo(HashSet(), ::nameKey)
        return names.firstOrNull { it.isNotBlank() && nameKey(it) in taken }
    }

    override suspend fun save(draft: PersonDraft): Long {
        saved += draft
        val d = draft.normalised()
        requireOneBirthdayAtMost(d.importantDates.map(ImportantDateDraft::type))
        val existing = d.id?.let { profiles.value[it]?.person }
        val id = existing?.id ?: ((profiles.value.keys.maxOrNull() ?: 0) + 1)
        val person = (existing ?: Person(id = id, name = d.name, ringId = d.ringId!!, createdAt = Instant.EPOCH))
            .copy(
                name = d.name,
                nickname = d.nickname,
                otherScriptName = d.otherScriptName,
                photoFileName = d.photoFileName,
                howWeMet = d.howWeMet,
                notes = d.notes,
                ringId = d.ringId!!,
            )
            .withCadenceSetting(d.cadence)
        val personTags = d.tags.map { name ->
            tags.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: Tag(id = tags.value.size + 1L, name = name).also { tags.value += it }
        }
        profiles.value += id to PersonProfile(
            person = person,
            ring = rings.firstOrNull { it.id == person.ringId },
            tags = personTags,
            contactMethods = d.contactMethods.mapIndexed { position, method ->
                ContactMethod(
                    id = position + 1L,
                    personId = id,
                    type = method.type,
                    value = method.value,
                    label = method.label,
                    isPrimary = method.isPrimary,
                    position = position,
                )
            },
            canHelpWith = d.canHelpWith,
            likes = d.likes,
            dislikes = d.dislikes,
            importantDates = d.importantDates.mapIndexed { position, date ->
                ImportantDate(
                    id = date.id ?: nextDateId++,
                    personId = id,
                    type = date.type,
                    label = date.label,
                    date = date.date,
                    leadTimes = date.leadTimes,
                    position = position,
                )
            }.sortedWith(ImportantDateOrder),
            giftIdeas = profiles.value[id]?.giftIdeas.orEmpty(),
        )
        return id
    }

    private var nextDateId = 1000L
    private var nextGiftId = 1L

    private fun update(personId: Long, transform: (PersonProfile) -> PersonProfile) {
        val profile = profiles.value[personId] ?: return
        profiles.value += personId to transform(profile)
    }

    private fun ownerOf(predicate: (PersonProfile) -> Boolean): Long? = profiles.value.values.firstOrNull(predicate)?.person?.id

    override suspend fun saveImportantDate(personId: Long, draft: ImportantDateDraft): Long {
        val d = draft.normalised()
        val existing = profiles.value[personId]?.importantDates.orEmpty()
        requireOneBirthdayAtMost(existing.filter { it.id != d.id }.map(ImportantDate::type) + d.type)
        val current = existing.firstOrNull { it.id == d.id }
        val date = ImportantDate(
            id = current?.id ?: nextDateId++,
            personId = personId,
            type = d.type,
            label = d.label,
            date = d.date,
            leadTimes = d.leadTimes,
            position = current?.position ?: ((existing.maxOfOrNull(ImportantDate::position) ?: -1) + 1),
        )
        update(personId) { profile ->
            profile.copy(importantDates = (profile.importantDates.filter { it.id != date.id } + date).sortedWith(ImportantDateOrder))
        }
        return date.id
    }

    override suspend fun deleteImportantDate(id: Long) {
        val owner = ownerOf { profile -> profile.importantDates.any { it.id == id } } ?: return
        update(owner) { profile -> profile.copy(importantDates = profile.importantDates.filter { it.id != id }) }
    }

    override suspend fun saveGiftIdea(personId: Long, draft: GiftIdeaDraft): Long {
        val d = draft.normalised()
        require(d.text.isNotEmpty())
        val current = profiles.value[personId]?.giftIdeas?.firstOrNull { it.id == d.id }
        val idea = current?.copy(text = d.text, link = d.link, note = d.note)
            ?: GiftIdea(id = nextGiftId++, personId = personId, text = d.text, link = d.link, note = d.note, createdAt = Instant.EPOCH)
        update(personId) { profile ->
            profile.copy(giftIdeas = (profile.giftIdeas.filter { it.id != idea.id } + idea).sortedWith(GiftIdeaOrder))
        }
        return idea.id
    }

    override suspend fun setGiftIdeaGiven(id: Long, date: LocalDate?) {
        val owner = ownerOf { profile -> profile.giftIdeas.any { it.id == id } } ?: return
        update(owner) { profile ->
            profile.copy(giftIdeas = profile.giftIdeas.map { if (it.id == id) it.copy(givenOn = date) else it })
        }
    }

    override suspend fun deleteGiftIdea(id: Long) {
        val owner = ownerOf { profile -> profile.giftIdeas.any { it.id == id } } ?: return
        update(owner) { profile -> profile.copy(giftIdeas = profile.giftIdeas.filter { it.id != id }) }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        val profile = profiles.value[id] ?: return
        val person = profile.person.copy(archivedAt = if (archived) Instant.EPOCH else null)
        profiles.value += id to profile.copy(person = person)
    }

    override suspend fun delete(id: Long) {
        profiles.value -= id
    }
}

/** The seed Rings, as first run creates them in English. */
object TestRings {
    val inner = Ring(id = 1, name = "Inner", position = 0, cadence = dev.alializadeh.orbit.ring.Cadence.days(14))
    val close = Ring(id = 2, name = "Close", position = 1, cadence = dev.alializadeh.orbit.ring.Cadence.days(30))
    val friends = Ring(id = 3, name = "Friends", position = 2, cadence = dev.alializadeh.orbit.ring.Cadence.days(90))
    val acquaintances = Ring(id = 4, name = "Acquaintances", position = 3, cadence = null)
    val all = listOf(inner, close, friends, acquaintances)
}

/** A profile with only what's given, in [ring]. */
fun profileOf(
    id: Long,
    name: String,
    ring: Ring = TestRings.friends,
    otherScriptName: String? = null,
    nickname: String? = null,
) = PersonProfile(
    person = Person(
        id = id,
        name = name,
        otherScriptName = otherScriptName,
        nickname = nickname,
        ringId = ring.id,
        createdAt = Instant.EPOCH,
    ),
    ring = ring,
    tags = emptyList(),
    contactMethods = emptyList(),
    canHelpWith = emptyList(),
    likes = emptyList(),
    dislikes = emptyList(),
)
