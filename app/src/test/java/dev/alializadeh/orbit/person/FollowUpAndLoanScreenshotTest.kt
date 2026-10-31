package dev.alializadeh.orbit.person

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.person.followups.FollowUpSheetContent
import dev.alializadeh.orbit.person.loans.LoanSheetContent
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

/** The Follow-up and Loan sheets, each drawn as a sheet over a scrim. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class FollowUpAndLoanScreenshotTest(private val variant: ScreenshotVariant) {

    private val followUp = FollowUp(
        id = 1,
        personId = 1,
        text = if (variant.persian) "بپرسم مصاحبه چطور بود" else "Ask how the interview went",
        dueOn = ScreenshotToday.plusDays(3),
        createdAt = Instant.EPOCH,
    )

    private val loan = Loan(
        id = 1,
        personId = 1,
        direction = LoanDirection.Lent,
        text = if (variant.persian) "کتاب بوف کور" else "The blue hiking tent",
        returnBy = ScreenshotToday.plusDays(10),
        createdAt = Instant.EPOCH,
    )

    @Test
    fun followUpNew() = captureScreen("follow_up_sheet_new", variant) {
        SheetFrame {
            FollowUpSheetContent(null, ScreenshotToday, onSave = {}, onDelete = null, onMarkDone = null, sheet = NoCloser)
        }
    }

    @Test
    fun followUpEdit() = captureScreen("follow_up_sheet_edit", variant, tall = true) {
        SheetFrame {
            FollowUpSheetContent(followUp, ScreenshotToday, onSave = {}, onDelete = {}, onMarkDone = {}, sheet = NoCloser)
        }
    }

    @Test
    fun loanNew() = captureScreen("loan_sheet_new", variant) {
        SheetFrame {
            LoanSheetContent(null, ScreenshotToday, onSave = {}, onDelete = null, onMarkReturned = null, sheet = NoCloser)
        }
    }

    @Test
    fun loanEdit() = captureScreen("loan_sheet_edit", variant, tall = true) {
        SheetFrame {
            LoanSheetContent(loan, ScreenshotToday, onSave = {}, onDelete = {}, onMarkReturned = {}, sheet = NoCloser)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = ScreenshotVariant.entries
    }
}
