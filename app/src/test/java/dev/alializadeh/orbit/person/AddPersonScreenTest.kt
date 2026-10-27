package dev.alializadeh.orbit.person

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.alializadeh.orbit.person.add.AddPersonScreen
import dev.alializadeh.orbit.person.add.AddPersonUiState
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddPersonScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveIsEnabledOnceANameIsTypedAndSavesOnClick() {
        val name = TextFieldState()
        var saves = 0
        composeRule.setContent {
            // TextFieldState is snapshot state, so this recomposes as the user types.
            val canSave = name.text.isNotBlank()
            OrbitTheme {
                AddPersonScreen(
                    uiState = AddPersonUiState(canSave = canSave),
                    name = name,
                    onSave = { saves++ },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Name").performTextInput("Sara")
        composeRule.onNodeWithText("Save").assertIsEnabled().performClick()

        assertEquals("Sara", name.text.toString())
        assertEquals(1, saves)
    }
}
