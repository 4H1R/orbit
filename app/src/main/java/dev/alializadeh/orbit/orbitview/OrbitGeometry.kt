package dev.alializadeh.orbit.orbitview

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

// The Orbit view's geometry (ticket 13), in dp and radians, pure so it's tested on the JVM.
// World coordinates have "You" at (0, 0); angles run clockwise from the top. Nothing is mirrored
// in RTL.

/**
 * How big a Person is drawn. Each Ring picks the largest that fits all its People around the circle
 * at the current zoom; Due and date-badged People never go below [Small].
 */
enum class PersonSize(
    /** Diameter in dp. */
    val diameter: Float,
    /** The room kept between two neighbours of this size, in dp. */
    val spacing: Float,
) {
    /** A photo or initials avatar. */
    Avatar(36f, 6f),

    /** A photo or a coloured disc. */
    Small(24f, 4f),

    /** A dot in the Person's colour. */
    Dot(10f, 2f),
    ;

    /** The arc one Person of this size needs on a Ring. */
    val arc: Float get() = diameter + spacing

    companion object {
        /**
         * The largest size that would fit [count] People evenly around a circle of [radius] at
         * [zoom], leaving [reservedArc] (screen dp, the Ring's label) free. Dots when nothing fits:
         * they overlap. The drawing also checks [forGap] on where they actually are.
         */
        fun forRing(count: Int, radius: Float, zoom: Float, reservedArc: Float = 0f): PersonSize {
            val arc = (2 * PI.toFloat() * radius * zoom - reservedArc).coerceAtLeast(0f)
            return entries.firstOrNull { count * it.arc <= arc } ?: Dot
        }

        /**
         * The largest size two neighbours [gap] dp apart (centre to centre, the closest pair on a
         * Ring at the current zoom) can be drawn at overlapping by at most [MAX_OVERLAP] of their
         * size; Dots when nothing fits.
         */
        fun forGap(gap: Float): PersonSize = entries.firstOrNull { gap >= it.diameter * (1 - MAX_OVERLAP) } ?: Dot

        /** How much two neighbours may overlap, as a share of their size, before the Ring steps down. */
        const val MAX_OVERLAP = 0.25f

        /** A Due or badged Person on a Ring drawn at [ringSize]: never below Small. */
        fun forPerson(ringSize: PersonSize, prominent: Boolean): PersonSize =
            if (prominent && ringSize == Dot) Small else ringSize
    }
}

/**
 * The Rings' radii around "You": equally spaced, innermost first, fitted so the outermost Ring plus
 * half a gap of drift plus an Avatar stays inside the window's shorter side.
 */
data class OrbitFrame(
    /** Each Ring's radius, innermost first. */
    val radii: List<Float>,
    /** The distance between two neighbouring Rings. */
    val gap: Float,
) {
    val ringCount: Int get() = radii.size

    /** The furthest a Person's centre can be: half a gap beyond the outermost Ring. */
    val outerRadius: Float get() = (radii.lastOrNull() ?: 0f) + gap / 2

    /**
     * Where a Person sits: on their Ring, pushed out by [drift] (0 to 1) × half the gap to the next
     * Ring. On the outermost Ring that's up to half a gap beyond it, so nobody ever appears to be in
     * another Ring.
     */
    fun personRadius(ringIndex: Int, drift: Float): Float = radii[ringIndex] + drift.coerceIn(0f, 1f) * gap / 2

    companion object {
        /** The "You" disc's radius. */
        const val YOU_RADIUS = 24f

        /** Room outside the furthest Person's centre: half an Avatar plus its badge. */
        const val EDGE_MARGIN = 24f

        /** The innermost Ring is at least this far out, so its Avatars clear "You". */
        const val MIN_INNER_RADIUS = YOU_RADIUS + 18f + 8f

        /** With room to spare, the innermost Ring sits a gap plus this from the centre. */
        private const val CENTRE_OFFSET = 12f

        /** [ringCount] Rings (1 to 6) in a circle of [availableRadius] (half the shorter side). */
        fun fit(ringCount: Int, availableRadius: Float): OrbitFrame {
            val n = max(1, ringCount)
            val usable = max(1f, availableRadius - EDGE_MARGIN)
            // inner = gap + offset, and inner + (n - 1) × gap + gap / 2 = usable.
            var gap = (usable - CENTRE_OFFSET) / (n + 0.5f)
            var inner = gap + CENTRE_OFFSET
            if (inner < MIN_INNER_RADIUS) {
                inner = min(MIN_INNER_RADIUS, usable)
                gap = if (n == 1) 2 * (usable - inner) else (usable - inner) / (n - 0.5f)
            }
            gap = max(gap, 1f)
            return OrbitFrame(radii = List(ringCount) { inner + it * gap }, gap = gap)
        }
    }
}

/**
 * What the user has zoomed and panned to: screen = centre + world × [zoom] + pan. 1× fits
 * everything; pan is only possible when zoomed in.
 */
data class OrbitViewport(val zoom: Float = MIN_ZOOM, val panX: Float = 0f, val panY: Float = 0f) {

    val isFit: Boolean get() = zoom <= MIN_ZOOM + FIT_TOLERANCE

    /** A world point (dp from "You") on screen, relative to the view's centre. */
    fun screenX(worldX: Float): Float = worldX * zoom + panX

    fun screenY(worldY: Float): Float = worldY * zoom + panY

    /** The world point under a screen point (relative to the view's centre). */
    fun worldX(screenX: Float): Float = (screenX - panX) / zoom

    fun worldY(screenY: Float): Float = (screenY - panY) / zoom

    /**
     * Zoomed by [factor] keeping the world point under [focusX], [focusY] (screen, from the centre)
     * where it is, then moved by [dragX], [dragY]; clamped to [bounds].
     */
    fun transformed(focusX: Float, focusY: Float, factor: Float, dragX: Float, dragY: Float, bounds: OrbitBounds): OrbitViewport {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val wx = worldX(focusX)
        val wy = worldY(focusY)
        return OrbitViewport(newZoom, focusX - wx * newZoom + dragX, focusY - wy * newZoom + dragY).clamped(bounds)
    }

    /** Zoomed to [target] with the world point under the focus staying put (double-tap, crowd tap). */
    fun zoomedTo(target: Float, focusX: Float, focusY: Float, bounds: OrbitBounds): OrbitViewport =
        transformed(focusX, focusY, target / zoom, 0f, 0f, bounds)

    /** Where double-tap at [focusX], [focusY] goes: 2× there from fit, otherwise back to fit. */
    fun doubleTapped(focusX: Float, focusY: Float, bounds: OrbitBounds): OrbitViewport =
        if (isFit) zoomedTo(STEP_ZOOM, focusX, focusY, bounds) else OrbitViewport()

    /** Pan kept so the drawing can't be dragged away; none at 1×. */
    fun clamped(bounds: OrbitBounds): OrbitViewport {
        val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val maxX = max(0f, bounds.contentRadius * z - bounds.halfWidth)
        val maxY = max(0f, bounds.contentRadius * z - bounds.halfHeight)
        // + 0f turns -0 into 0, so a fitted viewport equals OrbitViewport().
        return OrbitViewport(z, panX.coerceIn(-maxX, maxX) + 0f, panY.coerceIn(-maxY, maxY) + 0f)
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f

        /** Double-tap's zoom, and how much a tap on a crowd zooms in. */
        const val STEP_ZOOM = 2f

        private const val FIT_TOLERANCE = 0.01f
    }
}

/** The view's half-size and the drawing's fitted radius, in dp. */
data class OrbitBounds(val halfWidth: Float, val halfHeight: Float, val contentRadius: Float)
