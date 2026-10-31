package dev.alializadeh.orbit.person

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Something specific to come back to with one Person on [dueOn] ("ask how the interview went"):
 * open until Done on [doneOn]. Done ones stay as history. It doesn't touch their Cadence.
 */
@Entity(
    tableName = "follow_up",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class FollowUp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val text: String,
    val dueOn: LocalDate,
    /** The day it was done; null while it's open. */
    val doneOn: LocalDate? = null,
    val createdAt: Instant,
) {
    val isDone: Boolean get() = doneOn != null
}

/** A Follow-up as its sheet holds it: a new one when [id] is null. */
data class FollowUpDraft(
    val id: Long? = null,
    val text: String,
    val dueOn: LocalDate,
) {
    fun normalised(): FollowUpDraft = copy(text = text.trim())
}

/** Soonest first. */
val FollowUpOrder: Comparator<FollowUp> = compareBy<FollowUp> { it.dueOn }.thenBy { it.id }
