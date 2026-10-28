package dev.alializadeh.orbit.person.dates

import android.content.res.Resources
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
import dev.alializadeh.orbit.format.OrbitNumberFormatter
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.ui.icons.OrbitIcons

// Each text has a plain version over Resources and the number formatter (notifications build
// their text outside composition) and a composable one for screens.

fun ImportantDateType.displayName(resources: Resources): String = resources.getString(
    when (this) {
        ImportantDateType.Birthday -> R.string.important_date_type_birthday
        ImportantDateType.Anniversary -> R.string.important_date_type_anniversary
        ImportantDateType.Custom -> R.string.important_date_type_custom
    },
)

@Composable
@ReadOnlyComposable
fun ImportantDateType.displayName(): String = displayName(LocalResources.current)

val ImportantDateType.icon: ImageVector
    get() = when (this) {
        ImportantDateType.Birthday -> OrbitIcons.Cake
        ImportantDateType.Anniversary -> Icons.Filled.Favorite
        ImportantDateType.Custom -> Icons.Filled.DateRange
    }

/** "Birthday", "Anniversary", or a Custom date's own label. */
fun importantDateName(resources: Resources, type: ImportantDateType, label: String?): String =
    if (type == ImportantDateType.Custom && !label.isNullOrBlank()) label else type.displayName(resources)

@Composable
@ReadOnlyComposable
fun importantDateName(type: ImportantDateType, label: String?): String = importantDateName(LocalResources.current, type, label)

/**
 * The years count of an occurrence (copy deck): "Turns 35" ("turns 35" when [inline], after a
 * separator), "5th anniversary", "5 years". Null when the year is unknown or it's the first time.
 */
fun yearsCountText(
    resources: Resources,
    numbers: OrbitNumberFormatter,
    type: ImportantDateType,
    years: Int?,
    inline: Boolean = false,
): String? {
    if (years == null || years < 1) return null
    return when (type) {
        ImportantDateType.Birthday -> resources.getString(
            if (inline) R.string.years_birthday_inline else R.string.years_birthday,
            numbers.format(years),
        )
        ImportantDateType.Anniversary -> resources.getString(R.string.years_anniversary, numbers.ordinal(years))
        ImportantDateType.Custom -> resources.getQuantityString(R.plurals.years_custom, years, numbers.format(years))
    }
}

@Composable
@ReadOnlyComposable
fun yearsCountText(type: ImportantDateType, years: Int?, inline: Boolean = false): String? =
    yearsCountText(LocalResources.current, LocalOrbitFormats.current.numbers, type, years, inline)

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
fun dateTitle(resources: Resources, name: String, type: ImportantDateType, label: String?): String = when (type) {
    ImportantDateType.Birthday -> resources.getString(R.string.date_title_birthday, bidiIsolate(name))
    ImportantDateType.Anniversary -> resources.getString(R.string.date_title_anniversary, bidiIsolate(name))
    ImportantDateType.Custom -> resources.getString(
        R.string.date_title_custom,
        bidiIsolate(name),
        bidiIsolate(importantDateName(resources, type, label)),
    )
}

@Composable
@ReadOnlyComposable
fun dateTitle(name: String, type: ImportantDateType, label: String?): String =
    dateTitle(LocalResources.current, name, type, label)

/** "Today is Sara's birthday" / «امروز تولد سارا است». */
fun todayTitle(resources: Resources, name: String, type: ImportantDateType, label: String?): String = when (type) {
    ImportantDateType.Birthday -> resources.getString(R.string.reminder_title_birthday_today, bidiIsolate(name))
    ImportantDateType.Anniversary -> resources.getString(R.string.reminder_title_anniversary_today, bidiIsolate(name))
    ImportantDateType.Custom -> resources.getString(
        R.string.reminder_title_custom_today,
        bidiIsolate(name),
        bidiIsolate(importantDateName(resources, type, label)),
    )
}

@Composable
@ReadOnlyComposable
fun todayTitle(name: String, type: ImportantDateType, label: String?): String =
    todayTitle(LocalResources.current, name, type, label)

/** "1 week before · on the day", longest first; "No reminders" for none. */
@Composable
@ReadOnlyComposable
fun leadTimesText(leadTimes: Set<LeadTime>): String =
    leadTimesText(LocalResources.current, LocalOrbitFormats.current.numbers, leadTimes)

fun leadTimesText(resources: Resources, numbers: OrbitNumberFormatter, leadTimes: Set<LeadTime>): String {
    if (leadTimes.isEmpty()) return resources.getString(R.string.important_date_reminders_none)
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

/** A lead-time chip: "On the day", "3 days", "1 week", "1 month". */
@Composable
@ReadOnlyComposable
fun leadTimeChipLabel(leadTime: LeadTime): String {
    val resources = LocalResources.current
    val numbers = LocalOrbitFormats.current.numbers
    fun count(plural: Int, n: Int) = resources.getQuantityString(plural, n, numbers.format(n))
    return when (leadTime) {
        LeadTime.OnTheDay -> resources.getString(R.string.lead_time_chip_on_the_day)
        LeadTime.OneDay -> count(R.plurals.lead_time_chip_days, 1)
        LeadTime.ThreeDays -> count(R.plurals.lead_time_chip_days, 3)
        LeadTime.OneWeek -> count(R.plurals.lead_time_chip_weeks, 1)
        LeadTime.TwoWeeks -> count(R.plurals.lead_time_chip_weeks, 2)
        LeadTime.OneMonth -> count(R.plurals.lead_time_chip_months, 1)
    }
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
fun giftIdeasLine(resources: Resources, numbers: OrbitNumberFormatter, ideas: List<String>): String? {
    if (ideas.isEmpty()) return null
    val named = ideas.take(NAMED_IDEAS).joinToString(resources.getString(R.string.list_separator)) { bidiIsolate(it) }
    val rest = ideas.size - NAMED_IDEAS
    val list = if (rest > 0) resources.getQuantityString(R.plurals.list_and_more, rest, named, numbers.format(rest)) else named
    return resources.getString(R.string.reminder_gift_ideas, list)
}

@Composable
@ReadOnlyComposable
fun giftIdeasLine(ideas: List<String>): String? =
    giftIdeasLine(LocalResources.current, LocalOrbitFormats.current.numbers, ideas)

private const val NAMED_IDEAS = 2
