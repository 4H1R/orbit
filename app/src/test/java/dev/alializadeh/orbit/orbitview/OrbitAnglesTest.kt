package dev.alializadeh.orbit.orbitview

import kotlin.math.PI
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
