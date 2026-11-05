package dev.alializadeh.orbit.upcoming

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ui.expressive.LocalStillMotion
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upcoming's Due group: the count in its title, the toggle as the group's last row, two buttons a
 * row with Snooze a long-press (the quick-action sheet) or a TalkBack action away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class UpcomingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 28)
    private val showAll = mutableListOf<Boolean>()
    private val snoozed = mutableListOf<Long>()
    private val logged = mutableListOf<Long>()

    /** Six Due People in Friends, longest since the last Interaction first; the first has a phone number. */
    private val people = (0..5).map { i ->
        UpcomingPerson(
            id = 10L + i,
            names = PersonNames("Person $i"),
            photoFileName = null,
            isArchived = false,
            primaryContactMethod = if (i == 0) {
                ContactMethod(id = 1, personId = 10, type = ContactType.Phone, value = "+49 151 2345 6789", isPrimary = true, position = 0)
            } else {
                null
            },
            importantDates = emptyList(),
            openGiftIdeas = emptyList(),
            keepInTouch = KeepInTouch(cadence = Cadence.days(30), addedOn = today.minusDays(90), lastInteraction = today.minusDays(45L - i)),
            ringName = "Friends",
        )
    }

    private fun show(showAllDue: Boolean = false) {
        composeRule.setContent {
            OrbitTheme {
                CompositionLocalProvider(LocalStillMotion provides true) {
                    UpcomingScreen(
                        uiState = UpcomingUiState.Loaded(today = today, feed = UpcomingFeed.of(people, today), showAllDue = showAllDue),
                        onNewPerson = {},
                        onPersonClick = {},
                        onContactAction = {},
                        onLogInteraction = { logged += it },
                        onSnooze = { snoozed += it },
                        onShowAllDue = { showAll += it },
                    )
                }
            }
        }
    }

    private fun row(name: String) = composeRule.onNode(hasText(name) and hasClickAction())

    @Test
    fun theDueTitleSaysHowManyAndTheGroupEndsWithTheToggle() {
        show()

        composeRule.onNode(hasText("Due") and hasContentDescription("6 people are due") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("Person 5").assertDoesNotExist()
        composeRule.onNodeWithText("Show 1 more").performClick()
        assertEquals(listOf(true), showAll)
    }

    @Test
    fun showingAllEndsWithShowFewer() {
        show(showAllDue = true)

        composeRule.onNodeWithText("Person 5").assertIsDisplayed()
        composeRule.onNodeWithText("Show fewer").performClick()
        assertEquals(listOf(false), showAll)
    }

    @Test
    fun aDueRowShowsItsContactActionAndLogInteractionWithSnoozeAsACustomAction() {
        show()

        val first = row("Person 0")
        first.assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Call")).assertIsDisplayed()
        // One Log interaction button per Due row shown (five), no Snooze button.
        assertEquals(5, composeRule.onAllNodes(hasContentDescription("Log interaction")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasContentDescription("Snooze")).fetchSemanticsNodes().size)

        val actions = first.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Snooze"), actions.map { it.label })
        actions.single().action()
        assertEquals(listOf(10L), snoozed)
    }

    @Test
    fun longPressingADueRowOpensItsQuickActions() {
        show()

        row("Person 1").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Snooze").assertIsDisplayed()
        composeRule.onNodeWithText("Log interaction").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(11L), logged)
    }
}
