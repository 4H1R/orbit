package dev.alializadeh.orbit.orbitview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.interaction.lastTalkedText
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.dates.displayName
import dev.alializadeh.orbit.person.ui.label
import java.time.LocalDate

/**
 * What TalkBack reads for a Person, its parts joined with commas: "Sara Ahmadi, due, last talked
 * 34 days ago, birthday in 5 days".
 */
@Composable
@ReadOnlyComposable
internal fun personReading(person: OrbitPerson, today: LocalDate): String {
    val parts = listOfNotNull(
        person.names.displayName(LocalOrbitFormats.current.language),
        if (person.isDue) stringResource(R.string.orbit_a11y_person_due) else null,
        lastTalkedText(person.due.lastInteraction, today, inline = true),
        person.nearestDate?.let { dateReading(it) },
    )
    return parts.joinToString(stringResource(R.string.list_separator))
}

/** "birthday in 5 days", "anniversary today", "graduation tomorrow". */
@Composable
@ReadOnlyComposable
private fun dateReading(date: NearestDate): String {
    val resources = LocalResources.current
    val numbers = LocalOrbitFormats.current.numbers
    val whenText = when (date.daysAway) {
        0 -> resources.getString(R.string.time_today_inline)
        1 -> resources.getString(R.string.time_tomorrow_inline)
        else -> resources.getQuantityString(R.plurals.time_in_days_inline, date.daysAway, numbers.format(date.daysAway))
    }
    return when (date.type) {
        ImportantDateType.Birthday -> resources.getString(R.string.orbit_a11y_date_birthday, whenText)
        ImportantDateType.Anniversary -> resources.getString(R.string.orbit_a11y_date_anniversary, whenText)
        ImportantDateType.Custom -> resources.getString(
            R.string.orbit_a11y_date_custom,
            bidiIsolate(date.label?.takeIf(String::isNotBlank) ?: date.type.displayName(resources)),
            whenText,
        )
    }
}

/** TalkBack's custom actions for a Person: the long-press sheet's (contact, Log interaction, Snooze when Due). */
@Composable
internal fun personActions(person: OrbitPerson, actions: OrbitPersonActions): List<CustomAccessibilityAction> {
    val contact = person.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() }
    val contactLabel = contact?.kind?.label()
    val log = stringResource(R.string.interaction_log)
    val snooze = stringResource(R.string.snooze_action)
    return listOfNotNull(
        if (contact != null && contactLabel != null) {
            CustomAccessibilityAction(contactLabel) {
                actions.onContactAction(contact)
                true
            }
        } else {
            null
        },
        CustomAccessibilityAction(log) {
            actions.onLogInteraction(person.id)
            true
        },
        if (person.isDue) {
            CustomAccessibilityAction(snooze) {
                actions.onSnooze(person.id)
                true
            }
        } else {
            null
        },
    )
}
