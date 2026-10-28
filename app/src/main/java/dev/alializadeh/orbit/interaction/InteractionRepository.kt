package dev.alializadeh.orbit.interaction

import androidx.room3.withWriteTransaction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.data.OrbitDatabase
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * An Interaction as the Log interaction sheet holds it: a new one when [id] is null. [personIds]
 * are everyone in it, in the order they were picked.
 */
data class InteractionDraft(
    val id: Long? = null,
    val type: InteractionType,
    val date: LocalDate,
    val note: String? = null,
    val personIds: List<Long>,
) {
    /** The note trimmed (blank is none), each Person once. */
    fun normalised(): InteractionDraft = copy(
        note = note?.trim()?.takeIf(String::isNotEmpty),
        personIds = personIds.distinct(),
    )
}

/** Logs, edits and deletes Interactions. */
interface InteractionRepository {

    /** [personId]'s Interactions with everyone in them, newest first. */
    fun observeForPerson(personId: Long): Flow<List<InteractionWithPeople>>

    /** One Interaction, or null once it's gone. */
    fun observeInteraction(id: Long): Flow<InteractionWithPeople?>

    /** The ids of everyone with an Interaction on [date]. */
    fun observePeopleOn(date: LocalDate): Flow<Set<Long>>

    /**
     * Logs the [draft] (no id) or edits that Interaction for everyone in it, and returns its id.
     * Needs at least one Person and a date that isn't after today.
     */
    suspend fun save(draft: InteractionDraft): Long

    suspend fun delete(id: Long)

    /**
     * Mark as contacted (from a notification, slice 6): an Interaction of type Other with only
     * [personId], dated today, with no note. Returns its id.
     */
    suspend fun markAsContacted(personId: Long): Long
}

class RoomInteractionRepository @Inject constructor(
    private val database: OrbitDatabase,
    private val interactionDao: InteractionDao,
    private val clock: Clock,
) : InteractionRepository {

    override fun observeForPerson(personId: Long): Flow<List<InteractionWithPeople>> =
        interactionDao.observeForPerson(personId)

    override fun observeInteraction(id: Long): Flow<InteractionWithPeople?> = interactionDao.observeWithPeople(id)

    override fun observePeopleOn(date: LocalDate): Flow<Set<Long>> = interactionDao.observePeopleOn(date).map { it.toSet() }

    override suspend fun save(draft: InteractionDraft): Long {
        val d = draft.normalised()
        require(d.personIds.isNotEmpty()) { "An Interaction needs at least one Person" }
        require(!d.date.isAfter(LocalDate.now(clock))) { "An Interaction can't be in the future" }
        return database.withWriteTransaction {
            val interaction = Interaction(type = d.type, date = d.date, note = d.note)
            val existing = d.id?.let { interactionDao.getWithPeople(it) }
            val id = if (existing != null) {
                interactionDao.update(interaction.copy(id = existing.interaction.id))
                interactionDao.deletePeople(existing.interaction.id)
                existing.interaction.id
            } else {
                interactionDao.insert(interaction)
            }
            interactionDao.insertPeople(d.personIds.map { InteractionPerson(interactionId = id, personId = it) })
            id
        }
    }

    override suspend fun delete(id: Long) = interactionDao.delete(id)

    override suspend fun markAsContacted(personId: Long): Long = save(
        InteractionDraft(type = InteractionType.Other, date = LocalDate.now(clock), personIds = listOf(personId)),
    )
}

@Module
@InstallIn(SingletonComponent::class)
interface InteractionModule {

    @Binds
    fun bindInteractionRepository(repository: RoomInteractionRepository): InteractionRepository
}
