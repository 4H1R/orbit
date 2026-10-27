package dev.alializadeh.orbit.person.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class CropTransformTest {

    private val delta = 0.01f

    @Test
    fun atFirstTheShortSideFillsTheCircleAndTheCentreIsCropped() {
        val transform = CropTransform(imageWidth = 1200, imageHeight = 800, viewport = 400f)

        assertEquals(0.5f, transform.scale, delta)
        assertEquals(CropRect(left = 200f, top = 0f, size = 800f), transform.cropRect())
    }

    @Test
    fun dragsAreClampedSoTheImageAlwaysCoversTheCircle() {
        val start = CropTransform(imageWidth = 1200, imageHeight = 800, viewport = 400f)

        // Shown 600 × 400 under a 400 circle: 100 px of slack each side, none vertically.
        val dragged = start.transformed(panX = 500f, panY = 300f, zoomChange = 1f, centroidX = 0f, centroidY = 0f)

        assertEquals(100f, dragged.offsetX, delta)
        assertEquals(0f, dragged.offsetY, delta)
        assertEquals(CropRect(left = 0f, top = 0f, size = 800f), dragged.cropRect())
    }

    @Test
    fun pinchingZoomsAroundTheFingers() {
        val start = CropTransform(imageWidth = 1000, imageHeight = 1000, viewport = 500f)

        // Double the size around a point 100 px right of centre: that image point stays put.
        val zoomed = start.transformed(panX = 0f, panY = 0f, zoomChange = 2f, centroidX = 100f, centroidY = 0f)

        assertEquals(2f, zoomed.zoom, delta)
        assertEquals(-100f, zoomed.offsetX, delta)
        val crop = zoomed.cropRect()
        assertEquals(500f, crop.size, delta)
        assertEquals(350f, crop.left, delta)
        assertEquals(250f, crop.top, delta)
    }

    @Test
    fun zoomStaysBetweenFitAndTheMaximum() {
        val start = CropTransform(imageWidth = 800, imageHeight = 800, viewport = 400f)

        assertEquals(1f, start.transformed(0f, 0f, 0.2f, 0f, 0f).zoom, delta)
        assertEquals(CropTransform.MAX_ZOOM, start.transformed(0f, 0f, 50f, 0f, 0f).zoom, delta)
    }

    @Test
    fun aNewViewportKeepsTheSameCrop() {
        val zoomed = CropTransform(imageWidth = 1000, imageHeight = 1000, viewport = 500f)
            .transformed(panX = 0f, panY = 0f, zoomChange = 2f, centroidX = 100f, centroidY = 50f)

        val resized = zoomed.withViewport(300f)

        assertEquals(zoomed.cropRect().left, resized.cropRect().left, delta)
        assertEquals(zoomed.cropRect().top, resized.cropRect().top, delta)
        assertEquals(zoomed.cropRect().size, resized.cropRect().size, delta)
    }
}
