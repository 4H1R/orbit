package dev.alializadeh.orbit.person.list

import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.PeopleSort
import dev.alializadeh.orbit.search.SearchText
import java.util.Locale

/** What the People list draws, in order. */
sealed interface PeopleListItem {
    val key: Any

    /** A section header: a Ring's name (Ring sort) or a letter (Name sort). */
    data class Header(val label: String, private val section: Int) : PeopleListItem {
        override val key: Any get() = "header:$section"
    }

    /** A Person, the [index]th of [count] in their section (for the segmented shapes). */
    data class Person(val row: PersonRow, val index: Int, val count: Int, val section: String?) : PeopleListItem {
        override val key: Any get() = row.summary.id
    }

    /** "2 archived people match", at the end of search results. */
    data class ArchivedMatches(val count: Int) : PeopleListItem {
        override val key: Any get() = "archived-matches"
    }
}

/**
 * The list for [state] in [language], names compared with [compareNames] (`nameComparator`: Persian
 * collation in Persian UI):
 *
 * - With a query: by rank, then by displayed name; no headers; "N archived people match" last.
 * - Name: by displayed name, under letter headers ([nameInitial]).
 * - Ring: innermost first, then by name, under the Rings' names.
 * - Last Interaction: longest ago first, People with none first (by date added); no headers.
 * - Recently added: newest first; no headers.
 */
fun peopleListItems(
    state: PeopleUiState.Loaded,
    language: OrbitLanguage,
    compareNames: Comparator<String>,
): List<PeopleListItem> {
    val names = state.people.associate { it.summary.id to it.summary.names.displayName(language) }
    val byName = Comparator<PersonRow> { a, b -> compareNames.compare(names.getValue(a.summary.id), names.getValue(b.summary.id)) }
        .thenBy { it.summary.id }
    val sections: List<Pair<String?, List<PersonRow>>> = when {
        state.searching -> listOf(null to state.people.sortedWith(compareBy<PersonRow> { it.hit?.rank ?: Int.MAX_VALUE }.then(byName)))
        else -> when (state.sort) {
            PeopleSort.Name -> state.people.sortedWith(byName).sectionsBy { nameInitial(names.getValue(it.summary.id)) }
            PeopleSort.Ring -> state.people
                .sortedWith(compareBy<PersonRow> { it.summary.ringPosition }.then(byName))
                .sectionsBy { it.summary.ringName.orEmpty() }
            PeopleSort.LastInteraction -> listOf(
                null to state.people.sortedWith(
                    compareBy<PersonRow>(
                        { it.summary.keepInTouch.lastInteraction != null },
                        { it.summary.keepInTouch.lastInteraction },
                        { it.summary.addedAt },
                    ).then(byName),
                ),
            )
            PeopleSort.RecentlyAdded -> listOf(
                null to state.people.sortedWith(compareByDescending<PersonRow> { it.summary.addedAt }.thenByDescending { it.summary.id }),
            )
        }
    }
    val items = ArrayList<PeopleListItem>(state.people.size + sections.size + 1)
    sections.forEachIndexed { section, (label, rows) ->
        if (label != null) items += PeopleListItem.Header(label, section)
        rows.forEachIndexed { index, row -> items += PeopleListItem.Person(row, index, rows.size, label) }
    }
    if (state.archivedMatches > 0) items += PeopleListItem.ArchivedMatches(state.archivedMatches)
    return items
}

/** Consecutive runs of the same [label], in order (a label met again later starts a new run). */
private fun List<PersonRow>.sectionsBy(label: (PersonRow) -> String): List<Pair<String?, List<PersonRow>>> {
    val sections = ArrayList<Pair<String?, MutableList<PersonRow>>>()
    for (row in this) {
        val key = label(row)
        val last = sections.lastOrNull()
        if (last != null && last.first == key) last.second += row else sections += key to mutableListOf(row)
    }
    return sections
}

/**
 * A name's letter header: its first character with search's folding (É under E, آ under ا, ي under
 * ی), upper-cased; "#" when it doesn't start with a letter.
 */
fun nameInitial(name: String): String {
    val folded = SearchText.normalise(name).trimStart()
    if (folded.isEmpty()) return OTHER_INITIAL
    val first = folded.codePointAt(0)
    if (!Character.isLetter(first)) return OTHER_INITIAL
    return String(Character.toChars(first)).uppercase(Locale.ROOT)
}

private const val OTHER_INITIAL = "#"
