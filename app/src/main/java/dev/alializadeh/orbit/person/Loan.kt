package dev.alializadeh.orbit.person

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** Which way a Loan went. Stored by name: never rename a constant. */
enum class LoanDirection {
    /** The user gave it to the Person. */
    Lent,

    /** The Person gave it to the user. */
    Borrowed,
}

/**
 * Something lent to or borrowed from one Person: an object or, as plain text, an amount of money
 * (Orbit does no sums). Open until Returned on [returnedOn]; returned ones stay as history.
 */
@Entity(
    tableName = "loan",
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
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val direction: LoanDirection,
    val text: String,
    /** When it should come back, if there's a day for that. */
    val returnBy: LocalDate? = null,
    /** The day it came back; null while it's open. */
    val returnedOn: LocalDate? = null,
    val createdAt: Instant,
) {
    val isReturned: Boolean get() = returnedOn != null
}

/** A Loan as its sheet holds it: a new one when [id] is null. */
data class LoanDraft(
    val id: Long? = null,
    val direction: LoanDirection,
    val text: String,
    val returnBy: LocalDate? = null,
) {
    fun normalised(): LoanDraft = copy(text = text.trim())
}

/** Those with a day first, soonest first; then in the order they were noted. */
val LoanOrder: Comparator<Loan> =
    compareBy<Loan> { it.returnBy == null }.thenBy { it.returnBy }.thenBy { it.id }
