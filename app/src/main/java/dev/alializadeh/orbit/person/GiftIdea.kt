package dev.alializadeh.orbit.person

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * A possible present for one Person: still an Idea, or Given on [givenOn]. There's no occasion:
 * every Important date of the Person surfaces their open ideas. Given ideas stay as history.
 */
@Entity(
    tableName = "gift_idea",
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
data class GiftIdea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val text: String,
    /** A web link, stored as typed; opened through an intent. */
    val link: String? = null,
    val note: String? = null,
    /** The day it was given; null while it's still an idea. */
    val givenOn: LocalDate? = null,
    /** Ideas are listed in the order they were noted. */
    val createdAt: Instant,
) {
    val isGiven: Boolean get() = givenOn != null
}

/** A Gift idea as its sheet holds it: a new one when [id] is null. */
data class GiftIdeaDraft(
    val id: Long? = null,
    val text: String,
    val link: String? = null,
    val note: String? = null,
) {
    fun normalised(): GiftIdeaDraft = copy(
        text = text.trim(),
        link = link?.trim()?.takeIf(String::isNotEmpty),
        note = note?.trim()?.takeIf(String::isNotEmpty),
    )

    companion object {
        fun of(idea: GiftIdea) = GiftIdeaDraft(id = idea.id, text = idea.text, link = idea.link, note = idea.note)
    }
}

/** Oldest first. */
val GiftIdeaOrder: Comparator<GiftIdea> = compareBy<GiftIdea> { it.createdAt }.thenBy { it.id }
