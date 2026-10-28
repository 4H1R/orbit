package dev.alializadeh.orbit.interaction

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.due.DueState
import dev.alializadeh.orbit.due.SnoozeDuration
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.RelativeDays
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.format.text
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.names
import dev.alializadeh.orbit.person.ui.cadenceText
import dev.alializadeh.orbit.ui.icons.OrbitIcons
import java.time.LocalDate

@Composable
@ReadOnlyComposable
fun InteractionType.displayName(): String = stringResource(
    when (this) {
        InteractionType.Call -> R.string.interaction_type_call
        InteractionType.Message -> R.string.interaction_type_message
        InteractionType.InPerson -> R.string.interaction_type_in_person
        InteractionType.Other -> R.string.interaction_type_other
    },
)

val InteractionType.icon: ImageVector
    get() = when (this) {
        InteractionType.Call -> Icons.Filled.Call
        InteractionType.Message -> OrbitIcons.Chat
        InteractionType.InPerson -> OrbitIcons.People
        InteractionType.Other -> OrbitIcons.MoreHoriz
    }

/** "3 days", "1 week", "1 month" / «۳ روز», «یک هفته», «یک ماه» (copy deck: snoozed_snackbar). */
@Composable
@ReadOnlyComposable
fun SnoozeDuration.text(): String = when (this) {
    SnoozeDuration.ThreeDays ->
        LocalResources.current.getQuantityString(R.plurals.duration_days, 3, LocalOrbitFormats.current.numbers.format(3))
    SnoozeDuration.OneWeek -> stringResource(R.string.duration_one_week)
    SnoozeDuration.OneMonth -> stringResource(R.string.duration_one_month)
}

/**
 * "Last talked 34 days ago", or "No interactions yet" ("last talked…" and "no interactions yet"
 * when [inline], after a separator or a comma).
 */
@Composable
@ReadOnlyComposable
fun lastTalkedText(lastInteraction: LocalDate?, today: LocalDate, inline: Boolean = false): String {
    if (lastInteraction == null) return stringResource(if (inline) R.string.no_interactions_yet_inline else R.string.no_interactions_yet)
    val ago = RelativeDays.ago(lastInteraction, today).text()
    return stringResource(if (inline) R.string.last_interaction_inline else R.string.last_interaction, ago)
}

/** "Last talked 34 days ago · every 30 days"; just the first part with No Cadence. */
@Composable
@ReadOnlyComposable
fun cadenceLine(state: DueState, today: LocalDate): String {
    val parts = listOfNotNull(
        lastTalkedText(state.lastInteraction, today),
        state.cadence?.let { cadenceText(it) },
    )
    return parts.joinToString(stringResource(R.string.separator))
}

/**
 * "with Reza, Mina": the others in an Interaction (everyone but [exceptId]), by displayed name,
 * each isolated; null when it's only that Person.
 */
@Composable
@ReadOnlyComposable
fun withOthersText(people: List<Person>, exceptId: Long?, language: OrbitLanguage): String? {
    val others = people.filter { it.id != exceptId }
    if (others.isEmpty()) return null
    val names = others.joinToString(stringResource(R.string.list_separator)) { bidiIsolate(it.names.displayName(language)) }
    return stringResource(R.string.interaction_with, names)
}
