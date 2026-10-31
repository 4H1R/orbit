package dev.alializadeh.orbit.person

import androidx.room3.withWriteTransaction
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.due.keepInTouch
import dev.alializadeh.orbit.interaction.PersonInteractionNote
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingDao
import dev.alializadeh.orbit.search.InteractionNote
import dev.alializadeh.orbit.search.SearchContact
import dev.alializadeh.orbit.search.SearchDocument
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * A People-list row: who, their photo and Ring, how to reach them, their Important dates (for the
 * date badge), what Due is worked out from, and what search and the filters look at.
 */
data class PersonSummary(
    val id: Long,
    val names: PersonNames,
    val photoFileName: String?,
    val ringName: String?,
    val keepInTouch: KeepInTouch,
    /** The Ring's place, innermost 0. */
    val ringPosition: Int = 0,
    val primaryContactMethod: ContactMethod? = null,
    val importantDates: List<ImportantDate> = emptyList(),
    val ringId: Long = 0,
    val tagIds: Set<Long> = emptySet(),
    /** When they were added (Recently added; Last Interaction for People without one). */
    val addedAt: Instant = Instant.EPOCH,
    /** Everything they can be found by. */
    val search: SearchDocument = SearchDocument(id, names),
) {
    val isArchived: Boolean get() = keepInTouch.isArchived
}

/**
 * [this] Person's summary, with their latest Interaction on [lastInteraction] and the notes of the
 * Interactions they were part of ([interactionNotes], newest first).
 */
fun PersonProfile.summary(lastInteraction: LocalDate?, interactionNotes: List<InteractionNote>, zone: ZoneId) = PersonSummary(
    id = person.id,
    names = names,
    photoFileName = person.photoFileName,
    ringName = ring?.name,
    keepInTouch = person.keepInTouch(ring, lastInteraction, zone),
    ringPosition = ring?.position ?: Int.MAX_VALUE,
    primaryContactMethod = primaryContactMethod,
    importantDates = importantDates,
    ringId = person.ringId,
    tagIds = tags.mapTo(HashSet(), Tag::id),
    addedAt = person.createdAt,
    search = SearchDocument(
        personId = person.id,
        names = names,
        tags = tags.map(Tag::name),
        canHelpWith = canHelpWith,
        likes = likes,
        dislikes = dislikes,
        giftIdeas = giftIdeas.map(GiftIdea::text),
        followUps = followUps.map(FollowUp::text),
        loans = loans.map(Loan::text),
        howWeMet = person.howWeMet,
        contactMethods = contactMethods.map { SearchContact(it.type, it.value) },
        notes = person.notes,
        interactionNotes = interactionNotes,
    ),
)

/** Reads and writes People and what's stored around them. Screens observe it through Flows. */
interface PersonRepository {

    /**
     * Every Person, Archived ones included ([PersonSummary.isArchived]), unsorted: the People list
     * sorts, filters and searches them.
     */
    fun observeEveryone(): Flow<List<PersonSummary>>

    /** Every Person, Archived ones included (the Interaction People picker filters them). */
    fun observeAllPeople(): Flow<List<Person>>

    /** One Person with everything around them, or null once they no longer exist. */
    fun observeProfile(id: Long): Flow<PersonProfile?>

    suspend fun getProfile(id: Long): PersonProfile?

    /** Every Tag, by name. */
    fun observeTags(): Flow<List<Tag>>

    /** Wording of [kind] items already used on other People than [excludePersonId]. */
    fun observeItemSuggestions(kind: PersonItemKind, excludePersonId: Long?): Flow<List<String>>

    /**
     * The first of [names] that another Person (Archived ones included, never [excludePersonId])
     * already has as a Name or Name in other script, ignoring case and spacing; null when none.
     */
    suspend fun findNameClash(names: List<String>, excludePersonId: Long?): String?

    /**
     * Stores the [draft] (normalised, see [PersonDraft.normalised]) in one transaction and returns
     * the Person's id. New Tags are created by name. A replaced or removed photo's file is deleted.
     */
    suspend fun save(draft: PersonDraft): Long

    suspend fun setArchived(id: Long, archived: Boolean)

    /**
     * Snoozes the Person until [until] (null cancels a Snooze) and returns what it was before, for
     * Undo.
     */
    suspend fun setSnoozedUntil(id: Long, until: LocalDate?): LocalDate?

    /**
     * Adds (no [ImportantDateDraft.id]) or updates one Important date of [personId] (Person
     * detail's date sheet) and returns its id. Throws if it would give the Person a second Birthday.
     */
    suspend fun saveImportantDate(personId: Long, draft: ImportantDateDraft): Long

    suspend fun deleteImportantDate(id: Long)

    /** Adds (no [GiftIdeaDraft.id]) or edits a Gift idea of [personId] and returns its id. */
    suspend fun saveGiftIdea(personId: Long, draft: GiftIdeaDraft): Long

    /** Marks the idea Given on [date], or back to an open Idea when [date] is null. */
    suspend fun setGiftIdeaGiven(id: Long, date: LocalDate?)

    suspend fun deleteGiftIdea(id: Long)

    /** Adds (no [FollowUpDraft.id]) or edits a Follow-up of [personId] and returns its id. */
    suspend fun saveFollowUp(personId: Long, draft: FollowUpDraft): Long

    /** Marks the Follow-up Done on [date], or open again when [date] is null. */
    suspend fun setFollowUpDone(id: Long, date: LocalDate?)

    suspend fun deleteFollowUp(id: Long)

    /** Adds (no [LoanDraft.id]) or edits a Loan of [personId] and returns its id. */
    suspend fun saveLoan(personId: Long, draft: LoanDraft): Long

    /** Marks the Loan Returned on [date], or open again when [date] is null. */
    suspend fun setLoanReturned(id: Long, date: LocalDate?)

    suspend fun deleteLoan(id: Long)

    /**
     * Deletes the Person, everything they own, and their photo. Permanent. They leave shared
     * Interactions, which stay for the others; one left with nobody is deleted.
     */
    suspend fun delete(id: Long)
}

class RoomPersonRepository @Inject constructor(
    private val database: OrbitDatabase,
    private val personDao: PersonDao,
    private val tagDao: TagDao,
    private val ringDao: RingDao,
    private val photoStore: PhotoStore,
    private val clock: Clock,
) : PersonRepository {

    private val interactionDao = database.interactionDao()

    override fun observeEveryone(): Flow<List<PersonSummary>> =
        combine(
            personDao.observeAllWithDetails(),
            ringDao.observeAll(),
            interactionDao.observeLastDates(),
            interactionDao.observeNotes(),
        ) { people, rings, last, notes ->
            val ringsById = rings.associateBy(Ring::id)
            val lastDates = last.associate { it.personId to it.date }
            val notesByPerson = notes.groupBy(
                keySelector = PersonInteractionNote::personId,
                valueTransform = { InteractionNote(it.interactionId, it.date, it.note) },
            )
            people.map { details ->
                val id = details.person.id
                PersonProfile.of(details, ringsById[details.person.ringId])
                    .summary(lastDates[id], notesByPerson[id].orEmpty(), clock.zone)
            }
        }

    override fun observeAllPeople(): Flow<List<Person>> = personDao.observeAll()

    override fun observeProfile(id: Long): Flow<PersonProfile?> =
        combine(personDao.observeWithDetails(id), ringDao.observeAll()) { details, rings ->
            details?.let { PersonProfile.of(it, rings.firstOrNull { ring -> ring.id == it.person.ringId }) }
        }

    override suspend fun getProfile(id: Long): PersonProfile? {
        val details = personDao.getWithDetails(id) ?: return null
        return PersonProfile.of(details, ringDao.get(details.person.ringId))
    }

    override fun observeTags(): Flow<List<Tag>> = tagDao.observeAll()

    override fun observeItemSuggestions(kind: PersonItemKind, excludePersonId: Long?): Flow<List<String>> =
        personDao.observeItemTexts(kind, excludePersonId ?: NO_PERSON)

    override suspend fun findNameClash(names: List<String>, excludePersonId: Long?): String? {
        val taken = personDao.getAll()
            .filter { it.id != excludePersonId }
            .flatMap { listOfNotNull(it.name, it.otherScriptName) }
            .mapTo(HashSet(), ::nameKey)
        return names.map(String::trim).filter(String::isNotEmpty).firstOrNull { nameKey(it) in taken }
    }

    override suspend fun save(draft: PersonDraft): Long {
        val d = draft.normalised()
        require(d.name.isNotEmpty()) { "A Person needs a Name" }
        val ringId = requireNotNull(d.ringId) { "A Person needs a Ring" }
        requireOneBirthdayAtMost(d.importantDates.map(ImportantDateDraft::type))
        var replacedPhoto: String? = null
        val id = database.withWriteTransaction {
            val existing = d.id?.let { personDao.get(it) }
            val person = (existing ?: Person(name = d.name, ringId = ringId, createdAt = clock.instant()))
                .copy(
                    name = d.name,
                    nickname = d.nickname,
                    otherScriptName = d.otherScriptName,
                    photoFileName = d.photoFileName,
                    howWeMet = d.howWeMet,
                    notes = d.notes,
                    ringId = ringId,
                )
                .withCadenceSetting(d.cadence)
            val id = if (existing == null) {
                personDao.insert(person)
            } else {
                personDao.update(person)
                replacedPhoto = existing.photoFileName?.takeIf { it != d.photoFileName }
                personDao.deleteItems(person.id)
                personDao.deleteContactMethods(person.id)
                personDao.deletePersonTags(person.id)
                person.id
            }
            personDao.insertItems(
                PersonItemKind.entries.flatMap { kind ->
                    d.items(kind).mapIndexed { position, text ->
                        PersonItem(personId = id, kind = kind, text = text, position = position)
                    }
                },
            )
            personDao.insertContactMethods(
                d.contactMethods.mapIndexed { position, method ->
                    ContactMethod(
                        personId = id,
                        type = method.type,
                        value = method.value,
                        label = method.label,
                        isPrimary = method.isPrimary,
                        position = position,
                    )
                },
            )
            personDao.insertPersonTags(
                d.tags.mapIndexed { position, name ->
                    val tagId = tagDao.find(name)?.id ?: tagDao.insert(Tag(name = name))
                    PersonTag(personId = id, tagId = tagId, position = position)
                },
            )
            syncImportantDates(id, d.importantDates)
            id
        }
        replacedPhoto?.let(photoStore::delete)
        return id
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        personDao.setArchivedAt(id, if (archived) clock.instant().toEpochMilli() else null)
    }

    override suspend fun setSnoozedUntil(id: Long, until: LocalDate?): LocalDate? =
        database.withWriteTransaction {
            val before = personDao.get(id)?.snoozedUntil
            personDao.setSnoozedUntil(id, until)
            before
        }

    /**
     * Makes [personId]'s Important dates exactly [drafts], in that order. Dates that already exist
     * keep their ids (later, their Reminders), so editing one doesn't look like delete + add.
     */
    private suspend fun syncImportantDates(personId: Long, drafts: List<ImportantDateDraft>) {
        val existingIds = personDao.getImportantDates(personId).mapTo(HashSet(), ImportantDate::id)
        val keptIds = drafts.mapNotNull { it.id }.filter { it in existingIds }.toSet()
        (existingIds - keptIds).forEach { personDao.deleteImportantDate(it) }
        drafts.forEachIndexed { position, draft ->
            val date = draft.toEntity(personId, position)
            if (draft.id != null && draft.id in keptIds) {
                personDao.updateImportantDate(date.copy(id = draft.id))
            } else {
                personDao.insertImportantDate(date)
            }
        }
    }

    override suspend fun saveImportantDate(personId: Long, draft: ImportantDateDraft): Long {
        val d = draft.normalised()
        return database.withWriteTransaction {
            val existing = personDao.getImportantDates(personId)
            val others = existing.filter { it.id != d.id }
            requireOneBirthdayAtMost(others.map(ImportantDate::type) + d.type)
            val current = existing.firstOrNull { it.id == d.id }
            if (current != null) {
                personDao.updateImportantDate(d.toEntity(personId, current.position).copy(id = current.id))
                current.id
            } else {
                val position = (existing.maxOfOrNull(ImportantDate::position) ?: -1) + 1
                personDao.insertImportantDate(d.toEntity(personId, position))
            }
        }
    }

    override suspend fun deleteImportantDate(id: Long) = personDao.deleteImportantDate(id)

    override suspend fun saveGiftIdea(personId: Long, draft: GiftIdeaDraft): Long {
        val d = draft.normalised()
        require(d.text.isNotEmpty()) { "A Gift idea needs its text" }
        return database.withWriteTransaction {
            val current = d.id?.let { personDao.getGiftIdea(it) }?.takeIf { it.personId == personId }
            if (current != null) {
                personDao.updateGiftIdea(current.copy(text = d.text, link = d.link, note = d.note))
                current.id
            } else {
                personDao.insertGiftIdea(
                    GiftIdea(personId = personId, text = d.text, link = d.link, note = d.note, createdAt = clock.instant()),
                )
            }
        }
    }

    override suspend fun setGiftIdeaGiven(id: Long, date: LocalDate?) = personDao.setGiftIdeaGivenOn(id, date)

    override suspend fun deleteGiftIdea(id: Long) = personDao.deleteGiftIdea(id)

    override suspend fun saveFollowUp(personId: Long, draft: FollowUpDraft): Long {
        val d = draft.normalised()
        require(d.text.isNotEmpty()) { "A Follow-up needs its text" }
        return database.withWriteTransaction {
            val current = d.id?.let { personDao.getFollowUp(it) }?.takeIf { it.personId == personId }
            if (current != null) {
                personDao.updateFollowUp(current.copy(text = d.text, dueOn = d.dueOn))
                current.id
            } else {
                personDao.insertFollowUp(FollowUp(personId = personId, text = d.text, dueOn = d.dueOn, createdAt = clock.instant()))
            }
        }
    }

    override suspend fun setFollowUpDone(id: Long, date: LocalDate?) = personDao.setFollowUpDoneOn(id, date)

    override suspend fun deleteFollowUp(id: Long) = personDao.deleteFollowUp(id)

    override suspend fun saveLoan(personId: Long, draft: LoanDraft): Long {
        val d = draft.normalised()
        require(d.text.isNotEmpty()) { "A Loan needs its text" }
        return database.withWriteTransaction {
            val current = d.id?.let { personDao.getLoan(it) }?.takeIf { it.personId == personId }
            if (current != null) {
                personDao.updateLoan(current.copy(direction = d.direction, text = d.text, returnBy = d.returnBy))
                current.id
            } else {
                personDao.insertLoan(
                    Loan(personId = personId, direction = d.direction, text = d.text, returnBy = d.returnBy, createdAt = clock.instant()),
                )
            }
        }
    }

    override suspend fun setLoanReturned(id: Long, date: LocalDate?) = personDao.setLoanReturnedOn(id, date)

    override suspend fun deleteLoan(id: Long) = personDao.deleteLoan(id)

    override suspend fun delete(id: Long) {
        val photo = database.withWriteTransaction {
            val photo = personDao.get(id)?.photoFileName
            personDao.delete(id)
            interactionDao.deleteOrphans()
            photo
        }
        photo?.let(photoStore::delete)
    }

    private companion object {
        /** No Person has id 0, so excluding it excludes nobody. */
        const val NO_PERSON = 0L
    }
}

private fun ImportantDateDraft.toEntity(personId: Long, position: Int) = ImportantDate(
    personId = personId,
    type = type,
    label = label,
    date = date,
    leadTimes = leadTimes,
    position = position,
)

/** A Person has at most one Birthday. */
internal fun requireOneBirthdayAtMost(types: List<ImportantDateType>) =
    require(types.count { it == ImportantDateType.Birthday } <= 1) { "A Person has at most one Birthday" }

/**
 * How names are compared for the duplicate notice: case and runs of spaces ignored, and the Arabic
 * forms of ی and ک folded into the Persian ones (full search normalisation arrives in slice 8).
 */
internal fun nameKey(name: String): String =
    name.trim()
        .replace(WHITESPACE, " ")
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .lowercase()

private val WHITESPACE = Regex("\\s+")
