package dev.alializadeh.orbit.format

import dev.alializadeh.orbit.language.OrbitLanguage
import java.time.LocalTime

/**
 * Every number Orbit shows goes through here (never `"$n"`, `%d` or a `DateTimeFormatter`'s
 * digits): Persian digits in the Persian UI unless "Use Latin digits" is on, Latin digits in
 * English. Formatted numbers are passed to string resources as `%1$s`.
 */
class OrbitNumberFormatter(
    val language: OrbitLanguage,
    useLatinDigits: Boolean,
) {
    val usesPersianDigits: Boolean = language == OrbitLanguage.Persian && !useLatinDigits

    /** [n] in the UI's digits, without grouping separators (years read 1405, not 1,405). */
    fun format(n: Long): String = if (usesPersianDigits) Digits.toPersian(n.toString()) else n.toString()

    fun format(n: Int): String = format(n.toLong())

    /** [n] with at least two digits ("09"), for times. */
    fun twoDigits(n: Int): String = format(n.toLong()).let { if (n in 0..9) format(0) + it else it }

    /**
     * A time of day on the 24-hour clock in Orbit's digits: "09:00" / «۰۹:۰۰». Digits and the colon
     * keep their order inside Persian text by themselves (no isolate: an LTR isolate before «، » breaks it).
     */
    fun time(time: LocalTime): String = twoDigits(time.hour) + ":" + twoDigits(time.minute)

    /**
     * The ordinal of [n] (1 or more): "1st", "2nd", "11th", "21st" in English; spelled out in
     * Persian («اولین», «بیست‌ویکمین»), where the digit setting therefore doesn't apply.
     */
    fun ordinal(n: Int): String {
        require(n >= 1) { "No ordinal for $n" }
        return when (language) {
            OrbitLanguage.English -> n.toString() + englishOrdinalSuffix(n)
            // Beyond the words formatter's range (never reached by a years count) use digits.
            OrbitLanguage.Persian ->
                if (n <= PersianNumberWords.MAX) PersianNumberWords.ordinal(n) else format(n) + "\u200Cامین"
        }
    }

    private fun englishOrdinalSuffix(n: Int): String = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
}
