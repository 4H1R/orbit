package dev.alializadeh.orbit.occasion

import androidx.room3.withWriteTransaction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.data.OrbitDatabase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface OccasionRepository {
    /** Every Occasion with who it's for. */
    fun observeOccasions(): Flow<List<OccasionWithAudience>>

    suspend fun get(id: Long): OccasionWithAudience?

    /** Adds or updates the Occasion and who it's for, in one transaction. Returns its id. */
    suspend fun save(draft: OccasionDraft): Long

    suspend fun delete(id: Long)
}

class RoomOccasionRepository @Inject constructor(
    private val database: OrbitDatabase,
    private val dao: OccasionDao,
) : OccasionRepository {

    override fun observeOccasions(): Flow<List<OccasionWithAudience>> =
        combine(dao.observeAll(), dao.observeRings(), dao.observeTags(), dao.observePeople()) { occasions, rings, tags, people ->
            val ringIds = rings.groupBy({ it.occasionId }, { it.ringId })
            val tagIds = tags.groupBy({ it.occasionId }, { it.tagId })
            val personIds = people.groupBy({ it.occasionId }, { it.personId })
            occasions.map {
                OccasionWithAudience(
                    occasion = it,
                    ringIds = ringIds[it.id].orEmpty().toSet(),
                    tagIds = tagIds[it.id].orEmpty().toSet(),
                    personIds = personIds[it.id].orEmpty().toSet(),
                )
            }
        }

    override suspend fun get(id: Long): OccasionWithAudience? {
        val occasion = dao.get(id) ?: return null
        return OccasionWithAudience(occasion, dao.ringIds(id).toSet(), dao.tagIds(id).toSet(), dao.personIds(id).toSet())
    }

    override suspend fun save(draft: OccasionDraft): Long {
        val d = draft.normalised()
        require(d.canSave) { "An Occasion of the user's own needs a name" }
        return database.withWriteTransaction {
            val occasion = Occasion(
                id = d.id ?: 0,
                builtIn = d.builtIn,
                label = d.label,
                date = d.date,
                forEveryone = d.forEveryone,
                leadTimes = d.leadTimes,
            )
            val id = if (d.id != null && dao.get(d.id) != null) {
                dao.update(occasion)
                dao.deleteRings(d.id)
                dao.deleteTags(d.id)
                dao.deletePeople(d.id)
                d.id
            } else {
                dao.insert(occasion.copy(id = 0))
            }
            dao.insertRings(d.ringIds.map { OccasionRing(id, it) })
            dao.insertTags(d.tagIds.map { OccasionTag(id, it) })
            dao.insertPeople(d.personIds.map { OccasionPerson(id, it) })
            id
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

@Module
@InstallIn(SingletonComponent::class)
interface OccasionModule {

    @Binds
    fun bindOccasionRepository(repository: RoomOccasionRepository): OccasionRepository
}
