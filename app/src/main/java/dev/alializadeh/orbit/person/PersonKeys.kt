package dev.alializadeh.orbit.person

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The People tab's root: the list of People. */
@Serializable
data object PeopleKey : NavKey

/** The Add/Edit form for a new Person. Once saved, it's replaced by their Person detail. */
@Serializable
data object AddPersonKey : NavKey

/** The Add/Edit form for an existing Person, optionally scrolled to [focus] (an Add details chip). */
@Serializable
data class EditPersonKey(val personId: Long, val focus: PersonField? = null) : NavKey

@Serializable
data class PersonDetailKey(val personId: Long) : NavKey

/**
 * Fields of the Add/Edit form that Person detail's Add details row can open, in the form's order.
 * Those after [Birthday] sit behind "More details" until they have content. Stored in saved back
 * stacks by name: never rename a constant (new ones can go anywhere).
 */
@Serializable
enum class PersonField {
    Photo,
    ContactMethods,

    /** Opens the date sheet for a new Birthday. */
    Birthday,
    Nickname,
    OtherScriptName,
    Tags,

    /** The other Important dates; opens the date sheet for a new one. */
    ImportantDates,
    CanHelpWith,
    Likes,
    Dislikes,
    HowWeMet,
    Notes,
    ;

    val isBehindMoreDetails: Boolean get() = this > Birthday

    companion object {
        val moreDetails: List<PersonField> = entries.filter(PersonField::isBehindMoreDetails)

        fun of(kind: PersonItemKind): PersonField = when (kind) {
            PersonItemKind.CanHelpWith -> CanHelpWith
            PersonItemKind.Like -> Likes
            PersonItemKind.Dislike -> Dislikes
        }
    }
}
