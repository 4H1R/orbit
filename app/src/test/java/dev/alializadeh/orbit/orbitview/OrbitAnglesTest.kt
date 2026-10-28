package dev.alializadeh.orbit.orbitview

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stable, meaningless angles with an overlap nudge that never moves the People already placed. */
class OrbitAnglesTest {

    @Test
    fun theSamePersonAlwaysGetsTheSameAngle() {
        val first = OrbitAngles.place(listOf(3L, 1L, 2L), radius = 120f)
        val second = OrbitAngles.place(listOf(1L, 2L, 3L), radius = 120f)
        assertEquals(first, second)
    }

    @Test
    fun hashedFractionsSpreadEvenly() {
        val buckets = IntArray(10)
        for (id in 1L..10_000L) buckets[(OrbitAngles.fraction(id) * 10).toInt()]++
        buckets.forEach { assertTrue("bucket of $it", it in 900..1100) }
    }

    @Test
    fun addingSomeoneMovesNobodyElse() {
        val before = OrbitAngles.place((1L..40L).toList(), radius = 150f, wedgeHalfAngle = 0.3)
        val after = OrbitAngles.place((1L..41L).toList(), radius = 150f, wedgeHalfAngle = 0.3)
        before.forEach { (id, angle) -> assertEquals("person $id", angle, after.getValue(id), 0.0) }
    }

    @Test
    fun neighboursAreNudgedApartByAnAvatarWhenThereIsRoom() {
        val radius = 50f
        // Five Avatars need 210dp of the ~254dp left beside the label.
        val angles = OrbitAngles.place((1L..5L).toList(), radius, wedgeHalfAngle = 0.6)
        assertTrue(OrbitAngles.minGap(angles.values) >= PersonSize.Avatar.arc / radius - 1e-9)
    }

    @Test
    fun aCrowdedRingStillSpreadsItsDots() {
        val radius = 150f
        val angles = OrbitAngles.place((1L..50L).toList(), radius, wedgeHalfAngle = 0.4)
        // 50 Dots need 600dp of ~820dp; hashed angles alone would leave some on top of each other.
        assertTrue(OrbitAngles.minGap((1L..50L).map { OrbitAngles.fraction(it) * 2 * PI }) < PersonSize.Dot.arc / radius)
        assertTrue(OrbitAngles.minGap(angles.values) >= PersonSize.Dot.arc / radius - 1e-9)
    }

    @Test
    fun nobodySitsInTheLabelWedge() {
        val wedge = 0.5
        val angles = OrbitAngles.place((1L..200L).toList(), radius = 150f, wedgeHalfAngle = wedge)
        angles.values.forEach { assertFalse("angle $it", OrbitAngles.inWedge(it, wedge)) }
    }

    @Test
    fun addingSomeoneToAnyRingMovesNobodyElse() {
        val frame = OrbitFrame.fit(4, availableRadius = 198f)
        val seats = (1L..60L).map { OrbitAngles.Seat(it, (it % 4).toInt()) }
        val wedges = List(4) { 0.3 }
        val before = OrbitAngles.place(seats, frame.radii, frame.gap / 2, wedges)
        val after = OrbitAngles.place(seats + OrbitAngles.Seat(61, 1), frame.radii, frame.gap / 2, wedges)
        before.forEach { (id, angle) -> assertEquals("person $id", angle, after.getValue(id), 0.0) }
    }

    /**
     * About twenty People on four Rings in a phone-sized view, a few of them Due and drifting (the
     * `orbit_few` screenshot): Rings there are closer together than an Avatar is wide, yet no two
     * Avatars touch, on the same Ring or neighbouring ones.
     */
    @Test
    fun aFewPeopleNeverOverlapAcrossRings() {
        val frame = OrbitFrame.fit(4, availableRadius = 198f)
        assertTrue(frame.gap < PersonSize.Avatar.diameter)
        val drifts = mapOf(3L to 1f, 6L to 0.5f, 9L to 1f, 11L to 0.3f, 13L to 1f, 17L to 0.05f)
        val seats = orbitFewSeats().map { it.copy(drift = drifts[it.id] ?: 0f) }
        val angles = OrbitAngles.place(seats, frame.radii, frame.gap / 2, wedgeHalfAngles = List(4) { 0.35 })
        assertNoAvatarsTouch(seats, angles, frame) { it.drift }
    }

    /** With room to spare, where People sit doesn't depend on who's drifting: none ever touch. */
    @Test
    fun withRoomToSpareNobodyTouchesWhoeverDrifts() {
        val frame = OrbitFrame.fit(4, availableRadius = 198f)
        var id = 0L
        val seats = listOf(2, 3, 4, 2).flatMapIndexed { ring, count -> List(count) { OrbitAngles.Seat(++id, ring) } }
        val angles = OrbitAngles.place(seats, frame.radii, frame.gap / 2, wedgeHalfAngles = List(4) { 0.35 })
        val driftsNow = seats.map { it.copy(drift = 1f) }
        assertEquals(angles, OrbitAngles.place(driftsNow, frame.radii, frame.gap / 2, wedgeHalfAngles = List(4) { 0.35 }))
        for (drift in Drifts) assertNoAvatarsTouch(seats, angles, frame) { drift }
    }

    private fun orbitFewSeats(): List<OrbitAngles.Seat> {
        var id = 0L
        return listOf(4, 6, 7, 3).flatMapIndexed { ring, count -> List(count) { OrbitAngles.Seat(++id, ring) } }
    }

    private fun assertNoAvatarsTouch(seats: List<OrbitAngles.Seat>, angles: Map<Long, Double>, frame: OrbitFrame, drift: (OrbitAngles.Seat) -> Float) {
        for (a in seats) for (b in seats) {
            if (a.id >= b.id) continue
            val ra = frame.personRadius(a.ring, drift(a))
            val rb = frame.personRadius(b.ring, drift(b))
            val ax = ra * sin(angles.getValue(a.id))
            val ay = ra * cos(angles.getValue(a.id))
            val bx = rb * sin(angles.getValue(b.id))
            val by = rb * cos(angles.getValue(b.id))
            val distance = hypot(ax - bx, ay - by)
            assertTrue("${a.id} and ${b.id} are $distance dp apart", distance >= PersonSize.Avatar.diameter - 1e-6)
        }
    }

    private companion object {
        val Drifts = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    }

    @Test
    fun ringsFarEnoughApartDontLimitEachOther() {
        val radii = listOf(50f, 150f)
        assertEquals(0.0, OrbitAngles.separation(0, 1, radii, maxDrift = 20f, room = 42.0), 0.0)
        assertTrue(OrbitAngles.separation(0, 1, listOf(100f, 130f), maxDrift = 15f, room = 42.0) > 0.0)
        assertEquals(42.0 / 100, OrbitAngles.separation(0, 0, listOf(100f, 130f), maxDrift = 15f, room = 42.0), 1e-12)
    }

    @Test
    fun theSmallestGapWrapsAroundTheTop() {
        assertEquals(0.2, OrbitAngles.minGap(listOf(0.1, 2 * PI - 0.1, PI)), 1e-12)
        assertEquals(2 * PI, OrbitAngles.minGap(listOf(1.0)), 0.0)
    }

    @Test
    fun anglesAreClockwiseFromTheTopWithinOneTurn() {
        val angles = OrbitAngles.place((1L..50L).toList(), radius = 100f)
        angles.values.forEach { assertTrue(it >= 0.0 && it < 2 * PI) }
        assertEquals(0.0, OrbitAngles.distance(0.1, 2 * PI + 0.1), 1e-12)
        assertEquals(0.2, OrbitAngles.distance(2 * PI - 0.1, 0.1), 1e-12)
    }
}
