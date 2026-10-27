package dev.alializadeh.orbit.person

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The People tab's root: the list of People. */
@Serializable
data object PeopleKey : NavKey

@Serializable
data object AddPersonKey : NavKey

@Serializable
data class PersonDetailKey(val personId: Long) : NavKey
