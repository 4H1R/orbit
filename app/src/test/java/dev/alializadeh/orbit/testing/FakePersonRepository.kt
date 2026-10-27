package dev.alializadeh.orbit.testing

import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.PersonRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [PersonRepository] that keeps People in insertion order. */
class FakePersonRepository(initial: List<Person> = emptyList()) : PersonRepository {

    val people = MutableStateFlow(initial)

    override fun observePeople(): Flow<List<Person>> = people

    override fun observePerson(id: Long): Flow<Person?> =
        people.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun addPerson(name: String): Long {
        val id = (people.value.maxOfOrNull(Person::id) ?: 0) + 1
        people.value += Person(id = id, name = name.trim(), createdAt = Instant.EPOCH)
        return id
    }
}
