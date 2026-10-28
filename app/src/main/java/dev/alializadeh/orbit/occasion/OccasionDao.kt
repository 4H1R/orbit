package dev.alializadeh.orbit.occasion

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OccasionDao {

    @Query("SELECT * FROM occasion")
    fun observeAll(): Flow<List<Occasion>>

    @Query("SELECT * FROM occasion_ring")
    fun observeRings(): Flow<List<OccasionRing>>

    @Query("SELECT * FROM occasion_tag")
    fun observeTags(): Flow<List<OccasionTag>>

    @Query("SELECT * FROM occasion_person")
    fun observePeople(): Flow<List<OccasionPerson>>

    @Query("SELECT * FROM occasion WHERE id = :id")
    suspend fun get(id: Long): Occasion?

    @Query("SELECT ringId FROM occasion_ring WHERE occasionId = :id")
    suspend fun ringIds(id: Long): List<Long>

    @Query("SELECT tagId FROM occasion_tag WHERE occasionId = :id")
    suspend fun tagIds(id: Long): List<Long>

    @Query("SELECT personId FROM occasion_person WHERE occasionId = :id")
    suspend fun personIds(id: Long): List<Long>

    @Insert
    suspend fun insert(occasion: Occasion): Long

    @Update
    suspend fun update(occasion: Occasion)

    @Insert
    suspend fun insertRings(rows: List<OccasionRing>)

    @Insert
    suspend fun insertTags(rows: List<OccasionTag>)

    @Insert
    suspend fun insertPeople(rows: List<OccasionPerson>)

    @Query("DELETE FROM occasion_ring WHERE occasionId = :id")
    suspend fun deleteRings(id: Long)

    @Query("DELETE FROM occasion_tag WHERE occasionId = :id")
    suspend fun deleteTags(id: Long)

    @Query("DELETE FROM occasion_person WHERE occasionId = :id")
    suspend fun deletePeople(id: Long)

    /** Its Rings, Tags and People links go with it (CASCADE). */
    @Query("DELETE FROM occasion WHERE id = :id")
    suspend fun delete(id: Long)
}
