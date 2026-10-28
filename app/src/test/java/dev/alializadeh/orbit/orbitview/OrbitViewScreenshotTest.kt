package dev.alializadeh.orbit.orbitview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.search.PeopleFilters
import dev.alializadeh.orbit.testing.NoCloser
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.SheetFrame
import dev.alializadeh.orbit.testing.captureScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Orbit view of slice 9: about 20 People (Avatars, Due drift and glow, badges, names), about
 * 200 (Small and Dots, counted labels), zoomed in, filtered with the rest dimmed, filters matching
 * nobody, the legend sheet and the empty state, in en/fa × light/dark.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class OrbitViewScreenshotTest(private val variant: ScreenshotVariant) {

    private val rings = if (variant.persian) FaOrbitRings else EnOrbitRings
    private val tags = if (variant.persian) FaOrbitTags else EnOrbitTags

    @Test
    fun few() = captureScreen("orbit_few", variant) {
        Orbit(orbitFewPeople(variant.persian))
    }

    @Test
    fun many() = captureScreen("orbit_many", variant) {
        Orbit(orbitManyPeople(variant.persian))
    }

    /** 2.5× a little above and to the right of "You" in the crowded view: Rings switch up a size. */
    @Test
    fun zoomed() = captureScreen("orbit_zoomed", variant) {
        CompositionLocalProvider(LocalOrbitStartViewport provides OrbitViewport(zoom = 2.5f, panX = -120f, panY = 160f)) {
            Orbit(orbitManyPeople(variant.persian))
        }
    }

    /** Due only, in two Rings: everyone else dimmed in place; the Filtered bar. */
    @Test
    fun filtered() = captureScreen("orbit_filtered", variant) {
        Orbit(orbitFewPeople(variant.persian), PeopleFilters(ringIds = setOf(1, 3), due = true))
    }

    /** Filters that leave nobody in: everyone dimmed, "No one matches · Clear". */
    @Test
    fun noMatches() = captureScreen("orbit_no_matches", variant) {
        Orbit(orbitFewPeople(variant.persian), PeopleFilters(ringIds = setOf(4), due = true))
    }

    @Test
    fun empty() = captureScreen("orbit_empty", variant) {
        Orbit(emptyList())
    }

    @Test
    fun legend() = captureScreen("orbit_legend", variant) {
        SheetFrame { OrbitLegendSheetContent(NoCloser) }
    }

    @Composable
    private fun Orbit(people: List<PersonSummary>, filters: PeopleFilters = PeopleFilters()) {
        OrbitViewScreen(
            uiState = orbitState(people, rings, tags, OrbitToday, filters) { null },
            opening = 0,
            filterActions = NoOrbitFilterActions,
            onPersonClick = {},
            onAddPerson = {},
            onContactAction = {},
            onLogInteraction = {},
            onSnooze = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}

internal object NoOrbitFilterActions : OrbitFilterActions {
    override fun setRingFilter(ids: Set<Long>) = Unit

    override fun setTagFilter(ids: Set<Long>) = Unit

    override fun toggleDue() = Unit

    override fun toggleDateSoon() = Unit

    override fun clearFilters() = Unit
}
