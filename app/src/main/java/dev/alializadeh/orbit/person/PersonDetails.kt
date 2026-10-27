package dev.alializadeh.orbit.person

import androidx.annotation.StringRes
import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.person.contact.ContactType

/** The three lists of short one-line items a Person owns. Stored by name: never rename. */
enum class PersonItemKind(@StringRes val titleRes: Int) {
    CanHelpWith(R.string.person_section_can_help_with),
    Like(R.string.person_section_likes),
    Dislike(R.string.person_section_dislikes),
}

/**
 * One Like, Dislike or Can help with item: a line of text owned by one Person, in the order it
 * was added ([position]). Items aren't shared; autocomplete only suggests wording used elsewhere.
 */
@Entity(
    tableName = "person_item",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId", "kind", "position")],
)
data class PersonItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val kind: PersonItemKind,
    val text: String,
    val position: Int,
) {
    companion object {
        /** About one line. */
        const val MAX_LENGTH = 60
    }
}

/** A shared label grouping People by context ("Work", "Gym"). Names are unique, ignoring case. */
@Entity(tableName = "tag", indices = [Index("name", unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

/** A Tag on a Person, in the order the Tags were added to them. */
@Entity(
    tableName = "person_tag",
    primaryKeys = ["personId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class PersonTag(
    val personId: Long,
    val tagId: Long,
    val position: Int,
)

/**
 * One way to reach a Person. The [value] is stored as entered (phone digits as ASCII) and always
 * shown that way; the [label] ("work", "home") is optional user text. At most one per Person is
 * [isPrimary]: the repository keeps that true on every save.
 */
@Entity(
    tableName = "contact_method",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId", "position")],
)
data class ContactMethod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val type: ContactType,
    val value: String,
    val label: String? = null,
    val isPrimary: Boolean = false,
    val position: Int,
)
