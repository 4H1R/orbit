package dev.alializadeh.orbit.wishes

import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.NameScript
import dev.alializadeh.orbit.person.PersonNames

/** What the Wishes are for, which picks the default text. */
sealed interface WishKind {
    /** An Important date of the Person. A Custom date's label can be anything, so its text is neutral. */
    data class Date(val type: ImportantDateType) : WishKind
}

/**
 * The language Wishes start in: the one written in the script of the Person's Name, or the UI's
 * when the Name has no letters to tell by.
 */
fun PersonNames.messageLanguage(uiLanguage: OrbitLanguage): OrbitLanguage {
    if (name.none(Char::isLetter)) return uiLanguage
    return when (NameScript.detect(name)) {
        NameScript.Latin -> OrbitLanguage.English
        NameScript.Persian -> OrbitLanguage.Persian
    }
}

/**
 * Whom the message greets, in [language]: the Nickname if it's written in that language's script,
 * else the first word of the name in that script (Name in other script when only it matches), else
 * the first word of the Name.
 */
fun PersonNames.nameForMessage(language: OrbitLanguage): String {
    val script = NameScript.of(language)
    nickname?.trim()?.takeIf { it.isNotEmpty() && NameScript.detect(it) == script }?.let { return it }
    return firstWord(displayName(language))
}

private fun firstWord(name: String): String = name.trim().split(WHITESPACE).firstOrNull { it.isNotEmpty() } ?: name.trim()

private val WHITESPACE = Regex("\\s+")
