package dev.alializadeh.orbit.person

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.person.detail.PersonDetailEdits
import dev.alializadeh.orbit.person.detail.PersonDetailKeepInTouch
import dev.alializadeh.orbit.person.detail.PersonDetailScreen
import dev.alializadeh.orbit.person.detail.PersonDetailUiState
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ui.expressive.LocalStillMotion
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import androidx.compose.runtime.CompositionLocalProvider
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Person detail's behaviour beyond its screenshots: the name in the top bar, adding a date in place. */
@RunWith(RobolectricTestRunner::class)
class PersonDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val today = LocalDate.of(2026, 10, 28)
    private val edits = RecordingEdits()
    private val notes = "Allergic to cats. Prefers tea over coffee."

    @Test
    fun `the name shows in the top bar once it has scrolled away`() {
        show()
        composeRule.onAllNodesWithText(NAME).assertCountEquals(1)

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(notes))

        composeRule.onAllNodesWithText(NAME).assertCountEquals(2)
    }

    @Test
    fun `a Person without a Birthday gets one added in place`() {
        show()

        composeRule.onNodeWithText(context.getString(R.string.important_date_type_birthday)).performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.important_date_sheet_new)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.common_done)).performScrollTo().performClick()
        // The sheet slides away before it hands over the date.
        composeRule.waitUntil(timeoutMillis = 5_000) { edits.savedDates.isNotEmpty() }

        assertEquals(listOf(ImportantDateType.Birthday), edits.savedDates.map { it.type })
        assertEquals(listOf(null), edits.savedDates.map { it.id })
    }

    private fun show() {
        val ring = Ring(id = 1, name = "Close", position = 0, cadence = Cadence.days(30))
        val profile = PersonProfile(
            person = Person(id = 1, name = NAME, notes = notes, howWeMet = "Book club", ringId = 1, createdAt = Instant.EPOCH),
            ring = ring,
            tags = emptyList(),
            contactMethods = emptyList(),
            canHelpWith = listOf("Photography"),
            likes = listOf("Jazz", "Hiking"),
            dislikes = listOf("Cilantro"),
        )
        composeRule.setContent {
            OrbitTheme {
                CompositionLocalProvider(LocalStillMotion provides true) {
                    PersonDetailScreen(
                        uiState = PersonDetailUiState.Loaded(
                            profile,
                            today = today,
                            due = KeepInTouch(cadence = ring.cadence, addedOn = today.minusDays(10), lastInteraction = null).on(today),
                        ),
                        keepInTouch = NoKeepInTouch,
                        onBack = {},
                        onEdit = {},
                        onAddDetail = {},
                        onArchive = {},
                        onUnarchive = {},
                        onDelete = {},
                        onDeleteConfirm = {},
                        onDeleteDismiss = {},
                        edits = edits,
                        onMarkGiven = { _, _ -> },
                        onOpenLink = {},
                        onContactAction = {},
                        onCopy = {},
                    )
                }
            }
        }
    }

    private class RecordingEdits : PersonDetailEdits {
        val savedDates = mutableListOf<ImportantDateDraft>()
        override fun saveImportantDate(draft: ImportantDateDraft) {
            savedDates += draft
        }
        override fun deleteImportantDate(id: Long) = Unit
        override fun saveGiftIdea(draft: GiftIdeaDraft) = Unit
        override fun deleteGiftIdea(id: Long) = Unit
        override fun saveFollowUp(draft: FollowUpDraft) = Unit
        override fun deleteFollowUp(id: Long) = Unit
        override fun saveLoan(draft: LoanDraft) = Unit
        override fun deleteLoan(id: Long) = Unit
    }

    private object NoKeepInTouch : PersonDetailKeepInTouch {
        override fun logInteraction() = Unit
        override fun openInteraction(interactionId: Long) = Unit
        override fun allInteractions() = Unit
        override fun snooze() = Unit
        override fun cancelSnooze() = Unit
    }

    private companion object {
        const val NAME = "Sara Ahmadi"
    }
}
