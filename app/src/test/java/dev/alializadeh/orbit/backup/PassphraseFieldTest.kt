package dev.alializadeh.orbit.backup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Typing into a passphrase field (Compose under Robolectric): digits are converted as typed. */
@RunWith(RobolectricTestRunner::class)
class PassphraseFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun persianAndArabicDigitsAreTypedAsAscii() {
        val state = TextFieldState()
        composeRule.setContent {
            OrbitTheme { PassphraseField(state = state, label = "Passphrase", supportingText = null, isError = false) }
        }

        composeRule.onNodeWithText("Passphrase").performTextInput("orbit ۱۴۰۵ and ٢٠٢٦")
        composeRule.waitForIdle()

        assertEquals("orbit 1405 and 2026", state.text.toString())
    }

    @Test
    fun theFormNeedsTheRulesTheRepeatAndTheWarning() {
        val form = PassphraseForm(PassphraseFormMode.Setup)
        composeRule.setContent { OrbitTheme { PassphraseFormScreen(form = form, saving = false, onBack = {}, onSave = {}) } }

        composeRule.onNodeWithText("Passphrase").performTextInput("short one")
        composeRule.onNodeWithText("Repeat passphrase").performTextInput("short one")
        composeRule.onNodeWithText("I understand").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(PassphraseRules.Problem.TooShort, form.problem)
        assertFalse(form.canSave)

        composeRule.onNodeWithText("Passphrase").performTextInput(" made longer")
        composeRule.onNodeWithText("Repeat passphrase").performTextInput(" made longer")
        composeRule.waitForIdle()
        assertEquals("short one made longer", form.newPassphrase)
        assertEquals("short one made longer", form.repeat.text.toString())
        assertTrue("acknowledged", form.acknowledged)
        assertTrue(form.canSave)
    }
}
