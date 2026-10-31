package dev.alializadeh.orbit.lock

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ApplicationProvider
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.interaction.KeepInTouchSheets
import dev.alializadeh.orbit.interaction.KeepInTouchViewModel
import dev.alializadeh.orbit.orbitview.OrbitViewKey
import dev.alializadeh.orbit.person.AddPersonKey
import dev.alializadeh.orbit.person.PeopleKey
import dev.alializadeh.orbit.person.PersonDetailKey
import dev.alializadeh.orbit.person.edit.PersonFormRoute
import dev.alializadeh.orbit.person.edit.PersonFormViewModel
import dev.alializadeh.orbit.person.photo.CropImage
import dev.alializadeh.orbit.person.photo.CropRect
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.reminders.ReminderPermissionAsks
import dev.alializadeh.orbit.testing.FakeInteractionRepository
import dev.alializadeh.orbit.testing.FakePersonRepository
import dev.alializadeh.orbit.testing.FakePhotoStore
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.FakeRingRepository
import dev.alializadeh.orbit.settings.SettingsKey
import dev.alializadeh.orbit.testing.profileOf
import dev.alializadeh.orbit.ui.expressive.LocalStillMotion
import dev.alializadeh.orbit.ui.navigation.rememberEntries
import dev.alializadeh.orbit.ui.navigation.rememberOrbitNavigator
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import dev.alializadeh.orbit.upcoming.UpcomingKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * App lock's gate: while locked nothing of Orbit is composed (so nothing is drawn or reachable by
 * accessibility), and after unlocking everything behind it is as it was: the back stack, each
 * entry's ViewModel (an unsaved form with its cropped photo, an open Log interaction sheet) and
 * saveable state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // the crop screen draws a real bitmap
class AppLockGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC)
    private val people = FakePersonRepository(initial = listOf(profileOf(7, "Sara")))
    private val photos = FakePhotoStore().apply { image = PickedImage }
    private val photoStore = RecordingPhotoStore(photos)
    private var lock by mutableStateOf(AppLockState.Unlocked)
    private val forms = mutableListOf<PersonFormViewModel>()
    private val keepInTouch = mutableListOf<KeepInTouchViewModel>()

    @Test
    fun anUnsavedFormKeepsItsNameAndPhotoThroughALock() {
        showOrbit()
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText(text(R.string.person_form_name)).performTextInput("Ada Lovelace")
        // The picker's result, then Done on the crop screen.
        composeRule.runOnIdle { forms.single().startCrop("content://media/picked") }
        composeRule.onNodeWithText(text(R.string.common_done)).assertIsEnabled().performClick()
        composeRule.onNodeWithText(text(R.string.person_form_photo_change)).assertExists()
        val photo = composeRule.runOnIdle { forms.single().uiState.value.photo!!.name }

        lockAndExpectNothingOfOrbit()

        unlock()
        composeRule.onNodeWithText("Ada Lovelace").assertExists()
        composeRule.onNodeWithText(text(R.string.person_form_photo_change)).assertExists()
        composeRule.runOnIdle {
            assertEquals("the form's ViewModel was kept, not recreated", 1, forms.size)
            assertEquals(photo, forms.single().uiState.value.photo?.name)
            assertTrue("the cropped photo wasn't deleted", photo in photos.files)
        }

        // The changes are still unsaved: closing asks first, and Discard deletes the photo.
        composeRule.onNodeWithContentDescription(text(R.string.common_cancel)).performClick()
        composeRule.onNodeWithText(text(R.string.discard_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.discard_confirm)).performClick()
        composeRule.onNodeWithText("Add").assertExists()
        composeRule.runOnIdle { assertFalse(photo in photos.files) }
    }

    @Test
    fun theCropScreenStaysOpenThroughALock() {
        showOrbit()
        composeRule.onNodeWithText("Add").performClick()
        composeRule.runOnIdle { forms.single().startCrop("content://media/picked") }
        composeRule.onNodeWithText(text(R.string.common_done)).assertIsEnabled()
        // Moves the (wider than tall) photo right, so the crop starts further left.
        composeRule.onRoot().performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(20f, 0f)) }
            up()
        }
        composeRule.waitForIdle()

        lockAndExpectNothingOfOrbit()

        unlock()
        composeRule.onNodeWithText(text(R.string.common_done)).assertIsEnabled().performClick()
        composeRule.onNodeWithText(text(R.string.person_form_photo_change)).assertExists()
        composeRule.runOnIdle {
            assertEquals(1, forms.size)
            // Centred, it would start at 50: the pan was kept.
            assertTrue("crop ${photoStore.crops.single()}", photoStore.crops.single().left < 50f)
        }
    }

    @Test
    fun anOpenLogInteractionSheetKeepsItsNoteThroughALock() {
        showOrbit()
        composeRule.onNodeWithText("Sara").performClick()
        composeRule.runOnIdle { keepInTouch.single().logInteraction(7) }
        composeRule.onNodeWithText(text(R.string.interaction_note)).performTextInput("Coffee in town")

        lockAndExpectNothingOfOrbit()

        unlock()
        composeRule.onNodeWithText("Coffee in town").assertExists()
        assertEquals(1, keepInTouch.size)
    }

    private fun showOrbit() {
        composeRule.setContent {
            OrbitTheme {
                CompositionLocalProvider(LocalStillMotion provides true) {
                    AppLockGate(
                        state = lock,
                        locked = { LockedScreen(onUnlock = {}) },
                    ) { entryStates ->
                        val navigator = rememberOrbitNavigator()
                        val entries = navigator.rememberEntries(
                            entryStates,
                            entryProvider {
                                entry<UpcomingKey> {
                                    Column {
                                        Button(onClick = { navigator.navigate(AddPersonKey) }) { Text("Add") }
                                        Button(onClick = { navigator.navigate(PersonDetailKey(7)) }) { Text("Sara") }
                                    }
                                }
                                entry<AddPersonKey> {
                                    PersonFormRoute(
                                        personId = null,
                                        focus = null,
                                        onClose = navigator::goBack,
                                        onSaved = {},
                                        viewModel = viewModel { newForm() },
                                    )
                                }
                                entry<PersonDetailKey> {
                                    KeepInTouchSheets(viewModel { newKeepInTouch() })
                                }
                                entry<PeopleKey> {}
                                entry<OrbitViewKey> {}
                                entry<SettingsKey> {}
                            },
                        )
                        NavDisplay(entries = entries, onBack = navigator::goBack)
                    }
                }
            }
        }
    }

    private fun lockAndExpectNothingOfOrbit() {
        lock = AppLockState.Locked
        composeRule.waitForIdle()
        // Only the locked screen is there: no Orbit text, field or sheet, in any window.
        assertEquals(setOf(text(R.string.locked_title), text(R.string.locked_unlock)), allTexts())
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodes(hasText("Ada", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    private fun unlock() {
        lock = AppLockState.Unlocked
        composeRule.waitForIdle()
    }

    private fun allTexts(): Set<String> =
        composeRule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text) +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
            .toSet()

    private fun newForm() = PersonFormViewModel(
        personId = null,
        focus = null,
        personRepository = people,
        ringRepository = FakeRingRepository(),
        photoStore = photoStore,
        preferencesRepository = FakePreferencesRepository(),
        clock = clock,
        reminderAsks = ReminderPermissionAsks.None,
    ).also { forms += it }

    private fun newKeepInTouch() = KeepInTouchViewModel(
        personRepository = people,
        interactionRepository = FakeInteractionRepository(people, clock),
        preferencesRepository = FakePreferencesRepository(),
        photoStore = photoStore,
        clock = clock,
        reminderAsks = ReminderPermissionAsks.None,
    ).also { keepInTouch += it }

    private fun text(id: Int): String = context.getString(id)

    /** Notes the square each crop saves. */
    private class RecordingPhotoStore(private val fake: FakePhotoStore) : PhotoStore by fake {
        val crops = mutableListOf<CropRect>()

        override suspend fun saveCrop(image: CropImage, crop: CropRect): String {
            crops += crop
            return fake.saveCrop(image, crop)
        }
    }

    /** A picked photo the crop screen can draw. */
    private object PickedImage : CropImage {
        override val width = 400
        override val height = 300

        override fun imageBitmap(): ImageBitmap = ImageBitmap(width, height)
    }
}
