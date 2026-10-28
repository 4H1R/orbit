package dev.alializadeh.orbit.person

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.Junction
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import androidx.room3.Update
import java.time.LocalDate
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
    @Relation(parentColumns = ["id"], entityColumns = ["personId"])
    val importantDates: List<ImportantDate>,
    @Relation(parentColumns = ["id"], entityColumns = ["personId"])
    val giftIdeas: List<GiftIdea>,
)

/** A Person with what Upcoming needs about them, read in one transaction. */
data class PersonWithDates(
    @Embedded val person: Person,
    @Relation(parentColumns = ["id"], entityColumns = ["personId"])
    val importantDates: List<ImportantDate>,
    @Relation(parentColumns = ["id"], entityColumns = ["personId"])
    val giftIdeas: List<GiftIdea>,
    @Relation(parentColumns = ["id"], entityColumns = ["personId"])
    val contactMethods: List<ContactMethod>,
    /** Their Tags (Occasions reach People by Tag). */
    @Relation(parentColumns = ["id"], entityColumns = ["personId"])
    val tags: List<PersonTag> = emptyList(),
)

@Dao
interface PersonDao {

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

    @Query("UPDATE person SET snoozedUntil = :until WHERE id = :id")
    suspend fun setSnoozedUntil(id: Long, until: LocalDate?)

    /**
     * The Cadence digest named them as Due on [day] (or left them out for an on-the-day Reminder).
     * Only this column: it's excluded from the data-version triggers, so it's not a data change.
     */
    @Query("UPDATE person SET lastNotifiedDueOn = :day WHERE id = :id")
    suspend fun setLastNotifiedDueOn(id: Long, day: LocalDate?)

    /** [setLastNotifiedDueOn] for several People at once. */
    @Query("UPDATE person SET lastNotifiedDueOn = :day WHERE id IN (:ids)")
    suspend fun setLastNotifiedDueOn(ids: List<Long>, day: LocalDate)

    /** Forgets "last notified as Due" for [ids] (they aren't Due): like [setLastNotifiedDueOn], not a data change. */
    @Query("UPDATE person SET lastNotifiedDueOn = NULL WHERE id IN (:ids)")
    suspend fun clearLastNotifiedDueOn(ids: List<Long>)

    /** Every Person, Archived ones included. */
    @Query("SELECT * FROM person")
    fun observeAll(): Flow<List<Person>>

    /**
     * Every Person, Archived ones included (Upcoming leaves those out itself), with their
     * Important dates, Gift ideas and Contact methods.
     */
    @Transaction
    @Query("SELECT * FROM person")
    fun observeWithDates(): Flow<List<PersonWithDates>>

    /** Every Person, Archived ones included, with everything stored around them (the People list). */
    @Transaction
    @Query("SELECT * FROM person")
    fun observeAllWithDetails(): Flow<List<PersonWithDetails>>

    /**
     * Deletes the Person; their items, Tags links, Contact methods, Important dates and Gift ideas
     * go with them (CASCADE), and so do their places in Interactions (the repository then deletes the
     * Interactions left with nobody).
     */
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

    // Important dates

    @Query("SELECT * FROM important_date WHERE personId = :personId")
    suspend fun getImportantDates(personId: Long): List<ImportantDate>

    @Insert
    suspend fun insertImportantDate(date: ImportantDate): Long

    @Update
    suspend fun updateImportantDate(date: ImportantDate)

    @Query("DELETE FROM important_date WHERE id = :id")
    suspend fun deleteImportantDate(id: Long)

    // Gift ideas

    @Query("SELECT * FROM gift_idea WHERE id = :id")
    suspend fun getGiftIdea(id: Long): GiftIdea?

    @Insert
    suspend fun insertGiftIdea(idea: GiftIdea): Long

    @Update
    suspend fun updateGiftIdea(idea: GiftIdea)

    @Query("UPDATE gift_idea SET givenOn = :givenOn WHERE id = :id")
    suspend fun setGiftIdeaGivenOn(id: Long, givenOn: LocalDate?)

    @Query("DELETE FROM gift_idea WHERE id = :id")
    suspend fun deleteGiftIdea(id: Long)

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

    @Query("SELECT * FROM tag WHERE id = :id")
    suspend fun get(id: Long): Tag?

    /** Every Tag with how many People (Archived ones too) have it. */
    @Query(
        "SELECT tag.*, (SELECT COUNT(*) FROM person_tag WHERE person_tag.tagId = tag.id) AS peopleCount " +
            "FROM tag ORDER BY name COLLATE NOCASE",
    )
    fun observeWithCounts(): Flow<List<TagWithCount>>

    @Query("UPDATE tag SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /**
     * Gives everyone tagged [sourceId] the Tag [targetId] in its place (those who already have it
     * keep theirs).
     */
    @Query(
        "INSERT OR IGNORE INTO person_tag (personId, tagId, position) " +
            "SELECT personId, :targetId, position FROM person_tag WHERE tagId = :sourceId",
    )
    suspend fun copyPeople(sourceId: Long, targetId: Long)

    /** Deletes the Tag; it comes off everyone (CASCADE). */
    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun delete(id: Long)
}

/** A Tag and how many People have it (Settings → Tags). */
data class TagWithCount(
    @Embedded val tag: Tag,
    val peopleCount: Int,
)
