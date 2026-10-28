package dev.alializadeh.orbit.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import java.time.LocalDate

/** How a match is highlighted: the primary colour in the emphasised (SemiBold) face. */
@Composable
fun matchStyle(): SpanStyle = SpanStyle(
    color = MaterialTheme.colorScheme.primary,
    fontFamily = MaterialTheme.typography.labelLargeEmphasized.fontFamily,
)

/** [text] with [ranges] highlighted. */
@Composable
fun highlighted(text: String, ranges: List<IntRange>): AnnotatedString {
    if (ranges.isEmpty()) return AnnotatedString(text)
    val style = matchStyle()
    return buildAnnotatedString {
        append(text)
        ranges.forEach { range ->
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            addStyle(style, start, end)
        }
    }
}

/**
 * A search result's "why it matched" line, in place of the row's secondary line:
 * *Can help with: **Photo**graphy*, *Phone: +98 **912 345** 6789*, or
 * *Interaction · 12 Mar: "…moving to **Berlin**…" · +2 more*.
 */
@Composable
fun WhyMatchedLine(match: FieldMatch, today: LocalDate, modifier: Modifier = Modifier) {
    val formats = LocalOrbitFormats.current
    val separator = stringResource(R.string.separator)
    val label = when (match.field) {
        SearchField.Name -> stringResource(R.string.person_form_name)
        SearchField.OtherScriptName -> stringResource(R.string.person_field_other_script_name)
        SearchField.Nickname -> stringResource(R.string.person_field_nickname)
        SearchField.Tag -> stringResource(R.string.person_section_tags)
        SearchField.CanHelpWith -> stringResource(R.string.person_section_can_help_with)
        SearchField.Like -> stringResource(R.string.person_section_likes)
        SearchField.Dislike -> stringResource(R.string.person_section_dislikes)
        SearchField.GiftIdea -> stringResource(R.string.person_section_gift_ideas)
        SearchField.HowWeMet -> stringResource(R.string.person_section_how_we_met)
        SearchField.ContactMethod -> stringResource(match.contactType?.nameRes ?: R.string.person_field_contact_method)
        SearchField.Notes -> stringResource(R.string.person_section_notes)
        SearchField.InteractionNote -> listOfNotNull(
            stringResource(R.string.search_why_interaction),
            match.date?.let { formats.dates.format(it, formats.primaryCalendar, today) },
        ).joinToString(separator)
    }
    val snippet = match.snippet()
    val value = highlighted(snippet.text, snippet.ranges)
    val shown = when (match.field) {
        // User text in a sentence is isolated; Contact values always read left to right.
        SearchField.ContactMethod -> isolated(value, LEFT_TO_RIGHT_ISOLATE)
        SearchField.InteractionNote -> annotatedStringResource(R.string.search_why_quoted, isolated(value, FIRST_STRONG_ISOLATE))
        else -> isolated(value, FIRST_STRONG_ISOLATE)
    }
    val line = annotatedStringResource(R.string.search_why_field, AnnotatedString(label), shown)
    Row(modifier) {
        Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (match.more > 0) {
            val more = LocalResources.current.getQuantityString(R.plurals.search_why_more, match.more, formats.numbers.format(match.more))
            Text(separator + more, maxLines = 1)
        }
    }
}

private fun isolated(value: AnnotatedString, isolate: Char): AnnotatedString = buildAnnotatedString {
    append(isolate)
    append(value)
    append(POP_DIRECTIONAL_ISOLATE)
}

/** A string resource with styled [args] in place of its `%1$s`, `%2$s`…, in whatever order it has them. */
@Composable
private fun annotatedStringResource(@StringRes id: Int, vararg args: AnnotatedString): AnnotatedString {
    val markers = args.indices.map { (MARKER_BASE + it).toString() }
    val template = stringResource(id, *markers.toTypedArray())
    return buildAnnotatedString {
        for (c in template) {
            val arg = c - MARKER_BASE
            if (arg in args.indices) append(args[arg]) else append(c)
        }
    }
}

/** Private-use characters standing in for the arguments while the resource is formatted. */
private const val MARKER_BASE = '\uE000'
private const val FIRST_STRONG_ISOLATE = '\u2068'
private const val LEFT_TO_RIGHT_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'
