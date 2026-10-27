package dev.alializadeh.orbit.ring

import androidx.annotation.StringRes
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import dev.alializadeh.orbit.R
import kotlinx.coroutines.flow.Flow

/**
 * How close a Person is to the user. Rings are an ordered list, innermost first ([position] 0), of
 * at most [MAX_COUNT]. The [name] is user data: the seeds are written once in the language chosen
 * on Welcome and never re-translated.
 */
@Entity(tableName = "ring")
data class Ring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 0 is the innermost Ring. */
    val position: Int,
    /** The default Cadence of People in this Ring; null is "No Cadence". */
    @Embedded(prefix = "cadence_") val cadence: Cadence?,
) {
    companion object {
        const val MAX_COUNT = 6
    }
}

/** The four Rings first run creates, innermost first (copy deck: Seed Rings). */
enum class SeedRing(@StringRes val nameRes: Int, val cadence: Cadence?) {
    Inner(R.string.seed_ring_inner, Cadence.days(14)),
    Close(R.string.seed_ring_close, Cadence.days(30)),
    Friends(R.string.seed_ring_friends, Cadence.days(90)),
    Acquaintances(R.string.seed_ring_acquaintances, null),
}

@Dao
interface RingDao {

    /** Every Ring, innermost first. */
    @Query("SELECT * FROM ring ORDER BY position, id")
    fun observeAll(): Flow<List<Ring>>

    @Query("SELECT * FROM ring WHERE id = :id")
    suspend fun get(id: Long): Ring?

    @Query("SELECT COUNT(*) FROM ring")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(rings: List<Ring>)

    @Insert
    suspend fun insert(ring: Ring): Long
}
