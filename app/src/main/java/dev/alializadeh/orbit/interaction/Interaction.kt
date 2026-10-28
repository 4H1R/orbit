package dev.alializadeh.orbit.interaction

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.Junction
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import androidx.room3.Update
import dev.alializadeh.orbit.person.Person
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** What kind of contact an Interaction was. Stored by name: never rename a constant. */
enum class InteractionType {
    Call,
    Message,
    InPerson,
    Other,
}

/**
 * A logged moment of contact with one or more People ([InteractionPerson]) on a [date] with no
 * time, with an optional [note]. Shared by everyone in it: an edit applies for all of them. When a
 * Person is deleted they leave it, and it stays for the others; one left with nobody is deleted.
 */
@Entity(tableName = "interaction", indices = [Index("date")])
data class Interaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: InteractionType,
    val date: LocalDate,
    val note: String? = null,
)

/** A Person in an Interaction. */
@Entity(
    tableName = "interaction_person",
    primaryKeys = ["interactionId", "personId"],
    foreignKeys = [
        ForeignKey(
            entity = Interaction::class,
            parentColumns = ["id"],
            childColumns = ["interactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class InteractionPerson(
    val interactionId: Long,
    val personId: Long,
)

/** An Interaction with everyone in it, read in one transaction. */
data class InteractionWithPeople(
    @Embedded val interaction: Interaction,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        associateBy = Junction(InteractionPerson::class, parentColumns = ["interactionId"], entityColumns = ["personId"]),
    )
    val people: List<Person>,
)

/** A Person's latest Interaction date, of any type. */
data class LastInteractionDate(
    val personId: Long,
    val date: LocalDate,
)

@Dao
interface InteractionDao {

    /** [personId]'s Interactions, newest first (on the same day, the last logged first). */
    @Transaction
    @Query(
        "SELECT interaction.* FROM interaction " +
            "JOIN interaction_person ON interaction_person.interactionId = interaction.id " +
            "WHERE interaction_person.personId = :personId " +
            "ORDER BY interaction.date DESC, interaction.id DESC",
    )
    fun observeForPerson(personId: Long): Flow<List<InteractionWithPeople>>

    @Transaction
    @Query("SELECT * FROM interaction WHERE id = :id")
    fun observeWithPeople(id: Long): Flow<InteractionWithPeople?>

    @Transaction
    @Query("SELECT * FROM interaction WHERE id = :id")
    suspend fun getWithPeople(id: Long): InteractionWithPeople?

    /** Every Person's latest Interaction date; People without one are left out. */
    @Query(
        "SELECT interaction_person.personId AS personId, MAX(interaction.date) AS date " +
            "FROM interaction_person JOIN interaction ON interaction.id = interaction_person.interactionId " +
            "GROUP BY interaction_person.personId",
    )
    fun observeLastDates(): Flow<List<LastInteractionDate>>

    @Insert
    suspend fun insert(interaction: Interaction): Long

    @Update
    suspend fun update(interaction: Interaction)

    @Insert
    suspend fun insertPeople(people: List<InteractionPerson>)

    @Query("DELETE FROM interaction_person WHERE interactionId = :interactionId")
    suspend fun deletePeople(interactionId: Long)

    @Query("DELETE FROM interaction WHERE id = :id")
    suspend fun delete(id: Long)

    /** Deletes the Interactions nobody is in any more (after a Person was deleted). */
    @Query("DELETE FROM interaction WHERE id NOT IN (SELECT interactionId FROM interaction_person)")
    suspend fun deleteOrphans(): Int

    @Query("SELECT COUNT(*) FROM interaction")
    suspend fun count(): Int
}
