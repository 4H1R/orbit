package dev.alializadeh.orbit.search

import dev.alializadeh.orbit.format.Digits
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Text as search compares it (ticket 12), applied to the query and to everything searched alike.
 * Nothing normalised is ever stored: change these rules freely.
 *
 * - Persian/Arabic: ي and ى → ی, ك → ک, أ/إ/آ → ا (their hamza and madda are combining marks once
 *   decomposed), ة and ە → ه; harakat, tatweel and ZWNJ (and every other invisible format
 *   character) removed, so «می‌روم» matches «میروم».
 * - Latin: case- and accent-insensitive (decomposed, marks dropped, lower-cased).
 * - Digits: Persian and Arabic-Indic digits become ASCII.
 *
 * There's no transliteration between scripts.
 */
object SearchText {

    /** [text] normalised. */
    fun normalise(text: String): String = map(text).text

    /** [text] normalised, remembering where each normalised character came from (for highlights). */
    fun map(text: String): MappedText {
        val out = StringBuilder(text.length)
        var origins = IntArray(text.length)
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val folded = fold(codePoint)
            if (out.length + folded.length > origins.size) origins = origins.copyOf(maxOf(origins.size * 2, out.length + folded.length))
            for (c in folded) {
                origins[out.length] = i
                out.append(c)
            }
            i += Character.charCount(codePoint)
        }
        return MappedText(text, out.toString(), origins)
    }

    /** The words of a query, normalised: each must be found somewhere (AND), in any field. */
    fun words(query: String): List<String> {
        val words = LinkedHashSet<String>()
        val word = StringBuilder()
        for (c in normalise(query) + ' ') {
            if (!c.isWhitespace()) {
                word.append(c)
            } else if (word.isNotEmpty()) {
                words += word.toString()
                word.clear()
            }
        }
        return words.toList()
    }

    /**
     * The digits a phone-like query looks for in Contact methods, or null when the query isn't
     * made only of digits and phone punctuation (spaces, dashes, brackets, dots, a leading `+`).
     * The international `00` or `+` and a national leading `0` are dropped, so "0912 345" is found
     * in `+98 912 345 6789`. (Going the other way, "+98 912" in `0912…`, would need country codes.)
     */
    fun phoneDigits(query: String): String? {
        val text = normalise(query).trim()
        if (text.none(Char::isAsciiDigit)) return null
        val body = text.removePrefix("+")
        if (!body.all { it.isAsciiDigit() || it.isWhitespace() || it in PHONE_PUNCTUATION }) return null
        return body.filter(Char::isAsciiDigit).trimStart('0').takeIf(String::isNotEmpty)
    }

    private const val PHONE_PUNCTUATION = "-()[]./"
    private const val TATWEEL = 0x0640

    /** Lower-case ASCII, by code point. */
    private val ASCII: Array<String> = Array(0x80) { it.toChar().lowercaseChar().toString() }

    /** Folds of the non-ASCII code points met so far (the index is built off the main thread). */
    private val cache = ConcurrentHashMap<Int, String>()

    private fun fold(codePoint: Int): String =
        if (codePoint < 0x80) ASCII[codePoint] else cache.getOrPut(codePoint) { computeFold(codePoint) }

    private fun computeFold(codePoint: Int): String {
        val decomposed = Normalizer.normalize(String(Character.toChars(codePoint)), Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var i = 0
        while (i < decomposed.length) {
            val c = decomposed.codePointAt(i)
            i += Character.charCount(c)
            when (Character.getType(c).toByte()) {
                // Accents, harakat, hamza and madda above/below; ZWNJ, ZWJ and bidi marks.
                Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.FORMAT -> continue
            }
            if (c == TATWEEL) continue
            out.appendCodePoint(foldLetter(c))
        }
        return out.toString().lowercase(Locale.ROOT)
    }

    private fun foldLetter(c: Int): Int = when (c) {
        'ي'.code, 'ى'.code -> 'ی'.code
        'ك'.code -> 'ک'.code
        'ة'.code, 'ە'.code -> 'ه'.code
        else -> if (c <= Char.MAX_VALUE.code) Digits.toAscii(c.toChar()).code else c
    }
}

/** [original] normalised as [text], with where each of its characters came from. */
class MappedText internal constructor(
    val original: String,
    val text: String,
    private val origins: IntArray,
) {
    /** The range of [original] that produced `text[start until end]`. */
    fun originalRange(start: Int, end: Int): IntRange {
        val first = origins[start]
        val lastStart = origins[end - 1]
        return first until lastStart + Character.charCount(original.codePointAt(lastStart))
    }

    /** Whether a match at [index] starts a word: nothing before it, or no letter or digit. */
    fun startsWord(index: Int): Boolean = index == 0 || !text[index - 1].isLetterOrDigit()
}

private fun Char.isAsciiDigit() = this in '0'..'9'
