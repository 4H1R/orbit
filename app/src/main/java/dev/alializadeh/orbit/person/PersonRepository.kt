package dev.alializadeh.orbit.person

import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Reads and writes People. Screens observe it through Flows. */
interface PersonRepository {

    /** Every Person, sorted by Name. */
    fun observePeople(): Flow<List<Person>>

    /** One Person, or null once they no longer exist. */
    fun observePerson(id: Long): Flow<Person?>

    /** Adds a Person called [name] (trimmed, must not be blank) and returns their id. */
    suspend fun addPerson(name: String): Long
}

class RoomPersonRepository @Inject constructor(
    private val personDao: PersonDao,
    private val clock: Clock,
) : PersonRepository {

    override fun observePeople(): Flow<List<Person>> = personDao.observeAll()

    override fun observePerson(id: Long): Flow<Person?> = personDao.observe(id)

    override suspend fun addPerson(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A Person needs a Name" }
        return personDao.insert(Person(name = trimmed, createdAt = clock.instant()))
    }
}
