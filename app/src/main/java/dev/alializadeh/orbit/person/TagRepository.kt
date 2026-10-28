package dev.alializadeh.orbit.person

import androidx.room3.withWriteTransaction
import dev.alializadeh.orbit.data.OrbitDatabase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Settings → Tags: rename, merge and delete Tags. (Tags are created inline on the form.) */
interface TagRepository {

    /** Every Tag with its count of People. */
    fun observeTags(): Flow<List<TagWithCount>>

    /** The Tag called [name] (ignoring case), if there is one. */
    suspend fun find(name: String): Tag?

    /**
     * Renames the Tag. Throws if another Tag already has that name: renaming to an existing name
     * is a [merge], which the screen asks about first.
     */
    suspend fun rename(id: Long, name: String)

    /** Everyone tagged [sourceId] is tagged [targetId] instead, and [sourceId] is deleted. */
    suspend fun merge(sourceId: Long, targetId: Long)

    /** Deletes the Tag and removes it from everyone. */
    suspend fun delete(id: Long)
}

class RoomTagRepository @Inject constructor(
    private val database: OrbitDatabase,
    private val tagDao: TagDao,
) : TagRepository {

    override fun observeTags(): Flow<List<TagWithCount>> = tagDao.observeWithCounts()

    override suspend fun find(name: String): Tag? = tagDao.find(name.trim())

    override suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A Tag needs a name" }
        database.withWriteTransaction {
            val clash = tagDao.find(trimmed)
            require(clash == null || clash.id == id) { "Another Tag is called $trimmed: merge instead" }
            tagDao.rename(id, trimmed)
        }
    }

    override suspend fun merge(sourceId: Long, targetId: Long) {
        require(sourceId != targetId) { "A Tag can't merge into itself" }
        database.withWriteTransaction {
            requireNotNull(tagDao.get(targetId)) { "No Tag $targetId" }
            tagDao.copyPeople(sourceId, targetId)
            tagDao.delete(sourceId)
        }
    }

    override suspend fun delete(id: Long) = tagDao.delete(id)
}
