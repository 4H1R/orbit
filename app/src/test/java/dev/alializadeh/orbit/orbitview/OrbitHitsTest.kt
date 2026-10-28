package dev.alializadeh.orbit.orbitview

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tap and long-press with the 48dp crowd rule, and which names fit under Avatars. */
class OrbitHitsTest {

    private val sara = PlacedPerson(1, x = 0f, y = 0f, radius = 18f)
    private val reza = PlacedPerson(2, x = 60f, y = 0f, radius = 18f)
    private val mina = PlacedPerson(3, x = 75f, y = 10f, radius = 5f)

    @Test
    fun aTapOnOnePersonOpensThem() {
        assertEquals(OrbitHit.Person(1), OrbitHits.hit(listOf(sara, reza), 5f, 5f))
        // Anywhere within the 48dp target counts, even past a small drawing.
        assertEquals(OrbitHit.Person(3), OrbitHits.hit(listOf(sara, mina), 95f, 10f))
    }

    @Test
    fun aTapOnNobodyDoesNothing() {
        assertEquals(OrbitHit.Nothing, OrbitHits.hit(listOf(sara, reza), 30f, 60f))
    }

    @Test
    fun severalPeopleWithinTheTargetAreACrowdToZoomInOn() {
        assertEquals(OrbitHit.Crowd, OrbitHits.hit(listOf(sara, reza, mina), 66f, 4f))
    }

    @Test
    fun atFourTimesACrowdResolvesToTheNearest() {
        assertEquals(OrbitHit.Person(3), OrbitHits.hit(listOf(sara, reza, mina), 72f, 9f, canZoom = false))
    }

    @Test
    fun whileFilteringTheMatchingPeopleWin() {
        val dimmedReza = reza.copy(dimmed = true)
        assertEquals(OrbitHit.Person(3), OrbitHits.hit(listOf(dimmedReza, mina), 66f, 4f))
        // Only dimmed People there: they still open.
        assertEquals(OrbitHit.Person(2), OrbitHits.hit(listOf(sara, dimmedReza), 60f, 0f))
    }

    @Test
    fun namesShowOnlyWhereTheyDontCollide() {
        val alone = PlacedPerson(10, x = -100f, y = 0f, radius = 18f)
        val left = PlacedPerson(11, x = 0f, y = 0f, radius = 18f)
        val right = PlacedPerson(12, x = 30f, y = 0f, radius = 18f)
        val below = PlacedPerson(13, x = 100f, y = 0f, radius = 18f)
        val underneath = PlacedPerson(14, x = 100f, y = 40f, radius = 18f)
        val candidates = listOf(alone, left, right, below).map { NameCandidate(it, width = 40f, height = 14f) }
        val shown = OrbitNames.visible(candidates, everyone = listOf(alone, left, right, below, underneath), obstacles = emptyList())
        // Left's name comes first and blocks right's; below's runs into the Person underneath it.
        assertEquals(setOf(10L, 11L), shown)
    }

    @Test
    fun namesKeepClearOfTheRingLabels() {
        val person = PlacedPerson(1, x = 0f, y = 0f, radius = 18f)
        val label = Box(-30f, 25f, 30f, 40f)
        assertEquals(emptySet<Long>(), OrbitNames.visible(listOf(NameCandidate(person, 40f, 14f)), listOf(person), listOf(label)))
    }
}
