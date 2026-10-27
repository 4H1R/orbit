package dev.alializadeh.orbit.person

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.reminders.LeadTime

/** What an Important date is. Stored by name: never rename a constant. */
enum class ImportantDateType {
    /** At most one per Person. */
    Birthday,
    Anniversary,

    /** Named by the user ([ImportantDate.label]). */
    Custom,
}

/**
 * A date that recurs every year for one Person: their Birthday, an Anniversary or a Custom date.
 * It keeps the Calendar it was entered in ([date], ADR 0003); occurrences are resolved only when
 * needed. At most one Birthday per Person: the repository and the date sheet keep that true.
 */
@Entity(
    tableName = "important_date",
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
data class ImportantDate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val type: ImportantDateType,
    /** The user's name for a Custom date ("Graduation"); null for the other types. */
    val label: String? = null,
    /** (Calendar, month, day, optional year), as columns `calendar`, `month`, `day`, `year`. */
    @Embedded val date: RecurringDate,
    /**
     * This date's own lead times. Null means "use the default lead times" (a preference); an empty
     * set means "No reminders" (the date still shows on Upcoming).
     */
    val leadTimes: Set<LeadTime>? = null,
    /** The order the dates were added in. */
    val position: Int,
)

/** An Important date as the form and the date sheet hold it: a new one when [id] is null. */
data class ImportantDateDraft(
    val id: Long? = null,
    val type: ImportantDateType,
    val label: String? = null,
    val date: RecurringDate,
    val leadTimes: Set<LeadTime>? = null,
) {
    /** The label trimmed, and dropped for every type but Custom. */
    fun normalised(): ImportantDateDraft =
        copy(label = if (type == ImportantDateType.Custom) label?.trim()?.takeIf(String::isNotEmpty) else null)

    companion object {
        fun of(date: ImportantDate) = ImportantDateDraft(
            id = date.id,
            type = date.type,
            label = date.label,
            date = date.date,
            leadTimes = date.leadTimes,
        )
    }
}

/** Birthday first, then the others in the order they were added. */
val ImportantDateOrder: Comparator<ImportantDate> =
    compareBy<ImportantDate> { it.type != ImportantDateType.Birthday }.thenBy { it.position }.thenBy { it.id }
