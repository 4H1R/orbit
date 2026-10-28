package dev.alializadeh.orbit.wishes

import androidx.compose.foundation.text.input.TextFieldState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.testing.NoCloser
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.SheetFrame
import dev.alializadeh.orbit.testing.captureScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Wishes sheet: a birthday message on WhatsApp, and Signal, which needs the message copied. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class WishesScreenshotTest(private val variant: ScreenshotVariant) {

    private val methods = listOf(
        ContactMethod(id = 1, personId = 1, type = ContactType.Phone, value = "+98 912 123 4567", label = if (variant.persian) "همراه" else "mobile", position = 0),
        ContactMethod(id = 2, personId = 1, type = ContactType.WhatsApp, value = "+98 912 123 4567", isPrimary = true, position = 1),
        ContactMethod(id = 3, personId = 1, type = ContactType.Signal, value = "+98 912 123 4567", position = 2),
    )
    private val language = if (variant.persian) OrbitLanguage.Persian else OrbitLanguage.English
    private val names = if (variant.persian) PersonNames("سارا احمدی", "Sara Ahmadi") else PersonNames("Sara Ahmadi", "سارا احمدی")
    private val message = if (variant.persian) {
        "سارا عزیز، تولدت مبارک. سالی پر از شادی و سلامتی برایت آرزو می‌کنم."
    } else {
        "Happy birthday, Sara. Wishing you a wonderful year ahead."
    }

    private fun state(channelKey: String) = WishesSheetState(
        personId = 1,
        names = names,
        kind = WishKind.Date(ImportantDateType.Birthday),
        channels = WishChannels.of(methods),
        channelKey = channelKey,
        language = language,
    )

    @Test
    fun wishes() = captureScreen("wishes_sheet", variant) {
        SheetFrame {
            WishesSheetContent(state("2"), TextFieldState(message), {}, {}, {}, {}, NoCloser)
        }
    }

    @Test
    fun wishesCopied() = captureScreen("wishes_sheet_copy", variant) {
        SheetFrame {
            WishesSheetContent(state("3").copy(logAsMessage = false), TextFieldState(message), {}, {}, {}, {}, NoCloser)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = ScreenshotVariant.entries
    }
}
