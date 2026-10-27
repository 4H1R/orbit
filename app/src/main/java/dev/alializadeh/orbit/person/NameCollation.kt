package dev.alializadeh.orbit.person

import android.icu.text.Collator
import dev.alializadeh.orbit.language.OrbitLanguage

/**
 * Compares names the way the UI language sorts them: Persian collation in Persian (پ after ب,
 * گ after ک, ی last), English otherwise.
 *
 * It uses Android's ICU collator, which carries CLDR's Persian rules. (The JDK's `java.text`
 * collator has none for `fa` and sorts پ چ ژ گ after و, so this is tested under Robolectric, whose
 * android-all carries ICU.)
 */
fun nameComparator(language: OrbitLanguage): Comparator<String> {
    val collator = Collator.getInstance(language.formattingLocale).apply {
        // Case and accents only break ties: "ali" sits with "Ali".
        strength = Collator.SECONDARY
    }
    return Comparator { a, b -> collator.compare(a, b) }
}

/** [items] sorted by their displayed name in [language], ties broken by [tieBreak]. */
fun <T> sortByDisplayName(
    items: List<T>,
    language: OrbitLanguage,
    names: (T) -> PersonNames,
    tieBreak: (T) -> Long,
): List<T> {
    val comparator = nameComparator(language)
    return items
        .map { it to names(it).displayName(language) }
        .sortedWith { (a, aName), (b, bName) ->
            comparator.compare(aName, bName).takeIf { it != 0 } ?: tieBreak(a).compareTo(tieBreak(b))
        }
        .map { it.first }
}
