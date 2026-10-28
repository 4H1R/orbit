package dev.alializadeh.orbit.person

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.dates.ImportantDateSheetContent
import dev.alializadeh.orbit.person.gifts.GiftIdeaSheetContent
import dev.alializadeh.orbit.person.gifts.GivenDateSheetContent
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.testing.NoCloser
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.SheetFrame
import dev.alializadeh.orbit.testing.captureScreen
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

    /** A Birthday kept in Lunar Hijri: the third Calendar on the toggle. */
    @Test
    fun dateSheetLunarHijri() = captureScreen("date_sheet_lunar", variant) {
        SheetFrame {
            ImportantDateSheetContent(
                initial = ImportantDateDraft(
                    id = 6,
                    type = ImportantDateType.Birthday,
                    date = RecurringDate(CalendarSystem.LunarHijri, 9, 15, 1408),
                ),
                newType = ImportantDateType.Birthday,
                birthdayTaken = false,
                defaultLeadTimes = LeadTime.DEFAULT,
                today = ScreenshotToday,
                onDone = {},
                onDelete = {},
                sheet = NoCloser,
            )
        }
    }

    /** Custom reminders: the chips editable, with No reminders last. */
    @Test
    fun dateSheetCustomReminders() = captureScreen("date_sheet_custom_reminders", variant, tall = true) {
        SheetFrame {
            ImportantDateSheetContent(
                initial = ImportantDateDraft(
                    id = 4,
                    type = ImportantDateType.Anniversary,
                    date = RecurringDate(if (variant.persian) CalendarSystem.SolarHijri else CalendarSystem.Gregorian, 5, 12, 1399),
                    leadTimes = setOf(LeadTime.OnTheDay, LeadTime.OneDay, LeadTime.TwoWeeks),
                ),
                newType = ImportantDateType.Anniversary,
                birthdayTaken = true,
                defaultLeadTimes = LeadTime.DEFAULT,
                today = ScreenshotToday,
                onDone = {},
                onDelete = {},
                sheet = NoCloser,
            )
        }
    }

    /** No reminders: the date still shows on Upcoming. */
    @Test
    fun dateSheetNoReminders() = captureScreen("date_sheet_no_reminders", variant, tall = true) {
        SheetFrame {
            ImportantDateSheetContent(
                initial = ImportantDateDraft(
                    id = 5,
                    type = ImportantDateType.Birthday,
                    date = RecurringDate(CalendarSystem.Gregorian, 2, 29),
                    leadTimes = emptySet(),
                ),
                newType = ImportantDateType.Birthday,
                birthdayTaken = false,
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
