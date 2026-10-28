package dev.alializadeh.orbit.search

import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The search index: what matches, AND across fields, the four tiers and "why it matched". */
class SearchIndexTest {

    private val ali = SearchDocument(
        personId = 1,
        names = PersonNames("Ali Rezaei", otherScriptName = "علی رضایی", nickname = "Al"),
        tags = listOf("Gym", "Work"),
        canHelpWith = listOf("Photography"),
        likes = listOf("Jazz"),
        contactMethods = listOf(SearchContact(ContactType.Phone, "+98 912 345 6789")),
        notes = "Met at the photo club.",
    )
    private val sara = SearchDocument(
        personId = 2,
        names = PersonNames("Sara Ahmadi"),
        tags = listOf("Photo club"),
        dislikes = listOf("Loud music"),
        giftIdeas = listOf("Record player"),
        howWeMet = "University, through Ali",
        interactionNotes = listOf(
            InteractionNote(30, LocalDate.of(2026, 3, 12), "She's moving to Berlin next spring"),
            InteractionNote(20, LocalDate.of(2026, 1, 5), "Asked about Berlin flats"),
            InteractionNote(10, LocalDate.of(2025, 11, 2), "Berlin trip photos"),
        ),
    )
    private val khalil = SearchDocument(personId = 3, names = PersonNames("Khalil"), notes = "می‌روم به باشگاه")
    private val mina = SearchDocument(personId = 4, names = PersonNames("مینا", otherScriptName = "Mina"), likes = listOf("كتاب"))

    private val index = SearchIndex(listOf(ali, sara, khalil, mina))

    private fun ids(query: String) = index.search(query)!!.keys

    @Test
    fun aBlankQueryIsNoSearch() {
        assertNull(index.search(""))
        assertNull(index.search("   "))
    }

    @Test
    fun everyFieldIsSearched() {
        assertEquals(setOf(1L), ids("rezaei"))
        assertEquals(setOf(1L), ids("رضایی"))
        assertEquals(setOf(1L), ids("work"))
        assertEquals(setOf(1L), ids("jazz"))
        assertEquals(setOf(2L), ids("loud"))
        assertEquals(setOf(2L), ids("record"))
        assertEquals(setOf(2L), ids("university"))
        assertEquals(setOf(2L), ids("flats"))
        assertEquals(setOf(1L), ids("club."))
        assertEquals(setOf(4L), ids("کتاب"))
    }

    @Test
    fun matchingIsContainsAnywhere() {
        assertEquals(setOf(1L, 2L, 3L), ids("ali"))
        assertEquals(setOf(1L), ids("tograph"))
    }

    @Test
    fun wordsAreAndedAndMayMatchDifferentFields() {
        assertEquals(setOf(1L), ids("ali gym"))
        assertEquals(setOf(2L), ids("sara berlin"))
        assertTrue(ids("sara gym").isEmpty())
    }

    @Test
    fun zwnjAndArabicLettersDontMatter() {
        assertEquals(setOf(3L), ids("میروم"))
        assertEquals(setOf(3L), ids("می‌روم"))
        assertEquals(setOf(1L), ids("علي"))
        assertEquals(setOf(4L), ids("كتاب"))
    }

    @Test
    fun phoneDigitsMatchWhateverTheirPunctuationAndScript() {
        assertEquals(setOf(1L), ids("0912 345"))
        assertEquals(setOf(1L), ids("۰۹۱۲۳۴۵"))
        assertEquals(setOf(1L), ids("+98 912-345"))
        assertTrue(ids("0912 999").isEmpty())
        val match = index.search("09123456")!!.getValue(1).matches.single { it.field == SearchField.ContactMethod }
        assertEquals("912 345 6", match.text.substring(match.ranges.single()))
    }

    @Test
    fun tiersRankNamesThenTagsThenDetailsThenNotes() {
        val photo = index.search("photo")!!
        // Ali: Can help with (tier 2) beats his note; Sara: her Tag (tier 2).
        assertEquals(2, photo.getValue(1).rank)
        assertEquals(SearchField.CanHelpWith, photo.getValue(1).matches.first().field)
        assertEquals(2, photo.getValue(2).rank)

        val berlin = index.search("berlin")!!.getValue(2)
        assertEquals(4, berlin.rank)
        val music = index.search("music")!!.getValue(2)
        assertEquals(3, music.rank)
    }

    @Test
    fun aNameMatchAtAWordStartBeatsOneMidWord() {
        val hits = index.search("ali")!!
        assertEquals(0, hits.getValue(1).rank)
        assertEquals(1, hits.getValue(3).rank)
        // Sara matched only "through Ali" in How we met.
        assertEquals(3, hits.getValue(2).rank)
    }

    @Test
    fun interactionNotesShowTheNewestWithHowManyMore() {
        val match = index.search("berlin")!!.getValue(2).matches.single()
        assertEquals(SearchField.InteractionNote, match.field)
        assertEquals(LocalDate.of(2026, 3, 12), match.date)
        assertEquals(2, match.more)
        assertEquals("Berlin", match.text.substring(match.ranges.single()))
    }

    @Test
    fun theWhyLineIsLeftOutWhenTheDisplayedNameExplainsTheQuery() {
        val hit = index.search("ali")!!.getValue(1)
        val presentation = hit.presentation(SearchField.Name)
        assertNull(presentation.why)
        assertEquals("Ali", ali.names.name.substring(presentation.nameRanges.single()))
    }

    @Test
    fun theWhyLineExplainsWhatTheNameDoesnt() {
        val hit = index.search("ali gym")!!.getValue(1)
        val why = hit.presentation(SearchField.Name).why!!
        assertEquals(SearchField.Tag, why.field)
        assertEquals("Gym", why.text)

        // In Persian UI, Ali shows as «علی رضایی»: the Latin name is what matched.
        val persian = hit.presentation(ali.names.displayedField(OrbitLanguage.Persian))
        assertEquals(SearchField.Name, persian.why!!.field)
        assertTrue(persian.nameRanges.isEmpty())
    }

    @Test
    fun theWhyLineNamesTheNickname() {
        val hit = index.search("al")!!.getValue(1)
        // "Al" is in the name too: no line needed.
        assertNull(hit.presentation(SearchField.Name).why)
        val nickname = SearchIndex(listOf(ali.copy(names = PersonNames("Alexander", nickname = "Sasha")))).search("sasha")!!
        assertEquals(SearchField.Nickname, nickname.getValue(1).presentation(SearchField.Name).why!!.field)
    }

    @Test
    fun aSnippetStartsNearTheMatch() {
        val long = FieldMatch(
            field = SearchField.Notes,
            text = "We talked for hours about everything and then she said she is moving to Berlin\nnext spring",
            ranges = listOf(72..77),
            words = setOf(0),
            wordStart = true,
        )
        val snippet = long.snippet()
        assertTrue(snippet.text.startsWith("…"))
        assertEquals("Berlin", snippet.text.substring(snippet.ranges.single()))
        assertTrue(snippet.text.endsWith("Berlin next spring"))

        val short = long.copy(text = "Berlin", ranges = listOf(0..5))
        assertEquals(Snippet("Berlin", listOf(0..5)), short.snippet())
    }

    @Test
    fun aNewIndexSeesNewData() {
        assertTrue(ids("tennis").isEmpty())
        val rebuilt = SearchIndex(listOf(ali, sara.copy(likes = listOf("Tennis"))))
        assertEquals(setOf(2L), rebuilt.search("tennis")!!.keys)
    }
}
