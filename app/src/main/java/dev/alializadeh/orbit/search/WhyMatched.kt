package dev.alializadeh.orbit.search

import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.PersonNames

/** Which name field the list shows for these [PersonNames] in [language]. */
fun PersonNames.displayedField(language: OrbitLanguage): SearchField =
    if (otherScriptName != null && displayName(language) == otherScriptName && otherScriptName != name) {
        SearchField.OtherScriptName
    } else {
        SearchField.Name
    }

/**
 * How a result row shows its match: the [nameRanges] to highlight in the displayed name, and the
 * "why it matched" line ([why]), when the displayed name doesn't explain every word of the query.
 */
data class MatchPresentation(val nameRanges: List<IntRange>, val why: FieldMatch?)

/**
 * The row's highlights and "why it matched" line when the list shows [displayed] as the name.
 * The line is the best match outside the displayed name that holds a word the name doesn't: a
 * Nickname, the name not shown, or a field from the other tiers.
 */
fun SearchHit.presentation(displayed: SearchField): MatchPresentation {
    val nameMatch = matches.firstOrNull { it.field == displayed }
    val explained = nameMatch?.words.orEmpty()
    val why = if (explained.size >= wordCount) {
        null
    } else {
        matches.firstOrNull { it.field != displayed && !explained.containsAll(it.words) }
    }
    return MatchPresentation(nameMatch?.ranges.orEmpty(), why)
}

/** Text cut to show its first match on one line: "…moving to Berlin next spring". */
data class Snippet(val text: String, val ranges: List<IntRange>)

/**
 * [FieldMatch.text] on one line, starting a word or two before the first match
 * (at a word boundary, with "…" in front); the end is left to the line's ellipsis.
 */
fun FieldMatch.snippet(lead: Int = SNIPPET_LEAD): Snippet {
    val flat = text.map { if (it == '\n' || it == '\r' || it == '\t') ' ' else it }.joinToString("")
    val first = ranges.firstOrNull()?.first ?: 0
    if (first <= lead) return Snippet(flat, ranges)
    var cut = first - lead
    val space = flat.indexOf(' ', cut)
    if (space in cut until first) cut = space + 1
    if (flat[cut].isLowSurrogate()) cut++
    val shift = ELLIPSIS.length - cut
    return Snippet(
        text = ELLIPSIS + flat.substring(cut),
        ranges = ranges.filter { it.first >= cut }.map { (it.first + shift)..(it.last + shift) },
    )
}

private const val SNIPPET_LEAD = 10
private const val ELLIPSIS = "…"
