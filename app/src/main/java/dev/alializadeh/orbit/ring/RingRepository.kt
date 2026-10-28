package dev.alializadeh.orbit.ring

import androidx.room3.withWriteTransaction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.data.OrbitDatabase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Reads and edits Rings (Settings → Rings): at most [Ring.MAX_COUNT], innermost first. A Ring with
 * People in it can't be removed until they're moved, and the last Ring can't be removed at all.
 */
interface RingRepository {
    /** Every Ring, innermost first. */
    fun observeRings(): Flow<List<Ring>>

    /** Every Ring, innermost first, with how many People are in it. */
    fun observeRingsWithCounts(): Flow<List<RingWithCount>>

    /** Adds an outermost Ring and returns its id. Throws at [Ring.MAX_COUNT] Rings or a blank name. */
    suspend fun add(name: String, cadence: Cadence?): Long

    /** Renames a Ring and sets its default Cadence (null: "No Cadence"). */
    suspend fun update(id: Long, name: String, cadence: Cadence?)

    /** Moves every Person in Ring [fromId] (Archived ones too) to Ring [toId]. */
    suspend fun movePeople(fromId: Long, toId: Long)

    /** Removes an empty Ring that isn't the last one; throws otherwise. */
    suspend fun remove(id: Long)

    /** Puts the Rings in this order, innermost first. [ids] must be every Ring exactly once. */
    suspend fun reorder(ids: List<Long>)
}

class RoomRingRepository @Inject constructor(
    private val database: OrbitDatabase,
    private val ringDao: RingDao,
) : RingRepository {

    override fun observeRings(): Flow<List<Ring>> = ringDao.observeAll()

    override fun observeRingsWithCounts(): Flow<List<RingWithCount>> = ringDao.observeWithCounts()

    override suspend fun add(name: String, cadence: Cadence?): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A Ring needs a name" }
        return database.withWriteTransaction {
            val rings = ringDao.getAll()
            require(rings.size < Ring.MAX_COUNT) { "At most ${Ring.MAX_COUNT} Rings" }
            ringDao.insert(Ring(name = trimmed, position = (rings.maxOfOrNull(Ring::position) ?: -1) + 1, cadence = cadence))
        }
    }

    override suspend fun update(id: Long, name: String, cadence: Cadence?) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A Ring needs a name" }
        ringDao.update(id, trimmed, cadence?.count, cadence?.unit)
    }

    override suspend fun movePeople(fromId: Long, toId: Long) {
        require(fromId != toId) { "Moving People to the Ring they're in" }
        database.withWriteTransaction {
            requireNotNull(ringDao.get(toId)) { "No Ring $toId" }
            ringDao.movePeople(fromId, toId)
        }
    }

    override suspend fun remove(id: Long) {
        database.withWriteTransaction {
            val rings = ringDao.getAll()
            require(rings.any { it.id == id }) { "No Ring $id" }
            require(rings.size > 1) { "The last Ring can't be removed" }
            require(ringDao.peopleCount(id) == 0) { "A Ring with People in it can't be removed" }
            ringDao.delete(id)
            rings.filter { it.id != id }.forEachIndexed { position, ring -> ringDao.setPosition(ring.id, position) }
        }
    }

    override suspend fun reorder(ids: List<Long>) {
        database.withWriteTransaction {
            val existing = ringDao.getAll().map(Ring::id)
            require(ids.size == existing.size && ids.toSet() == existing.toSet()) { "Reorder needs every Ring once" }
            ids.forEachIndexed { position, id -> ringDao.setPosition(id, position) }
        }
    }
}

/** The Ring a new Person starts in: the outermost. */
fun List<Ring>.outermost(): Ring? = maxWithOrNull(compareBy(Ring::position, Ring::id))

@Module
@InstallIn(SingletonComponent::class)
interface RingModule {

    @Binds
    fun bindRingRepository(repository: RoomRingRepository): RingRepository
}
