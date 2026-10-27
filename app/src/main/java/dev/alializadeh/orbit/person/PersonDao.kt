package dev.alializadeh.orbit.person

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    /**
     * Every Person, by Name. `NOCASE` folds ASCII case only; Persian collation for the Persian UI
     * arrives with the People list in slice 2.
     */
    @Query("SELECT * FROM person ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<Person>>

    @Query("SELECT * FROM person WHERE id = :id")
    fun observe(id: Long): Flow<Person?>

    @Insert
    suspend fun insert(person: Person): Long
}
