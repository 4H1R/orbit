package dev.alializadeh.orbit.person

import dev.alializadeh.orbit.language.OrbitLanguage

/** The script a name is written in. Persian covers the whole Arabic script. */
enum class NameScript {
    Latin,
    Persian,
    ;

    companion object {
        /** The script of the UI in [language]: Persian UI shows the Persian-script name. */
        fun of(language: OrbitLanguage): NameScript = when (language) {
            OrbitLanguage.English -> Latin
            OrbitLanguage.Persian -> Persian
        }

        /**
         * The script [text] is written in, by its letters: Persian when most of its letters are in
         * the Arabic script, Latin otherwise (including names in other scripts, and names without
         * letters). A tie goes to the first letter's script.
         */
        fun detect(text: String): NameScript {
            var arabic = 0
            var other = 0
            var first: NameScript? = null
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                if (Character.isLetter(codePoint)) {
                    val script = if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC) {
                        arabic++
                        Persian
                    } else {
                        other++
                        Latin
                    }
                    if (first == null) first = script
                }
                i += Character.charCount(codePoint)
            }
            return when {
                arabic > other -> Persian
                other > arabic -> Latin
                else -> first ?: Latin
            }
        }
    }
}

/**
 * A Person's names and which one to show. The displayed name is whichever of [name] and
 * [otherScriptName] is written in the UI's script ([name] when neither or both are); the other one
 * and the [nickname] make the subtitle.
 */
data class PersonNames(
    val name: String,
    val otherScriptName: String? = null,
    val nickname: String? = null,
) {
    fun displayName(language: OrbitLanguage): String = if (showsOtherScriptName(language)) otherScriptName!! else name

    /** The subtitle's parts, in order: the name not displayed, then the Nickname. */
    fun subtitleParts(language: OrbitLanguage): List<String> {
        val otherName = if (showsOtherScriptName(language)) name else otherScriptName
        return listOfNotNull(otherName?.takeIf(String::isNotBlank), nickname?.takeIf(String::isNotBlank))
    }

    private fun showsOtherScriptName(language: OrbitLanguage): Boolean {
        val other = otherScriptName?.takeIf(String::isNotBlank) ?: return false
        val uiScript = NameScript.of(language)
        return NameScript.detect(name) != uiScript && NameScript.detect(other) == uiScript
    }
}

val Person.names: PersonNames get() = PersonNames(name, otherScriptName, nickname)
