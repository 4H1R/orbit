package dev.alializadeh.orbit.person

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.Junction
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/** A Person with everything stored around them, read in one transaction. */
data class PersonWithDetails(
    @Embedded val person: Person,
    @Relation(parentColumns = ["id"], entityColumns = ["personId"])
    val items: List<PersonItem>,
    @Relation(parentColumns = ["id"], entityColumns = ["personId"])
    val contactMethods: List<ContactMethod>,
    @Relation(parentColumns = ["id"], entityColumns = ["personId"])
    val personTags: List<PersonTag>,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        associateBy = Junction(PersonTag::class, parentColumns = ["personId"], entityColumns = ["tagId"]),
    )
    val tags: List<Tag>,
)

@Dao
interface PersonDao {

    /** Every Person who isn't Archived, in no order: lists sort by the displayed name in Kotlin. */
    @Query("SELECT * FROM person WHERE archivedAt IS NULL")
    fun observeActive(): Flow<List<Person>>

    @Transaction
    @Query("SELECT * FROM person WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<PersonWithDetails?>

    @Transaction
    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun getWithDetails(id: Long): PersonWithDetails?

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun get(id: Long): Person?

    /** Every Person, Archived ones included (for the duplicate-name notice). */
    @Query("SELECT * FROM person")
    suspend fun getAll(): List<Person>

    @Insert
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    @Query("UPDATE person SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun setArchivedAt(id: Long, archivedAt: Long?)

    /** Deletes the Person; their items, Tags links and Contact methods go with them (CASCADE). */
    @Query("DELETE FROM person WHERE id = :id")
    suspend fun delete(id: Long)

    // Likes, Dislikes and Can help with

    @Insert
    suspend fun insertItems(items: List<PersonItem>)

    @Query("DELETE FROM person_item WHERE personId = :personId")
    suspend fun deleteItems(personId: Long)

    /** The distinct wording of [kind] items on People other than [excludePersonId], for autocomplete. */
    @Query(
        "SELECT DISTINCT text FROM person_item WHERE kind = :kind AND personId != :excludePersonId " +
            "ORDER BY text COLLATE NOCASE",
    )
    fun observeItemTexts(kind: PersonItemKind, excludePersonId: Long): Flow<List<String>>

    // Contact methods

    @Insert
    suspend fun insertContactMethods(methods: List<ContactMethod>)

    @Query("DELETE FROM contact_method WHERE personId = :personId")
    suspend fun deleteContactMethods(personId: Long)

    // Tags on a Person

    @Insert
    suspend fun insertPersonTags(personTags: List<PersonTag>)

    @Query("DELETE FROM person_tag WHERE personId = :personId")
    suspend fun deletePersonTags(personId: Long)
}

@Dao
interface TagDao {

    /** Every Tag, by name. */
    @Query("SELECT * FROM tag ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tag>>

    /** The Tag called [name], ignoring (ASCII) case. */
    @Query("SELECT * FROM tag WHERE name = :name")
    suspend fun find(name: String): Tag?

    @Insert
    suspend fun insert(tag: Tag): Long
}
