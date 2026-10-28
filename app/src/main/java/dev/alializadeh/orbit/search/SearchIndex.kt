package dev.alializadeh.orbit.search

import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactType
import java.time.LocalDate

/** An Interaction's note, for search: the Person was part of Interaction [interactionId] on [date]. */
data class InteractionNote(val interactionId: Long, val date: LocalDate, val note: String)

/** A Contact method as search sees it. */
data class SearchContact(val type: ContactType, val value: String)

/** Everything one Person can be found by (ticket 12). Built from the repositories, never stored. */
data class SearchDocument(
    val personId: Long,
    val names: PersonNames,
    val tags: List<String> = emptyList(),
    val canHelpWith: List<String> = emptyList(),
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    /** The ideas' text, open and given. */
    val giftIdeas: List<String> = emptyList(),
    val howWeMet: String? = null,
    val contactMethods: List<SearchContact> = emptyList(),
    val notes: String? = null,
    /** The notes of the Interactions they were part of, newest first. */
    val interactionNotes: List<InteractionNote> = emptyList(),
)

/** What a match was found in, in its ranking tier (1 highest). */
enum class SearchField(val tier: Int) {
    Name(1),
    OtherScriptName(1),
    Nickname(1),
    Tag(2),
    CanHelpWith(2),
    Like(3),
    Dislike(3),
    GiftIdea(3),
    HowWeMet(3),
    ContactMethod(3),
    Notes(4),
    InteractionNote(4),
}

/**
 * One field that matched: its [text] as stored, the [ranges] of it to highlight, and which query
 * [words] (by index) it holds.
 */
data class FieldMatch(
    val field: SearchField,
    val text: String,
    val ranges: List<IntRange>,
    val words: Set<Int>,
    /** A match starts a word (only names rank by it). */
    val wordStart: Boolean,
    val contactType: ContactType? = null,
    /** An Interaction note's date. */
    val date: LocalDate? = null,
    /** An Interaction note: how many older Interaction notes of theirs matched too ("+N more"). */
    val more: Int = 0,
) {
    /** 0: a name, at a word start; 1: a name, mid-word; 2 to 4: tiers 2 to 4. */
    val rank: Int get() = if (this.field.tier == 1) (if (wordStart) 0 else 1) else this.field.tier
}

/** A Person who matches: their best [rank] and every field that matched, best first. */
data class SearchHit(val personId: Long, val rank: Int, val matches: List<FieldMatch>, val wordCount: Int)

/**
 * The in-memory search over every Person (ticket 11: no FTS, nothing persisted). Build one from
 * the repositories' data whenever it changes; [search] runs per keystroke.
 *
 * - Plain "contains" matching on [SearchText]-normalised text; a query's words are AND'ed, and each
 *   may match a different field ("ali gym" finds Ali tagged Gym).
 * - A query of digits and phone punctuation also looks for its digits in Contact methods,
 *   ignoring their punctuation ([SearchText.phoneDigits]).
 * - A hit's rank is its best field's: names (a word-start match beats a mid-word one), then Tags and
 *   Can help with, then Likes, Dislikes, Gift ideas, How we met and Contact methods, then Notes and
 *   Interaction notes. The list orders a rank by displayed name.
 * - Interaction notes make one match: the newest matching note, with how many more matched.
 */
class SearchIndex(documents: List<SearchDocument>) {

    private val entries: List<Entry> = documents.map(::entry)

    val size: Int get() = entries.size

    /** Everyone [query] finds, by Person id; null when there's no query (blank once normalised). */
    fun search(query: String): Map<Long, SearchHit>? {
        val words = SearchText.words(query)
        if (words.isEmpty()) return null
        val phone = SearchText.phoneDigits(query)
        val hits = HashMap<Long, SearchHit>()
        for (entry in entries) {
            entry.match(words, phone)?.let { hits[it.personId] = it }
        }
        return hits
    }

    private class Field(
        val field: SearchField,
        val mapped: MappedText,
        val contactType: ContactType? = null,
        val note: InteractionNote? = null,
    )

    /** A Contact method's digits, and where each came from in its value. */
    private class ContactDigits(val field: Field, val digits: String, val origins: IntArray)

    private class Entry(val personId: Long, val fields: List<Field>, val contacts: List<ContactDigits>)

    private fun entry(document: SearchDocument): Entry {
        val fields = ArrayList<Field>()
        fun add(field: SearchField, text: String?, contactType: ContactType? = null, note: InteractionNote? = null) {
            if (text.isNullOrBlank()) return
            fields += Field(field, SearchText.map(text), contactType, note)
        }
        with(document) {
            add(SearchField.Name, names.name)
            add(SearchField.OtherScriptName, names.otherScriptName)
            add(SearchField.Nickname, names.nickname)
            tags.forEach { add(SearchField.Tag, it) }
            canHelpWith.forEach { add(SearchField.CanHelpWith, it) }
            likes.forEach { add(SearchField.Like, it) }
            dislikes.forEach { add(SearchField.Dislike, it) }
            giftIdeas.forEach { add(SearchField.GiftIdea, it) }
            add(SearchField.HowWeMet, howWeMet)
            contactMethods.forEach { add(SearchField.ContactMethod, it.value, contactType = it.type) }
            add(SearchField.Notes, notes)
            interactionNotes.forEach { add(SearchField.InteractionNote, it.note, note = it) }
        }
        val contacts = fields.filter { it.field == SearchField.ContactMethod }.map { field ->
            val text = field.mapped.text
            val digits = StringBuilder()
            val origins = ArrayList<Int>()
            text.forEachIndexed { i, c ->
                if (c in '0'..'9') {
                    digits.append(c)
                    origins += i
                }
            }
            ContactDigits(field, digits.toString(), origins.toIntArray())
        }
        return Entry(document.personId, fields, contacts)
    }

    private class Found(val field: Field) {
        val ranges = ArrayList<IntRange>()
        val words = HashSet<Int>()
        var wordStart = false
    }

    private fun Entry.match(words: List<String>, phone: String?): SearchHit? {
        // First the cheap question: is every word somewhere (or the phone digits)?
        val allWords = words.all { word -> fields.any { it.mapped.text.contains(word) } }
        val phoneAt = if (phone == null) emptyList() else contacts.mapNotNull { contact -> contact.digits.indexOf(phone).takeIf { it >= 0 }?.let { contact to it } }
        if (!allWords && phoneAt.isEmpty()) return null

        // Then where: every field that holds a word, with up to MAX_RANGES highlights per word.
        // Interaction notes come newest first; only the newest matching one is shown, so the
        // others are only counted.
        val found = ArrayList<Found>()
        var newestNote: Found? = null
        var moreNotes = 0
        for (field in fields) {
            val isNote = field.field == SearchField.InteractionNote
            if (isNote && newestNote != null) {
                if (words.any { field.mapped.text.contains(it) }) moreNotes++
                continue
            }
            var f: Found? = null
            words.forEachIndexed { index, word ->
                val text = field.mapped.text
                var at = text.indexOf(word)
                var count = 0
                while (at >= 0 && count < MAX_RANGES) {
                    if (f == null) f = Found(field)
                    f.ranges += field.mapped.originalRange(at, at + word.length)
                    f.words += index
                    if (field.mapped.startsWord(at)) f.wordStart = true
                    count++
                    at = text.indexOf(word, at + word.length)
                }
            }
            val match = f ?: continue
            if (isNote) newestNote = match else found += match
        }
        for ((contact, at) in phoneAt) {
            val mapped = contact.field.mapped
            val start = contact.origins[at]
            val end = contact.origins[at + phone!!.length - 1] + 1
            val f = found.firstOrNull { it.field === contact.field } ?: Found(contact.field).also { found += it }
            f.ranges += mapped.originalRange(start, end)
            f.words += words.indices
            if (mapped.startsWord(start)) f.wordStart = true
        }
        val matches = found.mapTo(ArrayList()) { it.toMatch() }
        newestNote?.let { matches += it.toMatch(more = moreNotes) }
        matches.sortWith(
            compareBy<FieldMatch> { it.rank }
                .thenBy { !it.wordStart }
                .thenBy { it.field.ordinal },
        )
        return SearchHit(personId, matches.first().rank, matches, words.size)
    }

    private fun Found.toMatch(more: Int = 0) = FieldMatch(
        field = field.field,
        text = field.mapped.original,
        ranges = ranges.sortedBy(IntRange::first).merged(),
        words = words,
        wordStart = wordStart,
        contactType = field.contactType,
        date = field.note?.date,
        more = more,
    )
}

/** Highlights per word and field: a long note doesn't need every occurrence. */
private const val MAX_RANGES = 8

/** Overlapping or touching ranges (sorted by start) joined. */
private fun List<IntRange>.merged(): List<IntRange> {
    val out = ArrayList<IntRange>(size)
    for (range in this) {
        val last = out.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            out[out.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            out += range
        }
    }
    return out
}
