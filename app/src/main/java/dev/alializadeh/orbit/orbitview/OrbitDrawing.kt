package dev.alializadeh.orbit.orbitview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.person.DateSoon
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.nameComparator
import dev.alializadeh.orbit.person.ui.AvatarColours
import dev.alializadeh.orbit.ui.expressive.LocalStillMotion
import dev.alializadeh.orbit.ui.theme.isDarkScheme
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the drawing does with a Person: tap, long-press, and TalkBack's custom actions. */
class OrbitPersonActions(
    val onOpen: (personId: Long) -> Unit,
    val onQuickActions: (personId: Long) -> Unit,
    val onContactAction: (ContactAction) -> Unit,
    val onLogInteraction: (personId: Long) -> Unit,
    val onSnooze: (personId: Long) -> Unit,
)

/** Where the view opens: always fit, except in screenshot tests of the zoomed view. */
internal val LocalOrbitStartViewport = staticCompositionLocalOf { OrbitViewport() }

/** Room left around the drawing, in dp. */
private const val OUTER_PADDING = 8f

/**
 * The Orbit view itself (ticket 13): "You" at the centre, the Rings with their labels, and every
 * Person on their Ring at their stable angle, drifting out and glowing when Due, with the apricot
 * badge for an Important date soon. Pinch zooms 1–4× (pan only when zoomed), double-tap toggles
 * 2×/fit, tap opens a Person (or zooms in on a crowd) and long-press opens the quick actions.
 *
 * It's drawn on three canvases (Rings, glows, People) so the glow's pulse redraws only its own
 * layer, plus invisible accessibility nodes Ring by Ring. [opening] changes each time the tab is
 * opened: the view goes back to fit and the People spring out from "You". With [LocalStillMotion]
 * ("Remove animations") nothing moves: no entrance, no pulse, no transitions.
 */
@Composable
fun OrbitDrawing(
    rings: List<OrbitRing>,
    people: List<OrbitPerson>,
    today: LocalDate,
    opening: Int,
    actions: OrbitPersonActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val available = min(widthDp, heightDp) / 2 - OUTER_PADDING
        val frame = remember(rings.size, available) { OrbitFrame.fit(rings.size, available) }
        val bounds = OrbitBounds(widthDp / 2, heightDp / 2, available + OUTER_PADDING)
        OrbitCanvas(rings, people, today, opening, actions, frame, bounds)
    }
}

/** Per Person: where around the Ring, and how they're coloured. */
private class OrbitItem(
    val person: OrbitPerson,
    /** The unit vector from "You" (y down). */
    val dx: Float,
    val dy: Float,
    val container: Color,
    val content: Color,
    /** Their colour as a Small disc or a Dot. */
    val accent: Color,
    val initial: String,
    val label: String,
)

/** A Person's animated values: drift back onto the Ring, glow, and dimming. */
private class PersonMotion(drift: Float, glow: Float, alpha: Float) {
    val drift = Animatable(drift)
    val glow = Animatable(glow)
    val alpha = Animatable(alpha)
}

/** A Ring's label, plain and with its count (shown while the Ring is drawn as Dots). */
private class RingLabel(val plain: TextLayoutResult, val counted: TextLayoutResult)

/** How each Ring is drawn at one zoom. */
private class RingLook(val sizes: Array<PersonSize>, val counted: BooleanArray)

/** How long the entrance waits for the tab's cross-fade. */
private const val ENTRANCE_DELAY_MS = 220L

/** The opacity of a Person dimmed by a filter. */
private const val DIMMED_ALPHA = 0.22f

private val ViewportSaver = listSaver<OrbitViewport, Float>(
    save = { listOf(it.zoom, it.panX, it.panY) },
    restore = { OrbitViewport(it[0], it[1], it[2]) },
)

@Composable
private fun OrbitCanvas(
    rings: List<OrbitRing>,
    people: List<OrbitPerson>,
    today: LocalDate,
    opening: Int,
    actions: OrbitPersonActions,
    frame: OrbitFrame,
    bounds: OrbitBounds,
) {
    val still = LocalStillMotion.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val resources = LocalResources.current
    val formats = LocalOrbitFormats.current
    val language = formats.language
    val dark = colors.isDarkScheme()
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val fontScale = density.fontScale

    // Text: ring labels and names scale with the font size up to a cap; "You" and initials fit their disc.
    val labelStyle = typography.labelMedium.capped(fontScale, 1.3f).copy(textDirection = TextDirection.Content)
    val nameStyle = typography.labelSmall.capped(fontScale, 1.3f).copy(textDirection = TextDirection.Content)
    val initialStyle = typography.titleSmall.capped(fontScale, 1f)
    val youStyle = typography.labelLarge.capped(fontScale, 1f)

    val counts = remember(people, rings.size) { IntArray(rings.size).also { c -> people.forEach { c[it.ringIndex]++ } } }
    val ringLabels = remember(rings, counts.toList(), labelStyle, language) {
        rings.map { ring ->
            val count = formats.numbers.format(counts[ring.index])
            RingLabel(
                plain = measurer.measure(ring.name, labelStyle, maxLines = 1),
                counted = measurer.measure(resources.getString(R.string.orbit_ring_count, ring.name, count), labelStyle, maxLines = 1),
            )
        }
    }
    val you = remember(youStyle, language) { measurer.measure(resources.getString(R.string.orbit_center_you), youStyle, maxLines = 1) }
    val pillPadX = 8f
    val pillPadY = 2f
    fun pillWidth(layout: TextLayoutResult) = layout.size.width / density.density + 2 * pillPadX
    fun pillHeight(layout: TextLayoutResult) = layout.size.height / density.density + 2 * pillPadY

    /** Each Ring's size and label at [zoom] were its People spread evenly: sizes the label wedges. */
    fun evenLook(zoom: Float): RingLook {
        val sizes = Array(rings.size) { PersonSize.Avatar }
        val counted = BooleanArray(rings.size)
        for (i in rings.indices) {
            val label = ringLabels[i]
            var size = PersonSize.forRing(counts[i], frame.radii[i], zoom, pillWidth(label.plain))
            if (size == PersonSize.Dot) {
                counted[i] = true
                size = PersonSize.forRing(counts[i], frame.radii[i], zoom, pillWidth(label.counted))
            }
            sizes[i] = size
        }
        return RingLook(sizes, counted)
    }

    // Angles: stable, from each Person's id, nudged apart (from their own and the neighbouring
    // Rings' People, allowing for drift), with each label's wedge at the top kept free (sized for
    // the label and the People's size at fit).
    val fitLook = remember(frame, ringLabels) { evenLook(1f) }
    val wedges = remember(frame, fitLook) {
        rings.indices.map { i ->
            val label = if (fitLook.counted[i]) ringLabels[i].counted else ringLabels[i].plain
            // Due and badged People are never below Small, so the wedge leaves room for one.
            val person = maxOf(fitLook.sizes[i].diameter, PersonSize.Small.diameter)
            ((pillWidth(label) / 2 + 3f + person / 2) / frame.radii[i]).toDouble()
        }
    }
    val placementKey = remember(people) { people.map { OrbitAngles.Seat(it.id, it.ringIndex, it.drift) } }
    val angles = remember(placementKey, frame, wedges) { OrbitAngles.place(placementKey, frame.radii, frame.gap / 2, wedges) }
    // The closest two neighbours on each Ring (dp at 1×) decide its size at any zoom.
    val ringGaps = remember(angles, frame) {
        val byRing = placementKey.groupBy({ it.ring }, { angles.getValue(it.id) })
        FloatArray(rings.size) { i -> (OrbitAngles.minGap(byRing[i].orEmpty()) * frame.radii[i]).toFloat() }
    }

    /**
     * Each Ring's size at [zoom]: the largest that fits its People around the circle, as long as its
     * closest two neighbours don't overlap much at it. A Ring of Dots shows its count.
     */
    fun ringLook(zoom: Float): RingLook {
        val even = evenLook(zoom)
        // maxOf is the smaller of the two: PersonSize runs largest first.
        val sizes = Array(rings.size) { maxOf(even.sizes[it], PersonSize.forGap(ringGaps[it] * zoom)) }
        return RingLook(sizes, BooleanArray(rings.size) { sizes[it] == PersonSize.Dot })
    }

    val items = remember(people, angles, dark, language) {
        people.map { person ->
            val angle = angles[person.id] ?: 0.0
            val name = person.names.displayName(language)
            val avatar = AvatarColours.of(name, dark)
            val hue = AvatarColours.hueIndex(name) * 30f + 15f
            OrbitItem(
                person = person,
                dx = sin(angle).toFloat(),
                dy = -cos(angle).toFloat(),
                container = avatar.container,
                content = avatar.content,
                accent = Color.hsl(hue, if (dark) 0.36f else 0.42f, if (dark) 0.66f else 0.62f),
                initial = AvatarColours.initial(name),
                label = person.names.orbitLabel(language),
            )
        }
    }
    // Dimmed first, then the matching; within each, Due and badged People on top.
    val drawOrder = remember(items) { items.sortedWith(compareBy({ it.person.matches }, { it.person.isProminent })) }

    // Motion: the entrance once per opening, each Person's drift/glow/dimming, the glow's pulse.
    var shownOpening by rememberSaveable { mutableIntStateOf(-1) }
    val entrance = remember { Animatable(if (shownOpening != opening && !still) 0f else 1f) }
    val startViewport = LocalOrbitStartViewport.current
    var viewport by rememberSaveable(stateSaver = ViewportSaver) { mutableStateOf(startViewport) }
    val entranceSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val moveSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val viewportSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(opening, still) {
        if (shownOpening != opening) {
            shownOpening = opening
            viewport = startViewport.clamped(bounds)
            if (still) {
                entrance.snapTo(1f)
            } else {
                entrance.snapTo(0f)
                // Past the tab's cross-fade, so the spring out from "You" is seen.
                delay(ENTRANCE_DELAY_MS)
                entrance.animateTo(1f, entranceSpec)
            }
        } else if (still) {
            entrance.snapTo(1f)
        }
    }
    LaunchedEffect(bounds) { viewport = viewport.clamped(bounds) }

    val motions = remember { HashMap<Long, PersonMotion>() }
    fun glowTarget(person: OrbitPerson) = if (person.isDue) 1f else 0f
    fun alphaTarget(person: OrbitPerson) = if (person.matches) 1f else DIMMED_ALPHA
    LaunchedEffect(people, still) {
        motions.keys.retainAll(people.mapTo(HashSet(), OrbitPerson::id))
        for (person in people) {
            // Someone new starts where they belong; a change (an Interaction, a Snooze) animates.
            val motion = motions.getOrPut(person.id) { PersonMotion(person.drift, glowTarget(person), alphaTarget(person)) }
            suspend fun Animatable<Float, AnimationVector1D>.follow(target: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>) {
                if (targetValue == target && value == target) return
                if (still) snapTo(target) else animateTo(target, spec)
            }
            launch { motion.drift.follow(person.drift, moveSpec) }
            launch { motion.glow.follow(glowTarget(person), fadeSpec) }
            launch { motion.alpha.follow(alphaTarget(person), fadeSpec) }
        }
    }
    val pulse: State<Float>? = if (!still && people.any(OrbitPerson::isDue)) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse",
        )
    } else {
        null
    }

    val avatarPx = (PersonSize.Avatar.diameter * density.density).roundToInt()
    val photos = rememberOrbitPhotos(remember(people) { people.mapNotNullTo(HashSet()) { it.photo } }, avatarPx * 2)

    val initials = remember(initialStyle) { HashMap<String, TextLayoutResult>() }
    val names = remember(nameStyle) { HashMap<String, TextLayoutResult>() }
    fun nameLayout(text: String): TextLayoutResult = names.getOrPut(text) {
        measurer.measure(
            text,
            nameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = (NAME_MAX_WIDTH * density.density).roundToInt()),
        )
    }

    /** Each Person's centre (screen dp from the view's centre) and drawn radius right now. */
    fun placed(v: OrbitViewport, look: RingLook, animated: Boolean): List<PlacedPerson> = items.map { item ->
        val person = item.person
        val drift = if (animated) motions[person.id]?.drift?.value ?: person.drift else person.drift
        val r = frame.personRadius(person.ringIndex, drift) * if (animated) entrance.value else 1f
        val size = PersonSize.forPerson(look.sizes[person.ringIndex], person.isProminent)
        PlacedPerson(person.id, v.screenX(item.dx * r), v.screenY(item.dy * r), size.diameter / 2, dimmed = !person.matches)
    }

    // Names: worked out again only when the zoom or the People change.
    val nameCache = remember { NameCache() }
    fun visibleNames(zoom: Float): Set<Long> {
        if (nameCache.zoom == zoom && nameCache.items === items) return nameCache.shown
        val v = OrbitViewport(zoom)
        val look = ringLook(zoom)
        val everyone = placed(v, look, animated = false)
        val candidates = everyone.withIndex()
            .filter { (i, p) -> !p.dimmed && p.radius * 2 >= PersonSize.Avatar.diameter && items[i].label.isNotEmpty() }
            .sortedWith(compareBy({ !items[it.index].person.isProminent }, { items[it.index].person.ringIndex }, { it.value.id }))
            .map { (i, p) ->
                val layout = nameLayout(items[i].label)
                NameCandidate(p, layout.size.width / density.density, layout.size.height / density.density)
            }
        val obstacles = buildList {
            add(Box(-FrameYou, -FrameYou, FrameYou, FrameYou))
            rings.indices.forEach { i ->
                val label = if (look.counted[i]) ringLabels[i].counted else ringLabels[i].plain
                val w = pillWidth(label)
                val h = pillHeight(label)
                val y = -frame.radii[i] * zoom
                add(Box(-w / 2, y - h / 2, w / 2, y + h / 2))
            }
        }
        return OrbitNames.visible(candidates, everyone, obstacles).also {
            nameCache.shown = it
            nameCache.zoom = zoom
            nameCache.items = items
        }
    }

    // Gestures.
    val scope = rememberCoroutineScope()
    val viewportJob = remember { arrayOfNulls<Job>(1) }
    fun moveViewport(target: OrbitViewport) {
        viewportJob[0]?.cancel()
        if (still) {
            viewport = target
        } else {
            val from = viewport
            viewportJob[0] = scope.launch {
                animate(0f, 1f, animationSpec = viewportSpec) { t, _ ->
                    viewport = OrbitViewport(
                        zoom = from.zoom + (target.zoom - from.zoom) * t,
                        panX = from.panX + (target.panX - from.panX) * t,
                        panY = from.panY + (target.panY - from.panY) * t,
                    )
                }
            }
        }
    }
    val currentActions by rememberUpdatedState(actions)
    fun touch(at: Offset, long: Boolean) {
        val x = at.x / density.density - bounds.halfWidth
        val y = at.y / density.density - bounds.halfHeight
        val v = viewport
        val canZoom = v.zoom < OrbitViewport.MAX_ZOOM - 0.01f
        when (val hit = OrbitHits.hit(placed(v, ringLook(v.zoom), animated = true), x, y, canZoom)) {
            is OrbitHit.Person -> if (long) currentActions.onQuickActions(hit.id) else currentActions.onOpen(hit.id)
            OrbitHit.Crowd -> moveViewport(v.zoomedTo(min(v.zoom * OrbitViewport.STEP_ZOOM, OrbitViewport.MAX_ZOOM), x, y, bounds))
            OrbitHit.Nothing -> Unit
        }
    }
    val onTouch by rememberUpdatedState<(Offset, Boolean) -> Unit> { at, long -> touch(at, long) }
    val onDoubleTap by rememberUpdatedState<(Offset) -> Unit> { at ->
        val d = density.density
        moveViewport(viewport.doubleTapped(at.x / d - bounds.halfWidth, at.y / d - bounds.halfHeight, bounds))
    }
    val gestures = Modifier
        .pointerInput(bounds) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                viewportJob[0]?.cancel()
                val d = density.density
                viewport = viewport.transformed(
                    focusX = centroid.x / d - bounds.halfWidth,
                    focusY = centroid.y / d - bounds.halfHeight,
                    factor = zoom,
                    dragX = pan.x / d,
                    dragY = pan.y / d,
                    bounds = bounds,
                )
            }
        }
        .pointerInput(bounds) {
            detectTapGestures(
                onDoubleTap = { onDoubleTap(it) },
                onTap = { onTouch(it, false) },
                onLongPress = { onTouch(it, true) },
            )
        }

    val bandAlpha = 1f - (1f - if (dark) 0.3f else 0.2f).pow(1f / max(1, rings.size))
    // The glow: the primary colour itself in dark; in light a lighter tone of it, or it reads as a shadow.
    val glowColour = if (dark) colors.primary else colors.primaryFixedDim
    // One soft disc, drawn tinted for every glow: no gradient shader per Person per frame.
    val glowImage = remember { glowBitmap() }
    val glowTint = remember(glowColour) { ColorFilter.tint(glowColour) }
    Box(Modifier.fillMaxSize().then(gestures)) {
        // Rings, their labels and "You".
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val v = viewport
            val look = ringLook(v.zoom)
            drawRings(v, frame, rings, counts, ringLabels, look, colors, bandAlpha, pillPadX, pillPadY)
            drawYou(v, you, colors)
        }
        // Glows under the Due People: the only layer the pulse redraws.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val v = viewport
            val look = ringLook(v.zoom)
            val p = pulse?.value
            val d = density.density
            val cx = size.width / 2
            val cy = size.height / 2
            val e = entrance.value
            for (item in drawOrder) {
                val person = item.person
                val motion = motions[person.id]
                val glow = (motion?.glow?.value ?: glowTarget(person)) * (motion?.alpha?.value ?: alphaTarget(person)) * e.coerceIn(0f, 1f)
                if (glow <= 0.01f) continue
                val r = frame.personRadius(person.ringIndex, motion?.drift?.value ?: person.drift) * e
                val x = cx + v.screenX(item.dx * r) * d
                val y = cy + v.screenY(item.dy * r) * d
                val half = PersonSize.forPerson(look.sizes[person.ringIndex], true).diameter / 2
                // Solid under the Person, fading out past them; the pulse breathes it out a little.
                val reach = half / GLOW_SOLID * (if (p != null) 1f + 0.14f * p else 1.07f) * d
                val strength = if (p != null) 0.55f + 0.35f * p else 0.75f
                val side = (reach * 2).roundToInt()
                drawImage(
                    glowImage,
                    dstOffset = IntOffset((x - reach).roundToInt(), (y - reach).roundToInt()),
                    dstSize = IntSize(side, side),
                    alpha = (strength * glow).coerceIn(0f, 1f),
                    colorFilter = glowTint,
                    filterQuality = FilterQuality.Low,
                )
            }
        }
        // The People, then their names.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val v = viewport
            val look = ringLook(v.zoom)
            val d = density.density
            val cx = size.width / 2
            val cy = size.height / 2
            val e = entrance.value
            val appear = (e * 2f).coerceIn(0f, 1f)
            val showNames = if (e >= 0.999f) visibleNames(v.zoom) else emptySet()
            for (item in drawOrder) {
                val person = item.person
                val motion = motions[person.id]
                val r = frame.personRadius(person.ringIndex, motion?.drift?.value ?: person.drift) * e
                val x = cx + v.screenX(item.dx * r) * d
                val y = cy + v.screenY(item.dy * r) * d
                val size = PersonSize.forPerson(look.sizes[person.ringIndex], person.isProminent)
                val radius = size.diameter / 2 * d
                if (x < -radius * 3 || y < -radius * 3 || x > this.size.width + radius * 3 || y > this.size.height + radius * 3) continue
                val alpha = (motion?.alpha?.value ?: alphaTarget(person)) * appear
                val glow = motion?.glow?.value ?: glowTarget(person)
                drawPerson(item, size, Offset(x, y), alpha, glow, photos[person.photo], colors, initials, measurer, initialStyle)
                if (person.id in showNames) {
                    val layout = nameLayout(item.label)
                    drawText(
                        layout,
                        color = colors.onSurface,
                        topLeft = Offset(x - layout.size.width / 2f, y + radius + 2f * d),
                        alpha = alpha,
                    )
                }
            }
        }
        val itemsById = remember(items) { items.associateBy { it.person.id } }
        OrbitSemantics(rings, people, today, frame, bounds, ringLabels, itemsById, ::ringLook, { viewport }, actions, pillPadX, pillPadY)
    }
}

/** The share of a glow's radius that's solid (under the Person). */
private const val GLOW_SOLID = 0.55f

/** A white disc, solid to [GLOW_SOLID] of its radius and fading to nothing at the edge. */
private fun glowBitmap(size: Int = 96): ImageBitmap {
    val bitmap = createBitmap(size, size)
    val r = size / 2f
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.RadialGradient(
            r, r, r,
            intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, GLOW_SOLID, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    android.graphics.Canvas(bitmap).drawCircle(r, r, r, paint)
    return bitmap.asImageBitmap()
}

/** The names last worked out, and for what. */
private class NameCache {
    var zoom = Float.NaN
    var items: List<OrbitItem>? = null
    var shown: Set<Long> = emptySet()
}

/** "You"'s half-size, kept clear of names. */
private const val FrameYou = OrbitFrame.YOU_RADIUS + 4f

/** The widest a name under an Avatar gets before it's cut short. */
private const val NAME_MAX_WIDTH = 72f

private fun TextStyle.capped(fontScale: Float, cap: Float): TextStyle {
    if (fontScale <= cap) return this
    val factor = cap / fontScale
    return copy(
        fontSize = if (fontSize.isSpecified) fontSize * factor else 14.sp * factor,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    )
}

private fun DrawScope.drawRings(
    v: OrbitViewport,
    frame: OrbitFrame,
    rings: List<OrbitRing>,
    counts: IntArray,
    labels: List<RingLabel>,
    look: RingLook,
    colors: ColorScheme,
    bandAlpha: Float,
    pillPadX: Float,
    pillPadY: Float,
) {
    val d = density
    val centre = Offset(size.width / 2 + v.panX * d, size.height / 2 + v.panY * d)
    val band = colors.primary.copy(alpha = bandAlpha)
    // Bands, outermost first, so the middle is the deepest.
    for (i in rings.indices.reversed()) drawCircle(band, radius = frame.radii[i] * v.zoom * d, center = centre)
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f * d, 4f * d))
    for (i in rings.indices) {
        val empty = counts[i] == 0
        drawCircle(
            color = colors.primary.copy(alpha = if (empty) 0.22f else 0.3f),
            radius = frame.radii[i] * v.zoom * d,
            center = centre,
            style = Stroke(width = 1f * d, pathEffect = if (empty) dash else null),
        )
    }
    // Labels on each Ring at the top, in a pill the colour of the band behind them.
    for (i in rings.indices) {
        val empty = counts[i] == 0
        val layout = if (look.counted[i]) labels[i].counted else labels[i].plain
        val w = layout.size.width + 2 * pillPadX * d
        val h = layout.size.height + 2 * pillPadY * d
        val top = Offset(centre.x, centre.y - frame.radii[i] * v.zoom * d)
        var fill = colors.surface
        for (j in rings.indices.reversed()) if (j >= i) fill = band.compositeOver(fill)
        drawRoundRect(fill, topLeft = Offset(top.x - w / 2, top.y - h / 2), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
        drawRoundRect(
            colors.primary.copy(alpha = if (empty) 0.22f else 0.3f),
            topLeft = Offset(top.x - w / 2, top.y - h / 2),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2),
            style = Stroke(width = 1f * d, pathEffect = if (empty) dash else null),
        )
        drawText(
            layout,
            color = colors.primary,
            topLeft = Offset(top.x - layout.size.width / 2f, top.y - layout.size.height / 2f),
            alpha = if (empty) 0.6f else 1f,
        )
    }
}

private fun DrawScope.drawYou(v: OrbitViewport, you: TextLayoutResult, colors: ColorScheme) {
    val d = density
    val centre = Offset(size.width / 2 + v.panX * d, size.height / 2 + v.panY * d)
    drawCircle(colors.primary.copy(alpha = 0.16f), radius = (OrbitFrame.YOU_RADIUS + 9f) * d, center = centre)
    drawCircle(colors.primary, radius = OrbitFrame.YOU_RADIUS * d, center = centre)
    drawText(you, color = colors.onPrimary, topLeft = Offset(centre.x - you.size.width / 2f, centre.y - you.size.height / 2f))
}

private fun DrawScope.drawPerson(
    item: OrbitItem,
    size: PersonSize,
    at: Offset,
    alpha: Float,
    glow: Float,
    photo: ImageBitmap?,
    colors: ColorScheme,
    initials: HashMap<String, TextLayoutResult>,
    measurer: TextMeasurer,
    initialStyle: TextStyle,
) {
    val d = density
    val radius = size.diameter / 2 * d
    // A cut-out in the background colour keeps neighbours apart.
    drawCircle(colors.surface, radius = radius + (if (size == PersonSize.Dot) 1f else 1.5f) * d, center = at, alpha = alpha)
    when {
        size == PersonSize.Dot -> drawCircle(item.accent, radius = radius, center = at, alpha = alpha)
        photo != null -> {
            val side = (radius * 2).roundToInt()
            drawImage(
                photo,
                dstOffset = IntOffset((at.x - radius).roundToInt(), (at.y - radius).roundToInt()),
                dstSize = IntSize(side, side),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
        }
        size == PersonSize.Small -> drawCircle(item.accent, radius = radius, center = at, alpha = alpha)
        else -> {
            drawCircle(item.container, radius = radius, center = at, alpha = alpha)
            if (item.initial.isNotEmpty()) {
                val layout = initials.getOrPut(item.initial) { measurer.measure(item.initial, initialStyle, maxLines = 1) }
                drawText(
                    layout,
                    color = item.content,
                    topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f),
                    alpha = alpha,
                )
            }
        }
    }
    // Due: a ring in the primary colour, under the glow.
    if (glow > 0.01f) {
        drawCircle(colors.primary, radius = radius + 0.5f * d, center = at, alpha = alpha * glow, style = Stroke(width = 2f * d))
    }
    val soon = item.person.dateSoon ?: return
    // The apricot badge at the top right (not mirrored: the drawing isn't directional).
    val badge = (if (size == PersonSize.Avatar) 5.5f else 4.5f) * d
    val offset = radius * 0.7071f
    val centre = Offset(at.x + offset, at.y - offset)
    drawCircle(colors.surface, radius = badge + 1.5f * d, center = centre, alpha = alpha)
    if (soon == DateSoon.Today) {
        drawCircle(colors.tertiary, radius = badge, center = centre, alpha = alpha)
    } else {
        drawCircle(colors.tertiaryContainer, radius = badge, center = centre, alpha = alpha)
        drawCircle(colors.tertiary, radius = badge - 1f * d, center = centre, alpha = alpha, style = Stroke(width = 2f * d))
    }
}

/**
 * TalkBack's view of the drawing: Ring by Ring, innermost first, a heading per Ring ("Inner, 6
 * people, 2 due") then its matching People, each with a click (Person detail) and the long-press
 * sheet's actions. The nodes sit where the Ring labels and People are, kept inside the view so
 * everyone is reachable whatever the zoom.
 */
@Composable
private fun OrbitSemantics(
    rings: List<OrbitRing>,
    people: List<OrbitPerson>,
    today: LocalDate,
    frame: OrbitFrame,
    bounds: OrbitBounds,
    ringLabels: List<RingLabel>,
    items: Map<Long, OrbitItem>,
    ringLook: (Float) -> RingLook,
    viewport: () -> OrbitViewport,
    actions: OrbitPersonActions,
    pillPadX: Float,
    pillPadY: Float,
) {
    val language = LocalOrbitFormats.current.language
    val resources = LocalResources.current
    val numbers = LocalOrbitFormats.current.numbers
    val readings = remember(rings, people, language) {
        talkBackOrder(rings, people, { it.names.displayName(language) }, nameComparator(language))
    }
    Layout(
        modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true },
        content = {
            var index = 0
            for (reading in readings) {
                val headingText = resources.getQuantityString(
                    R.plurals.orbit_a11y_ring,
                    reading.people.size,
                    reading.ring.name,
                    numbers.format(reading.people.size),
                    numbers.format(reading.dueCount),
                )
                val order = (index++).toFloat()
                Box(
                    Modifier.semantics {
                        heading()
                        contentDescription = headingText
                        traversalIndex = order
                    },
                )
                for (person in reading.people) {
                    val description = personReading(person, today)
                    val custom = personActions(person, actions)
                    val personOrder = (index++).toFloat()
                    Box(
                        Modifier.semantics {
                            contentDescription = description
                            traversalIndex = personOrder
                            customActions = custom
                            onClick {
                                actions.onOpen(person.id)
                                true
                            }
                        },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val target = (OrbitHits.TARGET_RADIUS * 2 * density).roundToInt()
        // Where everyone is, in the same order as the nodes.
        val v = viewport()
        val look = ringLook(v.zoom)
        val placeables = ArrayList<Pair<androidx.compose.ui.layout.Placeable, IntOffset>>(measurables.size)
        var m = 0
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        fun clampedAt(cxDp: Float, cyDp: Float, width: Int, height: Int): IntOffset {
            val x = ((bounds.halfWidth + cxDp) * density - width / 2f).roundToInt().coerceIn(0, max(0, w - width))
            val y = ((bounds.halfHeight + cyDp) * density - height / 2f).roundToInt().coerceIn(0, max(0, h - height))
            return IntOffset(x, y)
        }
        for (reading in readings) {
            val i = reading.ring.index
            val label = if (look.counted[i]) ringLabels[i].counted else ringLabels[i].plain
            val lw = ((label.size.width / density) + 2 * pillPadX).times(density).roundToInt()
            val lh = ((label.size.height / density) + 2 * pillPadY).times(density).roundToInt()
            val heading = measurables[m++].measure(Constraints.fixed(lw, lh))
            placeables += heading to clampedAt(v.screenX(0f), v.screenY(-frame.radii[i]), lw, lh)
            for (person in reading.people) {
                val item = items[person.id]
                val r = frame.personRadius(i, person.drift)
                val node = measurables[m++].measure(Constraints.fixed(target, target))
                placeables += node to clampedAt(v.screenX((item?.dx ?: 0f) * r), v.screenY((item?.dy ?: 0f) * r), target, target)
            }
        }
        layout(w, h) {
            placeables.forEach { (placeable, at) -> placeable.place(at) }
        }
    }
}
