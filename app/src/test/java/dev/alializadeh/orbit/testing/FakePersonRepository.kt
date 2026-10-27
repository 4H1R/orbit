package dev.alializadeh.orbit.testing

import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.PersonDraft
import dev.alializadeh.orbit.person.PersonItemKind
import dev.alializadeh.orbit.person.PersonProfile
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.nameKey
import dev.alializadeh.orbit.person.names
import dev.alializadeh.orbit.person.withCadenceSetting
import dev.alializadeh.orbit.ring.Ring
import java.time.Instant
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
        )
        return id
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
