package dev.alializadeh.orbit.ui.datepicker

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.captureScreen
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The wheel picker just after a Calendar flip of a year-less date, so the hint shows. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class WheelDatePickerScreenshotTest(private val variant: ScreenshotVariant) {

    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun datePicker() {
        // English: 30 Esfand flipped to Gregorian. Persian: 29 Feb flipped to Solar Hijri.
        val state = if (variant.persian) {
            WheelDatePickerState.forDate(RecurringDate(CalendarSystem.Gregorian, 2, 29), today, allowNoYear = true)
                .withCalendar(CalendarSystem.SolarHijri)
        } else {
            WheelDatePickerState.forDate(RecurringDate(CalendarSystem.SolarHijri, 12, 30), today, allowNoYear = true)
                .withCalendar(CalendarSystem.Gregorian)
        }
        captureScreen("date_picker", variant) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                WheelDatePicker(state = state, onStateChange = {}, modifier = Modifier.padding(16.dp))
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}
