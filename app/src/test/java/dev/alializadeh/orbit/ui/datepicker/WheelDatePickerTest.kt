package dev.alializadeh.orbit.ui.datepicker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The wheel picker in Compose (Robolectric): tapping rows and the Calendar toggle. Slice 3
 * (Important dates) and slice 4 (Interactions) are its first real users.
 */
@RunWith(RobolectricTestRunner::class)
class WheelDatePickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 28)

    private var state by mutableStateOf(
        WheelDatePickerState.forDate(RecurringDate(CalendarSystem.SolarHijri, 8, 5), today, allowNoYear = true),
    )

    private fun showPicker() {
        composeRule.setContent {
            OrbitTheme { WheelDatePicker(state = state, onStateChange = { state = it }) }
        }
    }

    @Test
    fun theToggleConvertsTheSelectionAndSaysWhichYearItUsed() {
        showPicker()

        composeRule.onNodeWithText("Gregorian").performClick()
        composeRule.waitForIdle()

        assertEquals(RecurringDate(CalendarSystem.Gregorian, 10, 27), state.date)
        composeRule.onNodeWithText("Converted using 1405").assertExists()
        composeRule.onNodeWithText("October").assertExists()
    }

    @Test
    fun tappingARowSelectsIt() {
        showPicker()

        composeRule.onNodeWithText("Mehr").performClick()
        composeRule.waitForIdle()

        assertEquals(RecurringDate(CalendarSystem.SolarHijri, 7, 5), state.date)
    }
}
