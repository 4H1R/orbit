package dev.alializadeh.orbit.upcoming

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian
import dev.alializadeh.orbit.calendar.CalendarSystem.SolarHijri
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.captureScreen
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Upcoming in en/LTR and fa/RTL, light and dark: Today's hero card and rows with Coming up, the
 * same with the other Primary calendar, and both empty states.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class UpcomingScreenshotTest(private val variant: ScreenshotVariant) {

    private val today = LocalDate.of(2026, 10, 28) // Wednesday, 6 Aban 1405

    private val people = if (variant.persian) PersianPeople else EnglishPeople

    private fun loaded(people: List<UpcomingPerson>) =
        UpcomingUiState.Loaded(today = today, feed = UpcomingFeed.of(people, today))

    @Test
    fun upcoming() = captureScreen("upcoming", variant, tall = true) {
        Screen(loaded(people))
    }

    @Test
    fun upcomingOtherCalendar() {
        val other = if (variant.persian) Gregorian else SolarHijri
        captureScreen("upcoming_other_calendar", variant, primaryCalendar = other, tall = true) {
            Screen(loaded(people))
        }
    }

    @Test
    fun upcomingNoPeople() = captureScreen("upcoming_no_people", variant) {
        Screen(loaded(emptyList()))
    }

    @Test
    fun upcomingAllCaughtUp() = captureScreen("upcoming_all_caught_up", variant) {
        // Only a date beyond the next 30 days.
        Screen(loaded(people.filter { it.id == 5L }))
    }

    @androidx.compose.runtime.Composable
    private fun Screen(uiState: UpcomingUiState) {
        UpcomingScreen(uiState = uiState, onNewPerson = {}, onPersonClick = {}, onContactAction = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}

private var nextId = 1L

private fun person(
    id: Long,
    name: String,
    type: ImportantDateType,
    date: RecurringDate,
    label: String? = null,
    contact: ContactType? = null,
    ideas: List<String> = emptyList(),
) = UpcomingPerson(
    id = id,
    names = PersonNames(name),
    photoFileName = null,
    isArchived = false,
    primaryContactMethod = contact?.let {
        ContactMethod(id = id, personId = id, type = it, value = "+49 151 2345 6789", isPrimary = true, position = 0)
    },
    importantDates = listOf(
        ImportantDate(id = nextId++, personId = id, type = type, label = label, date = date, position = 0),
    ),
    openGiftIdeas = ideas,
)

private fun people(
    sara: String,
    reza: String,
    mina: String,
    tom: String,
    babak: String,
    graduation: String,
    ideas: List<String>,
    minaIdeas: List<String>,
) = listOf(
    // Today: Sara turns 35 (the hero), and Reza's 6th anniversary.
    person(1, sara, ImportantDateType.Birthday, RecurringDate(CalendarSystem.Gregorian, 10, 28, 1991), contact = ContactType.Phone, ideas = ideas),
    person(2, reza, ImportantDateType.Anniversary, RecurringDate(CalendarSystem.SolarHijri, 8, 6, 1399), contact = ContactType.WhatsApp),
    // Coming up: in 2 days, in 8 days (past a week: the date), in 25 days.
    person(3, mina, ImportantDateType.Birthday, RecurringDate(CalendarSystem.SolarHijri, 8, 8), ideas = minaIdeas),
    person(4, tom, ImportantDateType.Custom, RecurringDate(CalendarSystem.Gregorian, 11, 5, 2015), label = graduation),
    person(6, babak, ImportantDateType.Anniversary, RecurringDate(CalendarSystem.SolarHijri, 9, 1, 1395)),
    // Beyond 30 days: "Next" when nothing else shows.
    person(5, sara, ImportantDateType.Birthday, RecurringDate(CalendarSystem.Gregorian, 1, 12)),
)

private val EnglishPeople = people(
    sara = "Sara Ahmadi",
    reza = "Reza Karimi",
    mina = "Mina",
    tom = "Tom O'Neill",
    babak = "Babak",
    graduation = "graduation",
    ideas = listOf("Fountain pen (Lamy 2000)", "Illustrated Divan of Hafez", "Silk scarf"),
    minaIdeas = listOf("Concert tickets", "Plant"),
)

private val PersianPeople = people(
    sara = "سارا احمدی",
    reza = "رضا کریمی",
    mina = "مینا",
    tom = "Tom O'Neill",
    babak = "بابک",
    graduation = "جشن فارغ‌التحصیلی",
    ideas = listOf("خودنویس لامی ۲۰۰۰", "دیوان مصور حافظ", "شال ابریشمی"),
    minaIdeas = listOf("بلیت کنسرت", "گلدان"),
)
