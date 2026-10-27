package dev.alializadeh.orbit.person.dates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.ui.icons.OrbitIcons

@Composable
@ReadOnlyComposable
fun ImportantDateType.displayName(): String = stringResource(
    when (this) {
        ImportantDateType.Birthday -> R.string.important_date_type_birthday
        ImportantDateType.Anniversary -> R.string.important_date_type_anniversary
        ImportantDateType.Custom -> R.string.important_date_type_custom
    },
)

val ImportantDateType.icon: ImageVector
    get() = when (this) {
        ImportantDateType.Birthday -> OrbitIcons.Cake
        ImportantDateType.Anniversary -> Icons.Filled.Favorite
        ImportantDateType.Custom -> Icons.Filled.DateRange
    }

/** "Birthday", "Anniversary", or a Custom date's own label. */
@Composable
@ReadOnlyComposable
fun importantDateName(type: ImportantDateType, label: String?): String =
    if (type == ImportantDateType.Custom && !label.isNullOrBlank()) label else type.displayName()

/**
 * The years count of an occurrence (copy deck): "Turns 35" ("turns 35" when [inline], after a
 * separator), "5th anniversary", "5 years". Null when the year is unknown or it's the first time.
 */
@Composable
@ReadOnlyComposable
fun yearsCountText(type: ImportantDateType, years: Int?, inline: Boolean = false): String? {
    if (years == null || years < 1) return null
    val numbers = LocalOrbitFormats.current.numbers
    val resources = LocalResources.current
    return when (type) {
        ImportantDateType.Birthday -> resources.getString(
            if (inline) R.string.years_birthday_inline else R.string.years_birthday,
            numbers.format(years),
        )
        ImportantDateType.Anniversary -> resources.getString(R.string.years_anniversary, numbers.ordinal(years))
        ImportantDateType.Custom -> resources.getQuantityString(R.plurals.years_custom, years, numbers.format(years))
    }
}

/** A Birthday's current age: "35 years old" / «۳۵ ساله». Null under a year. */
@Composable
@ReadOnlyComposable
fun ageText(years: Int?): String? {
    if (years == null || years < 1) return null
    return LocalResources.current.getQuantityString(
        R.plurals.important_date_age,
        years,
        LocalOrbitFormats.current.numbers.format(years),
    )
}

/** Whose date it is: "Sara's birthday", "Sara's Graduation" / «تولد سارا». [name] is the displayed name. */
@Composable
@ReadOnlyComposable
fun dateTitle(name: String, type: ImportantDateType, label: String?): String = when (type) {
    ImportantDateType.Birthday -> stringResource(R.string.date_title_birthday, bidiIsolate(name))
    ImportantDateType.Anniversary -> stringResource(R.string.date_title_anniversary, bidiIsolate(name))
    ImportantDateType.Custom ->
        stringResource(R.string.date_title_custom, bidiIsolate(name), bidiIsolate(importantDateName(type, label)))
}

/** "Today is Sara's birthday" / «امروز تولد سارا است». */
@Composable
@ReadOnlyComposable
fun todayTitle(name: String, type: ImportantDateType, label: String?): String = when (type) {
    ImportantDateType.Birthday -> stringResource(R.string.reminder_title_birthday_today, bidiIsolate(name))
    ImportantDateType.Anniversary -> stringResource(R.string.reminder_title_anniversary_today, bidiIsolate(name))
    ImportantDateType.Custom ->
        stringResource(R.string.reminder_title_custom_today, bidiIsolate(name), bidiIsolate(importantDateName(type, label)))
}

/** "1 week before · on the day", longest first; "No reminders" for none. */
@Composable
@ReadOnlyComposable
fun leadTimesText(leadTimes: Set<LeadTime>): String {
    if (leadTimes.isEmpty()) return stringResource(R.string.important_date_reminders_none)
    val resources = LocalResources.current
    val numbers = LocalOrbitFormats.current.numbers
    fun before(plural: Int, n: Int) = resources.getQuantityString(plural, n, numbers.format(n))
    return leadTimes.sortedByDescending(LeadTime::ordinal).joinToString(resources.getString(R.string.separator)) {
        when (it) {
            LeadTime.OnTheDay -> resources.getString(R.string.lead_time_on_the_day)
            LeadTime.OneDay -> before(R.plurals.lead_time_days_before, 1)
            LeadTime.ThreeDays -> before(R.plurals.lead_time_days_before, 3)
            LeadTime.OneWeek -> before(R.plurals.lead_time_weeks_before, 1)
            LeadTime.TwoWeeks -> before(R.plurals.lead_time_weeks_before, 2)
            LeadTime.OneMonth -> before(R.plurals.lead_time_months_before, 1)
        }
    }
}

/**
 * The date sheet's Reminders line: "Default (1 week before · on the day)" with the actual default
 * [defaults], "Custom (…)" for a date's own lead times, or "No reminders".
 */
@Composable
@ReadOnlyComposable
fun remindersSummary(leadTimes: Set<LeadTime>?, defaults: Set<LeadTime>): String = when {
    leadTimes == null -> stringResource(R.string.important_date_reminders_default, leadTimesText(defaults))
    leadTimes.isEmpty() -> stringResource(R.string.important_date_reminders_none)
    else -> stringResource(R.string.important_date_reminders_custom, leadTimesText(leadTimes))
}

/** "default reminders", "custom reminders", "no reminders": the end of a form row. */
@Composable
@ReadOnlyComposable
fun remindersShort(leadTimes: Set<LeadTime>?): String = stringResource(
    when {
        leadTimes == null -> R.string.important_date_reminders_default_short
        leadTimes.isEmpty() -> R.string.important_date_reminders_none_short
        else -> R.string.important_date_reminders_custom_short
    },
)

/** "Gift ideas: Book, Scarf and 2 more" (two named, then list_and_more); null without ideas. */
@Composable
@ReadOnlyComposable
fun giftIdeasLine(ideas: List<String>): String? {
    if (ideas.isEmpty()) return null
    val resources = LocalResources.current
    val named = ideas.take(NAMED_IDEAS).joinToString(resources.getString(R.string.list_separator)) { bidiIsolate(it) }
    val rest = ideas.size - NAMED_IDEAS
    val list = if (rest > 0) {
        resources.getString(R.string.list_and_more, named, LocalOrbitFormats.current.numbers.format(rest))
    } else {
        named
    }
    return resources.getString(R.string.reminder_gift_ideas, list)
}

private const val NAMED_IDEAS = 2
