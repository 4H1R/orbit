package dev.alializadeh.orbit.search

import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactType
import java.time.LocalDate
import kotlin.random.Random
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * A sanity check, not a benchmark: building the index for 500 People (notes, Likes, Tags, phones
 * and 20 noted Interactions each) and running a query per keystroke stay far inside a frame.
 * Prints the numbers; the bounds are loose, and only checked off CI: a shared runner (the Release
 * workflow) can miss a frame on a single query.
 */
class SearchPerformanceTest {

    private val random = Random(8)
    private val latin = listOf("Sara", "Ali", "Reza", "Mina", "Babak", "Laleh", "Omid", "Nazanin", "Kian", "Parisa", "Arash", "Elodie")
    private val persian = listOf("سارا", "علی", "رضا", "مینا", "بابک", "لاله", "امید", "نازنین", "کیان", "پریسا", "آرش", "می‌روم")
    private val words = listOf(
        "jazz", "photography", "berlin", "gym", "climbing", "coffee", "lawyer", "design", "moving", "birthday",
        "باشگاه", "کتاب", "عکاسی", "موسیقی", "سفر", "دانشگاه", "قهوه", "کوه", "فیلم", "آشپزی",
    )

    private fun sentence(n: Int) = List(n) { words.random(random) }.joinToString(" ")

    private fun people(count: Int) = List(count) { i ->
        SearchDocument(
            personId = i.toLong(),
            names = PersonNames("${latin.random(random)} ${latin.random(random)}son", otherScriptName = persian.random(random), nickname = latin.random(random)),
            tags = List(2) { words.random(random) },
            canHelpWith = List(2) { words.random(random) },
            likes = List(3) { words.random(random) },
            dislikes = List(2) { words.random(random) },
            giftIdeas = List(2) { sentence(3) },
            howWeMet = sentence(12),
            contactMethods = listOf(SearchContact(ContactType.Phone, "+98 912 ${100 + i % 900} ${1000 + i}"), SearchContact(ContactType.Email, "person$i@example.com")),
            notes = sentence(80),
            interactionNotes = List(20) { n -> InteractionNote(i * 100L + n, LocalDate.of(2026, 1, 1).plusDays(n.toLong()), sentence(25)) },
        )
    }

    @Test
    fun fiveHundredPeopleIndexAndSearchWellInsideAFrame() {
        val documents = people(500)
        // Warm up the JIT and the normaliser's cache.
        repeat(3) { SearchIndex(documents).search("warm up") }

        lateinit var index: SearchIndex
        val buildMs = List(5) { measureNanoTime { index = SearchIndex(documents) } / 1e6 }.min()

        val keystrokes = listOf("s", "sa", "sar", "sara", "b", "be", "ber", "berl", "berli", "berlin", "۰۹۱۲", "0912 345", "می‌روم", "ali gym", "عکاسی")
        repeat(3) { keystrokes.forEach { index.search(it) } }
        val perQueryMs = keystrokes.map { query -> List(5) { measureNanoTime { index.search(query) } / 1e6 }.min() }

        println(
            "SearchIndex, 500 people: build %.1f ms; per keystroke max %.2f ms, mean %.2f ms".format(
                buildMs,
                perQueryMs.max(),
                perQueryMs.average(),
            ),
        )
        assumeFalse("timing isn't checked on CI", System.getenv("CI") == "true")
        assertTrue("build took $buildMs ms", buildMs < 500)
        assertTrue("a query took ${perQueryMs.max()} ms", perQueryMs.max() < 16)
    }
}
