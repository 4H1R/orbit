package dev.alializadeh.orbit.format

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.language.OrbitLanguage

/**
 * The number and date formatters for the current UI language and digit setting, and the Primary
 * calendar dates are shown in.
 */
class OrbitFormats(
    language: OrbitLanguage,
    useLatinDigits: Boolean,
    val primaryCalendar: CalendarSystem = language.defaultCalendar,
) {
    val numbers = OrbitNumberFormatter(language, useLatinDigits)
    val dates = OrbitDateFormatter(numbers)
    val language: OrbitLanguage get() = numbers.language
}

/** Provided by `OrbitTheme` from the UI locale and the "Use Latin digits" and Primary calendar preferences. */
val LocalOrbitFormats = staticCompositionLocalOf<OrbitFormats> {
    error("No OrbitFormats: wrap the content in OrbitTheme")
}

/** "Today", "Tomorrow", "In 5 days" / «امروز», «فردا», «۵ روز دیگر». */
fun RelativeAhead.text(resources: Resources, numbers: OrbitNumberFormatter): String = when (this) {
    RelativeAhead.Today -> resources.getString(R.string.time_today)
    RelativeAhead.Tomorrow -> resources.getString(R.string.time_tomorrow)
    is RelativeAhead.InDays -> resources.getQuantityString(R.plurals.time_in_days, days, numbers.format(days))
}

/** "today", "yesterday", "34 days ago", "3 months ago", "2 years ago", for use inside a sentence. */
fun RelativeAgo.text(resources: Resources, numbers: OrbitNumberFormatter): String = when (this) {
    RelativeAgo.Today -> resources.getString(R.string.time_today_inline)
    RelativeAgo.Yesterday -> resources.getString(R.string.time_yesterday)
    is RelativeAgo.DaysAgo -> resources.getQuantityString(R.plurals.time_days_ago, days, numbers.format(days))
    is RelativeAgo.MonthsAgo ->
        resources.getQuantityString(R.plurals.time_months_ago, months, numbers.format(months))
    is RelativeAgo.YearsAgo -> resources.getQuantityString(R.plurals.time_years_ago, years, numbers.format(years))
}

@Composable
@ReadOnlyComposable
fun RelativeAhead.text(): String = text(LocalResources.current, LocalOrbitFormats.current.numbers)

@Composable
@ReadOnlyComposable
fun RelativeAgo.text(): String = text(LocalResources.current, LocalOrbitFormats.current.numbers)
