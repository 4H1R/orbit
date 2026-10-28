package dev.alializadeh.orbit.orbitview

import kotlin.math.hypot

/** A Person where they're drawn, in screen dp. */
data class PlacedPerson(
    val id: Long,
    val x: Float,
    val y: Float,
    /** Half the drawn size. */
    val radius: Float,
    /** Dimmed by a filter. */
    val dimmed: Boolean = false,
)

/** What a tap or long-press at a point lands on. */
sealed interface OrbitHit {
    data object Nothing : OrbitHit

    data class Person(val id: Long) : OrbitHit

    /** Several People within the touch target: zoom in on them instead. */
    data object Crowd : OrbitHit
}

object OrbitHits {

    /** Half the 48dp touch target. */
    const val TARGET_RADIUS = 24f

    /**
     * What a tap at [x], [y] hits: the one Person whose centre is within the 48dp target, or a
     * crowd when there are several. While a filter dims some People, the matching ones win. With
     * [canZoom] false (already at 4×) a crowd resolves to the nearest Person.
     */
    fun hit(people: List<PlacedPerson>, x: Float, y: Float, canZoom: Boolean = true): OrbitHit {
        val near = people.filter { distance(it, x, y) <= maxOf(TARGET_RADIUS, it.radius) }
        val candidates = near.filterNot(PlacedPerson::dimmed).ifEmpty { near }
        return when {
            candidates.isEmpty() -> OrbitHit.Nothing
            candidates.size == 1 -> OrbitHit.Person(candidates.single().id)
            canZoom -> OrbitHit.Crowd
            else -> OrbitHit.Person(candidates.minBy { distance(it, x, y) }.id)
        }
    }

    private fun distance(person: PlacedPerson, x: Float, y: Float) = hypot(person.x - x, person.y - y)
}

/** A rectangle in screen dp. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun intersects(other: Box): Boolean = left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** Whether this box overlaps a circle at [cx], [cy] of [radius]. */
    fun intersectsCircle(cx: Float, cy: Float, radius: Float): Boolean {
        val nx = cx.coerceIn(left, right)
        val ny = cy.coerceIn(top, bottom)
        val dx = cx - nx
        val dy = cy - ny
        return dx * dx + dy * dy < radius * radius
    }
}

/** A name that could show under an Avatar, [width] × [height] dp, centred under it. */
data class NameCandidate(val person: PlacedPerson, val width: Float, val height: Float) {
    val box: Box
        get() {
            val top = person.y + person.radius + NAME_GAP
            return Box(person.x - width / 2, top, person.x + width / 2, top + height)
        }

    private companion object {
        const val NAME_GAP = 2f
    }
}

object OrbitNames {

    /**
     * Which [candidates] (Avatars, in priority order) get their name shown: a name shows only where
     * it doesn't collide with another Person, another shown name or an [obstacles] box (Ring
     * labels, "You"). Earlier candidates win.
     */
    fun visible(candidates: List<NameCandidate>, everyone: List<PlacedPerson>, obstacles: List<Box>): Set<Long> {
        val shown = ArrayList<Box>()
        val result = HashSet<Long>()
        for (candidate in candidates) {
            val box = candidate.box
            val collides = obstacles.any(box::intersects) ||
                shown.any(box::intersects) ||
                everyone.any { it.id != candidate.person.id && box.intersectsCircle(it.x, it.y, it.radius) }
            if (!collides) {
                shown += box
                result += candidate.person.id
            }
        }
        return result
    }
}
