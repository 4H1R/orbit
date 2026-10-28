package dev.alializadeh.orbit.orbitview

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Where around their Ring each Person sits. The angle means nothing: it's derived from the
 * Person's id, so it's stable, then nudged a little so neighbours don't overlap.
 *
 * People are placed oldest first (by id), each against only the People placed before them, so
 * adding someone never moves anyone else. The nudge takes the free spot nearest the hashed angle
 * with the room an Avatar needs, else a Small's, else a Dot's; when not even a Dot's room is left
 * the Person keeps their hashed angle and overlaps. A wedge at the top of each Ring is kept for its
 * label.
 */
object OrbitAngles {

    private const val TWO_PI = 2 * PI

    /** A stable, evenly spread fraction in [0, 1) from [id] and [salt] (SplitMix64). */
    fun fraction(id: Long, salt: Long = 0): Double {
        var z = id * -0x61c8864680b583ebL + salt * 0x632BE59BD9B4E019L
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    /**
     * Angles (radians, clockwise from the top, in [0, 2π)) for the People [ids] on a Ring of
     * [radius] dp, keeping the label wedge of [wedgeHalfAngle] either side of the top free.
     */
    fun place(ids: Collection<Long>, radius: Float, wedgeHalfAngle: Double = 0.0): Map<Long, Double> {
        val wedge = wedgeHalfAngle.coerceIn(0.0, PI * 0.45)
        val placed = SortedAngles()
        val result = HashMap<Long, Double>(ids.size * 2)
        val r = radius.toDouble().coerceAtLeast(1.0)
        for (id in ids.sorted()) {
            var base = fraction(id) * TWO_PI
            // Someone hashed into the label's wedge lands somewhere else around the Ring, evenly.
            if (inWedge(base, wedge)) base = wedge + fraction(id, salt = 1) * (TWO_PI - 2 * wedge)
            val angle = nudged(base, placed, r, wedge)
            placed.add(angle)
            result[id] = angle
        }
        return result
    }

    private fun nudged(base: Double, placed: SortedAngles, radius: Double, wedge: Double): Double {
        if (placed.isEmpty()) return base
        for (size in PersonSize.entries) {
            val separation = size.arc / radius
            val step = separation / 4
            val steps = ceil(PI / step).toInt()
            for (k in 0..steps) {
                for (sign in if (k == 0) intArrayOf(1) else intArrayOf(1, -1)) {
                    val candidate = normalise(base + sign * k * step)
                    if (!inWedge(candidate, wedge) && placed.minDistance(candidate) >= separation) return candidate
                }
            }
        }
        return base
    }

    /** Whether [angle] is within [wedge] of the top. */
    fun inWedge(angle: Double, wedge: Double): Boolean = wedge > 0 && distance(angle, 0.0) < wedge

    /** The smallest angle between two neighbours among [angles] (2π for one Person or none). */
    fun minGap(angles: Collection<Double>): Double {
        if (angles.size < 2) return TWO_PI
        val sorted = angles.map(::normalise).sorted()
        var gap = TWO_PI - sorted.last() + sorted.first()
        for (i in 1 until sorted.size) gap = minOf(gap, sorted[i] - sorted[i - 1])
        return gap
    }

    /** The shorter way round between two angles. */
    fun distance(a: Double, b: Double): Double {
        val d = abs(normalise(a) - normalise(b))
        return if (d > PI) TWO_PI - d else d
    }

    fun normalise(angle: Double): Double {
        val a = angle % TWO_PI
        return if (a < 0) a + TWO_PI else a
    }

    /** Placed angles kept sorted, for the nearest-neighbour check. */
    private class SortedAngles {
        private var values = DoubleArray(16)
        private var size = 0

        fun isEmpty() = size == 0

        fun add(angle: Double) {
            val at = insertionPoint(angle)
            if (size == values.size) values = values.copyOf(size * 2)
            System.arraycopy(values, at, values, at + 1, size - at)
            values[at] = angle
            size++
        }

        fun minDistance(angle: Double): Double {
            if (size == 0) return Double.MAX_VALUE
            val at = insertionPoint(angle)
            // The neighbours either side, wrapping around.
            val after = values[if (at == size) 0 else at]
            val before = values[if (at == 0) size - 1 else at - 1]
            return minOf(distance(angle, after), distance(angle, before))
        }

        private fun insertionPoint(angle: Double): Int {
            var lo = 0
            var hi = size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (values[mid] < angle) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}
