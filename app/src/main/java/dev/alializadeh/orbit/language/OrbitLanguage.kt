package dev.alializadeh.orbit.language

import dev.alializadeh.orbit.calendar.CalendarSystem
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/** The two UI languages. English is LTR, Persian RTL. */
enum class OrbitLanguage(
    /** The per-app locale tag stored by AppCompat (matches the `values-fa` resources). */
    val tag: String,
    /** The locale for anything locale-sensitive Orbit doesn't own (week start, collation). */
    val formattingLocale: Locale,
) {
    English("en", Locale.forLanguageTag("en-GB")),

    /** Formats with `fa-IR`, not plain `fa`, so the week starts on Saturday. */
    Persian("fa", Locale.forLanguageTag("fa-IR")),
    ;

    /** Saturday in Persian (fa-IR), Monday in English (en-GB). */
    val firstDayOfWeek: DayOfWeek
        get() = WeekFields.of(formattingLocale).firstDayOfWeek

    /** The Primary calendar a first run in this language starts with. */
    val defaultCalendar: CalendarSystem
        get() = when (this) {
            English -> CalendarSystem.Gregorian
            Persian -> CalendarSystem.SolarHijri
        }

    companion object {
        /** The language for [locale]: Persian for any `fa` locale, English otherwise. */
        fun of(locale: Locale?): OrbitLanguage =
            if (locale?.language == Persian.tag) Persian else English
    }
}
