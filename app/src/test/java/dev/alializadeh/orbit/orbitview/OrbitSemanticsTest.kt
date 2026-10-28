package dev.alializadeh.orbit.orbitview

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.search.PeopleFilters
import dev.alializadeh.orbit.ui.expressive.LocalStillMotion
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What TalkBack gets from the drawing: a heading per Ring, innermost first, counting only the People
 * the filters leave in, then those People with a click (Person detail) and the long-press sheet's
 * actions as custom actions. Dimmed People are skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class OrbitSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<Long>()
    private val snoozed = mutableListOf<Long>()
    private val logged = mutableListOf<Long>()

    private fun show(filters: PeopleFilters = PeopleFilters()) {
        composeRule.setContent {
            OrbitTheme {
                CompositionLocalProvider(LocalStillMotion provides true) {
                    OrbitViewScreen(
                        uiState = orbitState(orbitFewPeople(persian = false), EnOrbitRings, EnOrbitTags, OrbitToday, filters) { null },
                        opening = 0,
                        filterActions = NoOrbitFilterActions,
                        onPersonClick = { opened += it },
                        onAddPerson = {},
                        onContactAction = {},
                        onLogInteraction = { logged += it },
                        onSnooze = { snoozed += it },
                    )
                }
            }
        }
    }

    private val sara = "Sara Ahmadi, due, last talked 20 days ago, birthday in 5 days"

    private fun headings(): List<String> = composeRule.onAllNodes(isHeading()).fetchSemanticsNodes()
        .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }

    private fun traversal(description: String): Float =
        composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().config[SemanticsProperties.TraversalIndex]

    @Test
    fun ringsAreHeadingsInnermostFirstThenTheirPeopleDueFirst() {
        show()

        assertEquals(
            listOf("Inner, 4 people, 2 due", "Close, 6 people, 2 due", "Friends, 7 people, 3 due", "Acquaintances, 3 people, 0 due"),
            headings(),
        )
        // Inner: Mina (30 days) and Sara (20) are Due, then Ali and Reza by name.
        val inner = listOf(
            "Inner, 4 people, 2 due",
            "Mina Karimi, due, last talked 30 days ago",
            sara,
            "Ali Rezaei, last talked 8 days ago",
            "Reza Tehrani, last talked 3 days ago",
            "Close, 6 people, 2 due",
        ).map(::traversal)
        assertEquals(inner.sorted(), inner)
        assertEquals(inner.size, inner.toSet().size)
    }

    @Test
    fun aPersonOpensOnClickAndHasTheQuickActionsAsCustomActions() {
        show()

        val node = composeRule.onNodeWithContentDescription(sara)
        val labels = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Call", "Log interaction", "Snooze"), labels)

        node.performSemanticsAction(SemanticsActions.OnClick)
        node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Snooze" }.action()
        node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Log interaction" }.action()
        assertEquals(listOf(1L), opened)
        assertEquals(listOf(1L), snoozed)
        assertEquals(listOf(1L), logged)

        // Not Due: no Snooze; no contact method: no contact action.
        val reza = composeRule.onNodeWithContentDescription("Reza Tehrani, last talked 3 days ago").fetchSemanticsNode()
        assertEquals(listOf("Log interaction"), reza.config[SemanticsActions.CustomActions].map { it.label })
    }

    @Test
    fun dimmedPeopleAreSkippedAndHeadingsCountOnlyMatches() {
        show(PeopleFilters(due = true))

        assertEquals(
            listOf("Inner, 2 people, 2 due", "Close, 2 people, 2 due", "Friends, 3 people, 3 due", "Acquaintances, 0 people, 0 due"),
            headings(),
        )
        composeRule.onAllNodesWithContentDescription("Reza Tehrani, last talked 3 days ago").assertCountEquals(0)
        composeRule.onNodeWithContentDescription(sara).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        composeRule.onNode(hasContentDescription("Laleh", substring = true)).assertDoesNotExist()
    }
}
