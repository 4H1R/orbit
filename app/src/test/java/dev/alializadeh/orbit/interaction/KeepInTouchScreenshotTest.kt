package dev.alializadeh.orbit.interaction

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.testing.NoCloser
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.SheetFrame
import dev.alializadeh.orbit.testing.captureScreen
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Log interaction sheet (new, and editing a shared one on a picked day), its People picker,
 * the Snooze sheet and the quick-action sheet, each drawn as a sheet over a scrim.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class KeepInTouchScreenshotTest(private val variant: ScreenshotVariant) {

    private val today = LocalDate.of(2026, 10, 28)
    private val names = if (variant.persian) PersianNames else EnglishNames
    private val people = names.mapIndexed { index, (name, other) -> PickablePerson(index + 1L, PersonNames(name, other)) }

    @Test
    fun logInteractionNew() = captureScreen("log_interaction_new", variant) {
        SheetFrame {
            LogInteractionSheetContent(
                state = LogSheetState(
                    interactionId = null,
                    type = InteractionType.Message,
                    date = today,
                    note = "",
                    people = people.take(1),
                    candidates = people,
                    today = today,
                ),
                onSave = {},
                onDelete = {},
                sheet = NoCloser,
            )
        }
    }

    /** Editing a shared Interaction on a picked day: the wheel picker, two People, a note, Delete. */
    @Test
    fun logInteractionEdit() = captureScreen("log_interaction_edit", variant, tall = true) {
        SheetFrame {
            LogInteractionSheetContent(
                state = LogSheetState(
                    interactionId = 7,
                    type = InteractionType.InPerson,
                    date = today.minusDays(12),
                    note = if (variant.persian) "شام خانهٔ مینا" else "Dinner at Mina's",
                    people = people.take(2),
                    candidates = people,
                    today = today,
                ),
                onSave = {},
                onDelete = {},
                sheet = NoCloser,
            )
        }
    }

    @Test
    fun peoplePicker() = captureScreen("log_interaction_people", variant) {
        SheetFrame {
            PeoplePickerContent(candidates = people.drop(1), onPick = {})
        }
    }

    @Test
    fun snooze() = captureScreen("snooze_sheet", variant) {
        SheetFrame {
            SnoozeSheetContent(SnoozeSheetState(1, people.first().names, today), onPick = {})
        }
    }

    @Test
    fun quickActions() = captureScreen("quick_actions", variant) {
        SheetFrame {
            QuickActionSheetContent(
                target = QuickActionTarget(
                    personId = 1,
                    names = people.first().names,
                    photo = null,
                    primaryContactMethod = ContactMethod(1, 1, ContactType.WhatsApp, "+49 151 2345 6789", isPrimary = true, position = 0),
                    isDue = true,
                ),
                onContactAction = {},
                onLogInteraction = {},
                onSnooze = {},
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}

private val EnglishNames = listOf(
    "Sara Ahmadi" to "سارا احمدی",
    "Reza Karimi" to null,
    "Mina" to null,
    "بابک" to "Babak",
    "Tom O'Neill" to null,
    "Hannah Becker" to null,
)

private val PersianNames = listOf(
    "سارا احمدی" to "Sara Ahmadi",
    "رضا کریمی" to null,
    "مینا" to null,
    "Babak" to "بابک",
    "Tom O'Neill" to null,
    "Hannah Becker" to null,
)
