package dev.alializadeh.orbit.orbitview

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where around their Ring each Person sits. The angle means nothing: it's derived from the
 * Person's id, so it's stable, then nudged a little so neighbours don't overlap.
 *
 * People are placed oldest first (by id), each against only the People placed before them, so
 * adding someone never moves anyone else. The nudge takes the free spot nearest the hashed angle
 * with the room an Avatar needs, else a Small's, else a Dot's; when not even a Dot's room is left
 * the Person keeps their hashed angle and overlaps. "Free" counts the neighbouring Rings too: Rings
 * can be closer together than an Avatar is wide, and a Due Person drifts up to half a gap outward,
 * so People on neighbouring Rings keep the same room apart, wherever either has drifted. A wedge
 * at the top of each Ring is kept for its label.
 */
object OrbitAngles {

    private const val TWO_PI = 2 * PI

    /**
     * One Person to place: their id, their Ring's index (0 innermost) and how far they've drifted
     * now (0 to 1, see `OrbitFrame.personRadius`).
     */
    data class Seat(val id: Long, val ring: Int, val drift: Float = 0f)

    /** A stable, evenly spread fraction in [0, 1) from [id] and [salt] (SplitMix64). */
    fun fraction(id: Long, salt: Long = 0): Double {
        var z = id * -0x61c8864680b583ebL + salt * 0x632BE59BD9B4E019L
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    /**
     * Angles (radians, clockwise from the top, in [0, 2π)) for the People [ids] on a single Ring of
     * [radius] dp, keeping the label wedge of [wedgeHalfAngle] either side of the top free.
     */
    fun place(ids: Collection<Long>, radius: Float, wedgeHalfAngle: Double = 0.0): Map<Long, Double> =
        place(ids.map { Seat(it, 0) }, listOf(radius), maxDrift = 0f, wedgeHalfAngles = listOf(wedgeHalfAngle))

    /**
     * Angles for everyone in [seats] on Rings of [radii] (dp, innermost first), where a Due Person
     * may drift up to [maxDrift] dp outward, keeping each Ring's label wedge of [wedgeHalfAngles]
     * either side of the top free.
     *
     * Each size's room is looked for twice: first allowing for any drift (so the angle doesn't
     * depend on who's Due today), then, when the Rings are too crowded for that, around where the
     * People placed before actually are now.
     */
    fun place(seats: Collection<Seat>, radii: List<Float>, maxDrift: Float, wedgeHalfAngles: List<Double>): Map<Long, Double> {
        val placed = Array(radii.size) { SortedAngles() }
        val result = HashMap<Long, Double>(seats.size * 2)
        for (seat in seats.sortedBy(Seat::id)) {
            val ring = seat.ring.coerceIn(0, radii.lastIndex)
            val wedge = wedgeHalfAngles.getOrElse(ring) { 0.0 }.coerceIn(0.0, PI * 0.45)
            var base = fraction(seat.id) * TWO_PI
            // Someone hashed into the label's wedge lands somewhere else around the Ring, evenly.
            if (inWedge(base, wedge)) base = wedge + fraction(seat.id, salt = 1) * (TWO_PI - 2 * wedge)
            val radius = radii[ring] + seat.drift.coerceIn(0f, 1f) * maxDrift
            val angle = nudged(base, ring, radius.toDouble(), placed, radii, maxDrift, wedge)
            placed[ring].add(angle, radius.toDouble())
            result[seat.id] = angle
        }
        return result
    }

    private fun nudged(
        base: Double,
        ring: Int,
        radiusNow: Double,
        placed: Array<SortedAngles>,
        radii: List<Float>,
        maxDrift: Float,
        wedge: Double,
    ): Double {
        if (placed.all(SortedAngles::isEmpty)) return base
        val radius = radii[ring].toDouble().coerceAtLeast(1.0)
        for (size in PersonSize.entries) {
            val room = size.arc.toDouble()
            // How far apart in angle this Person must be from the People on each Ring (0: no limit).
            val apart = DoubleArray(radii.size) { other -> separation(ring, other, radii, maxDrift, room) }
            // Where they are now: only People within these angles can be that close.
            val window = DoubleArray(radii.size) { other ->
                if (other == ring) 2 * asin(min(1.0, room / (2 * radius))) else apart[other]
            }
            val step = room / radius / 4
            val steps = ceil(PI / step).toInt()
            for (anyDrift in booleanArrayOf(true, false)) {
                for (k in 0..steps) {
                    for (sign in if (k == 0) intArrayOf(1) else intArrayOf(1, -1)) {
                        val candidate = normalise(base + sign * k * step)
                        if (inWedge(candidate, wedge)) continue
                        val free = if (anyDrift) {
                            radii.indices.all { apart[it] <= 0.0 || placed[it].minDistance(candidate) >= apart[it] }
                        } else {
                            radii.indices.none { other ->
                                window[other] > 0.0 && placed[other].anyWithin(candidate, window[other]) { angle, r ->
                                    pointDistance(radiusNow, candidate, r, angle) < room
                                }
                            }
                        }
                        if (free) return candidate
                    }
                }
            }
        }
        return base
    }

    private fun pointDistance(r1: Double, a1: Double, r2: Double, a2: Double): Double =
        sqrt(max(0.0, r1 * r1 + r2 * r2 - 2 * r1 * r2 * cos(a1 - a2)))

    /**
     * The angle two People, on Rings [a] and [b], need between them to be [room] dp apart: along the
     * Ring when it's the same one; otherwise straight across, wherever each has drifted (0 to
     * [maxDrift] dp outward). 0 when their Rings are that far apart anyway.
     */
    internal fun separation(a: Int, b: Int, radii: List<Float>, maxDrift: Float, room: Double): Double {
        if (a == b) return room / radii[a].toDouble().coerceAtLeast(1.0)
        val ra = radii[a].toDouble()
        val rb = radii[b].toDouble()
        val drift = maxDrift.toDouble()
        if (closest(ra, ra + drift, rb, rb + drift, 0.0) >= room) return 0.0
        if (closest(ra, ra + drift, rb, rb + drift, PI) < room) return PI
        // The closest approach only grows with the angle between them: bisect.
        var lo = 0.0
        var hi = PI
        repeat(48) {
            val mid = (lo + hi) / 2
            if (closest(ra, ra + drift, rb, rb + drift, mid) >= room) hi = mid else lo = mid
        }
        return hi
    }

    /**
     * The closest two points can be when one is [a1]–[a2] from the centre along one ray and the
     * other [b1]–[b2] along a ray [angle] (0 to π) away.
     */
    private fun closest(a1: Double, a2: Double, b1: Double, b2: Double, angle: Double): Double {
        val c = cos(angle)
        fun d(r: Double, s: Double) = sqrt(max(0.0, r * r + s * s - 2 * r * s * c))
        // The squared distance is convex in (r, s) and has no minimum inside the box: check its edges.
        return minOf(
            d(a1, (a1 * c).coerceIn(b1, b2)),
            d(a2, (a2 * c).coerceIn(b1, b2)),
            d((b1 * c).coerceIn(a1, a2), b1),
            d((b2 * c).coerceIn(a1, a2), b2),
        )
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

    /** A Ring's placed angles kept sorted (with where each Person is now), for the neighbour checks. */
    private class SortedAngles {
        private var values = DoubleArray(16)
        private var radii = DoubleArray(16)
        private var size = 0

        fun isEmpty() = size == 0

        fun add(angle: Double, radius: Double) {
            val at = insertionPoint(angle)
            if (size == values.size) {
                values = values.copyOf(size * 2)
                radii = radii.copyOf(size * 2)
            }
            System.arraycopy(values, at, values, at + 1, size - at)
            System.arraycopy(radii, at, radii, at + 1, size - at)
            values[at] = angle
            radii[at] = radius
            size++
        }

        /** Whether anyone within [window] of [angle] (either way round) passes [test]. */
        fun anyWithin(angle: Double, window: Double, test: (angle: Double, radius: Double) -> Boolean): Boolean {
            if (size == 0) return false
            val at = insertionPoint(angle)
            // Walk outward both ways from the insertion point, wrapping, each Person at most once.
            for (i in 0 until size) {
                val index = (at + i) % size
                if (distance(angle, values[index]) > window) break
                if (test(values[index], radii[index])) return true
            }
            for (i in 1..size) {
                val index = ((at - i) % size + size) % size
                if (distance(angle, values[index]) > window) break
                if (test(values[index], radii[index])) return true
            }
            return false
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
