package dev.alializadeh.orbit.person.photo

import kotlin.math.min

/** A square in image pixels: what becomes the 768 px photo. */
data class CropRect(val left: Float, val top: Float, val size: Float)

/**
 * The crop screen's pan and zoom: an image of [imageWidth] × [imageHeight] pixels under a square
 * [viewport] (the circle's bounding box, in screen pixels).
 *
 * At [zoom] 1 the image just covers the viewport and is centred. [offsetX]/[offsetY] move the
 * image's centre away from the viewport's centre, in screen pixels. Every gesture result is
 * clamped so the image always covers the whole viewport: there's never an empty edge to crop.
 */
data class CropTransform(
    val imageWidth: Int,
    val imageHeight: Int,
    val viewport: Float,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    init {
        require(imageWidth > 0 && imageHeight > 0 && viewport > 0f) { "Empty image or viewport" }
    }

    /** Screen pixels per image pixel. */
    val scale: Float get() = viewport / min(imageWidth, imageHeight) * zoom

    /**
     * After a pinch of [zoomChange] around [centroidX]/[centroidY] (relative to the viewport's
     * centre) and a drag of [panX]/[panY]: the point under the fingers stays under them.
     */
    fun transformed(
        panX: Float,
        panY: Float,
        zoomChange: Float,
        centroidX: Float,
        centroidY: Float,
    ): CropTransform {
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val actualChange = newZoom / zoom
        return copy(
            zoom = newZoom,
            offsetX = centroidX - (centroidX - offsetX) * actualChange + panX,
            offsetY = centroidY - (centroidY - offsetY) * actualChange + panY,
        ).clamped()
    }

    /** For a new viewport size (rotation, resize), keeping the zoom. */
    fun withViewport(newViewport: Float): CropTransform {
        val ratio = newViewport / viewport
        return copy(viewport = newViewport, offsetX = offsetX * ratio, offsetY = offsetY * ratio).clamped()
    }

    fun clamped(): CropTransform {
        val maxX = (imageWidth * scale - viewport) / 2
        val maxY = (imageHeight * scale - viewport) / 2
        return copy(offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
    }

    /** The square of the image under the viewport. */
    fun cropRect(): CropRect {
        val size = (viewport / scale).coerceAtMost(min(imageWidth, imageHeight).toFloat())
        val centreX = imageWidth / 2f - offsetX / scale
        val centreY = imageHeight / 2f - offsetY / scale
        return CropRect(
            left = (centreX - size / 2).coerceIn(0f, imageWidth - size),
            top = (centreY - size / 2).coerceIn(0f, imageHeight - size),
            size = size,
        )
    }

    companion object {
        const val MAX_ZOOM = 5f
    }
}
