package dev.alializadeh.orbit.orbitview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ring radii, drift, the per-Ring size choice and the zoom/pan maths. */
class OrbitGeometryTest {

    @Test
    fun oneToSixRingsAreEquallySpacedAndFitWithTheirDrift() {
        for (count in 1..6) {
            for (available in listOf(150f, 196f, 300f)) {
                val frame = OrbitFrame.fit(count, available)
                assertEquals(count, frame.ringCount)
                frame.radii.zipWithNext().forEach { (a, b) -> assertEquals("$count rings", frame.gap, b - a, 1e-3f) }
                // Half a gap of drift past the outermost Ring, plus an Avatar and its badge, stays inside.
                assertEquals(available - OrbitFrame.EDGE_MARGIN, frame.outerRadius, 1e-3f)
                assertTrue("innermost clears You", frame.radii.first() >= OrbitFrame.MIN_INNER_RADIUS - 1e-3f)
            }
        }
    }

    @Test
    fun driftIsAtMostHalfTheGapToTheNextRing() {
        val frame = OrbitFrame.fit(4, 196f)
        assertEquals(frame.radii[1], frame.personRadius(1, 0f), 0f)
        assertEquals(frame.radii[1] + frame.gap / 4, frame.personRadius(1, 0.5f), 1e-4f)
        assertEquals(frame.radii[1] + frame.gap / 2, frame.personRadius(1, 1f), 1e-4f)
        // Capped: nobody reaches the next Ring.
        assertEquals(frame.radii[1] + frame.gap / 2, frame.personRadius(1, 3f), 1e-4f)
        // The outermost drifts half a gap beyond it.
        assertEquals(frame.outerRadius, frame.personRadius(3, 1f), 1e-4f)
    }

    @Test
    fun eachRingPicksTheLargestSizeThatFits() {
        // A circle of 100dp: 628dp around.
        assertEquals(PersonSize.Avatar, PersonSize.forRing(count = 14, radius = 100f, zoom = 1f))
        assertEquals(PersonSize.Small, PersonSize.forRing(count = 15, radius = 100f, zoom = 1f))
        assertEquals(PersonSize.Dot, PersonSize.forRing(count = 40, radius = 100f, zoom = 1f))
        // Zooming in makes room.
        assertEquals(PersonSize.Avatar, PersonSize.forRing(count = 40, radius = 100f, zoom = 3f))
        // Past even Dots, they stay Dots (and overlap).
        assertEquals(PersonSize.Dot, PersonSize.forRing(count = 500, radius = 100f, zoom = 1f))
        // The label's arc is kept free.
        assertEquals(PersonSize.Small, PersonSize.forRing(count = 14, radius = 100f, zoom = 1f, reservedArc = 60f))
    }

    @Test
    fun theClosestNeighboursDecideTheSize() {
        // Neighbours may overlap by a quarter of their size: Avatars from 27dp apart, Smalls from 18dp.
        assertEquals(PersonSize.Avatar, PersonSize.forGap(42f))
        assertEquals(PersonSize.Avatar, PersonSize.forGap(27.1f))
        assertEquals(PersonSize.Small, PersonSize.forGap(26.9f))
        assertEquals(PersonSize.Small, PersonSize.forGap(18.1f))
        assertEquals(PersonSize.Dot, PersonSize.forGap(17.9f))
        assertEquals(PersonSize.Dot, PersonSize.forGap(2f))
    }

    @Test
    fun dueAndBadgedPeopleAreNeverSmallerThanSmall() {
        assertEquals(PersonSize.Small, PersonSize.forPerson(PersonSize.Dot, prominent = true))
        assertEquals(PersonSize.Dot, PersonSize.forPerson(PersonSize.Dot, prominent = false))
        assertEquals(PersonSize.Avatar, PersonSize.forPerson(PersonSize.Avatar, prominent = true))
        assertEquals(PersonSize.Small, PersonSize.forPerson(PersonSize.Small, prominent = true))
    }

    private val bounds = OrbitBounds(halfWidth = 205f, halfHeight = 300f, contentRadius = 197f)

    @Test
    fun atFitThereIsNoPan() {
        val v = OrbitViewport().transformed(focusX = 0f, focusY = 0f, factor = 1f, dragX = 80f, dragY = -50f, bounds = bounds)
        assertEquals(OrbitViewport(), v)
    }

    @Test
    fun pinchKeepsThePointUnderTheFingersAndStaysWithinOneToFourTimes() {
        val v = OrbitViewport().transformed(focusX = 100f, focusY = 50f, factor = 2f, dragX = 0f, dragY = 0f, bounds = bounds)
        assertEquals(2f, v.zoom, 0f)
        // The world point that was under (100, 50) is still there.
        assertEquals(100f, v.screenX(100f), 1e-4f)
        assertEquals(50f, v.screenY(50f), 1e-4f)
        assertEquals(4f, v.transformed(0f, 0f, 10f, 0f, 0f, bounds).zoom, 0f)
        assertEquals(1f, v.transformed(0f, 0f, 0.1f, 0f, 0f, bounds).zoom, 0f)
    }

    @Test
    fun panIsClampedSoTheDrawingCantBeDraggedAway() {
        val v = OrbitViewport(zoom = 2f).transformed(0f, 0f, 1f, dragX = 5_000f, dragY = -5_000f, bounds = bounds)
        // At 2× the drawing reaches 394dp out: 189dp past the side, 94dp past the top and bottom.
        assertEquals(189f, v.panX, 1e-3f)
        assertEquals(-94f, v.panY, 1e-3f)
    }

    @Test
    fun doubleTapTogglesTwoTimesAtThatPointAndFit() {
        val zoomed = OrbitViewport().doubleTapped(focusX = 40f, focusY = -30f, bounds = bounds)
        assertEquals(2f, zoomed.zoom, 0f)
        assertEquals(40f, zoomed.screenX(40f), 1e-4f)
        assertEquals(-30f, zoomed.screenY(-30f), 1e-4f)
        assertEquals(OrbitViewport(), zoomed.doubleTapped(0f, 0f, bounds))
        assertEquals(OrbitViewport(), OrbitViewport(zoom = 3.3f, panX = 10f).doubleTapped(0f, 0f, bounds))
    }

    @Test
    fun screenAndWorldPointsRoundTrip() {
        val v = OrbitViewport(zoom = 2.5f, panX = -30f, panY = 12f)
        assertEquals(17f, v.worldX(v.screenX(17f)), 1e-4f)
        assertEquals(-9f, v.worldY(v.screenY(-9f)), 1e-4f)
    }
}
