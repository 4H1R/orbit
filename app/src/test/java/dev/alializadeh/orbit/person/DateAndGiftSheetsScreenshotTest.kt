package dev.alializadeh.orbit.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.dates.ImportantDateSheetContent
import dev.alializadeh.orbit.person.gifts.GiftIdeaSheetContent
import dev.alializadeh.orbit.person.gifts.GivenDateSheetContent
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.captureScreen
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Important date sheet, the Gift idea sheet and the given-date sheet, each drawn as a sheet
 * over a scrim (a real `ModalBottomSheet` is a separate window the capture doesn't see).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DateAndGiftSheetsScreenshotTest(private val variant: ScreenshotVariant) {

    @Test
    fun dateSheetNew() = captureScreen("date_sheet_new", variant) {
        SheetFrame {
            ImportantDateSheetContent(
                initial = null,
                newType = ImportantDateType.Birthday,
                birthdayTaken = false,
                defaultLeadTimes = LeadTime.DEFAULT,
                today = ScreenshotToday,
                onDone = {},
                onDelete = null,
                sheet = NoCloser,
            )
        }
    }

    @Test
    fun dateSheetEditCustom() = captureScreen("date_sheet_edit", variant) {
        SheetFrame {
            ImportantDateSheetContent(
                initial = ImportantDateDraft(
                    id = 3,
                    type = ImportantDateType.Custom,
                    label = if (variant.persian) "مهاجرت به برلین" else "Moved to Berlin",
                    date = RecurringDate(CalendarSystem.SolarHijri, 7, 1, 1398),
                ),
                newType = ImportantDateType.Custom,
                birthdayTaken = true,
                defaultLeadTimes = LeadTime.DEFAULT,
                today = ScreenshotToday,
                onDone = {},
                onDelete = {},
                sheet = NoCloser,
            )
        }
    }

    @Test
    fun giftIdeaSheet() = captureScreen("gift_idea_sheet", variant) {
        SheetFrame {
            GiftIdeaSheetContent(
                initial = GiftIdea(
                    id = 1,
                    personId = 1,
                    text = if (variant.persian) "خودنویس لامی ۲۰۰۰" else "Fountain pen (Lamy 2000)",
                    link = "lamy.com/2000",
                    note = if (variant.persian) "مشکی، نوک ریز" else "Black, fine nib",
                    createdAt = Instant.EPOCH,
                ),
                today = ScreenshotToday,
                onSave = {},
                onDelete = {},
                onMarkGiven = {},
                onOpenLink = {},
                sheet = NoCloser,
            )
        }
    }

    @Test
    fun givenDateSheet() = captureScreen("gift_given_sheet", variant) {
        SheetFrame { GivenDateSheetContent(today = ScreenshotToday, onConfirm = {}, sheet = NoCloser) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}

private val NoCloser = SheetCloser { }

/** A bottom sheet's look: a scrim, then the sheet with its handle at the bottom. */
@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .padding(vertical = 22.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
                content()
            }
        }
    }
}
