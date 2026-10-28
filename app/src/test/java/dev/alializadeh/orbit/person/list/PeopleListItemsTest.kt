package dev.alializadeh.orbit.person.list

import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.PeopleSort
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.search.SearchHit
import dev.alializadeh.orbit.search.SearchText
import dev.alializadeh.orbit.testing.TestRings
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The list's order and headers for each sort, and for search results. */
class PeopleListItemsTest {

    private val today = LocalDate.of(2026, 10, 28)

    private fun row(
        id: Long,
        name: String,
        ring: dev.alializadeh.orbit.ring.Ring = TestRings.friends,
        addedDaysAgo: Long = 100,
        lastInteractionDaysAgo: Long? = null,
        rank: Int? = null,
    ): PersonRow {
        val keepInTouch = KeepInTouch(
            cadence = ring.cadence,
            addedOn = today.minusDays(addedDaysAgo),
            lastInteraction = lastInteractionDaysAgo?.let(today::minusDays),
        )
        return PersonRow(
            summary = PersonSummary(
                id = id,
                names = PersonNames(name),
                photoFileName = null,
                ringName = ring.name,
                keepInTouch = keepInTouch,
                ringPosition = ring.position,
                ringId = ring.id,
                addedAt = Instant.parse("2026-10-28T09:00:00Z").minusSeconds(addedDaysAgo * 86_400),
            ),
            photo = null,
            due = keepInTouch.on(today),
            hit = rank?.let { SearchHit(id, it, emptyList(), wordCount = 1) },
        )
    }

    private val sara = row(1, "Sara", TestRings.close, addedDaysAgo = 300, lastInteractionDaysAgo = 40)
    private val ali = row(2, "ali", TestRings.inner, addedDaysAgo = 10)
    private val babak = row(3, "Babak", TestRings.friends, addedDaysAgo = 200, lastInteractionDaysAgo = 3)
    private val elodie = row(4, "Élodie", TestRings.inner, addedDaysAgo = 50)
    private val amir = row(5, "Amir", TestRings.close, addedDaysAgo = 400, lastInteractionDaysAgo = 90)
    private val everyone = listOf(sara, ali, babak, elodie, amir)

    private fun items(sort: PeopleSort, people: List<PersonRow> = everyone, searching: Boolean = false, archivedMatches: Int = 0) =
        peopleListItems(
            PeopleUiState.Loaded(people, today = today, sort = sort, searching = searching, archivedMatches = archivedMatches),
            OrbitLanguage.English,
            // Like the collator: case and accents aside.
            compareBy(SearchText::normalise),
        )

    /** The items as text: headers in brackets, People by name. */
    private fun List<PeopleListItem>.text() = map {
        when (it) {
            is PeopleListItem.Header -> "[${it.label}]"
            is PeopleListItem.Person -> it.row.summary.names.name
            is PeopleListItem.ArchivedMatches -> "+${it.count} archived"
        }
    }

    @Test
    fun byNameUnderLetterHeaders() {
        assertEquals(
            listOf("[A]", "ali", "Amir", "[B]", "Babak", "[E]", "Élodie", "[S]", "Sara"),
            items(PeopleSort.Name).text(),
        )
    }

    @Test
    fun eachSectionIsItsOwnSegmentedGroup() {
        val people = items(PeopleSort.Name).filterIsInstance<PeopleListItem.Person>()
        assertEquals(listOf(0 to 2, 1 to 2, 0 to 1, 0 to 1, 0 to 1), people.map { it.index to it.count })
        assertEquals(listOf("A", "A", "B", "E", "S"), people.map { it.section })
    }

    @Test
    fun byRingInnermostFirstUnderTheRingsNames() {
        assertEquals(
            listOf("[Inner]", "ali", "Élodie", "[Close]", "Amir", "Sara", "[Friends]", "Babak"),
            items(PeopleSort.Ring).text(),
        )
    }

    @Test
    fun byLastInteractionLongestAgoFirstAndNoneBeforeThatByDateAdded() {
        // No Interaction: Élodie (added 50 days ago), then ali (10); then 90, 40 and 3 days ago.
        assertEquals(listOf("Élodie", "ali", "Amir", "Sara", "Babak"), items(PeopleSort.LastInteraction).text())
    }

    @Test
    fun byRecentlyAddedNewestFirst() {
        assertEquals(listOf("ali", "Élodie", "Babak", "Sara", "Amir"), items(PeopleSort.RecentlyAdded).text())
    }

    @Test
    fun searchResultsGoByRankThenNameWithoutHeaders() {
        val hits = listOf(
            sara.copy(hit = SearchHit(1, 4, emptyList(), 1)),
            babak.copy(hit = SearchHit(3, 2, emptyList(), 1)),
            amir.copy(hit = SearchHit(5, 0, emptyList(), 1)),
            ali.copy(hit = SearchHit(2, 2, emptyList(), 1)),
        )
        assertEquals(
            listOf("Amir", "ali", "Babak", "Sara", "+2 archived"),
            items(PeopleSort.Ring, hits, searching = true, archivedMatches = 2).text(),
        )
    }

    @Test
    fun initialsFoldLikeSearchAndGatherTheRest() {
        assertEquals("E", nameInitial("élodie"))
        assertEquals("ا", nameInitial("آرش"))
        assertEquals("ا", nameInitial("احمد"))
        assertEquals("ی", nameInitial("يوسف"))
        assertEquals("پ", nameInitial("پریا"))
        assertEquals("#", nameInitial("2Pac"))
        assertEquals("#", nameInitial("(Mum)"))
        assertEquals("#", nameInitial(""))
    }
}
