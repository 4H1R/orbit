package dev.alializadeh.orbit.occasion

import androidx.compose.foundation.text.input.TextFieldState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.SheetFrame
import dev.alializadeh.orbit.testing.captureScreen
import dev.alializadeh.orbit.ui.datepicker.WheelDatePickerState
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings → Occasions, the Add sheet, the editor, and an Occasion on its day. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class OccasionScreenshotTest(private val variant: ScreenshotVariant) {

    private val today = LocalDate.of(2026, 10, 28)
    private val fa = variant.persian
    private val rings = listOf(
        Ring(id = 1, name = if (fa) "صمیمی" else "Inner", position = 0, cadence = Cadence.days(14)),
        Ring(id = 2, name = if (fa) "نزدیک" else "Close", position = 1, cadence = Cadence.days(30)),
        Ring(id = 3, name = if (fa) "دوستان" else "Friends", position = 2, cadence = Cadence.days(90)),
    )
    private val tags = listOf(Tag(id = 1, name = if (fa) "همکاران" else "Work"), Tag(id = 2, name = if (fa) "معلم‌ها" else "Teachers"))
    private val names = if (fa) {
        listOf("سارا احمدی", "رضا کریمی", "مینا", "امید")
    } else {
        listOf("Sara Ahmadi", "Reza Karimi", "Mina", "Omid")
    }
    private val people = names.mapIndexed { i, name ->
        occasionPerson(i + 1L, ringId = rings[i % 3].id, ringPosition = i % 3, tagIds = if (i == 1) setOf(2L) else emptySet())
            .copy(names = PersonNames(name), ringName = rings[i % 3].name)
    }

    @Test
    fun occasionsSettings() = captureScreen("occasions_settings", variant) {
        val nowruz = occasionOf(1, BuiltInOccasion.Nowruz.date, forEveryone = true, builtIn = BuiltInOccasion.Nowruz)
        val teachers = occasionOf(2, BuiltInOccasion.TeachersDay.date, tagIds = setOf(2), builtIn = BuiltInOccasion.TeachersDay)
        val club = occasionOf(3, RecurringDate(CalendarSystem.Gregorian, 11, 12), personIds = setOf(1, 3), label = if (fa) "باشگاه کتاب" else "Book club")
        OccasionsSettingsScreen(
            uiState = OccasionsSettingsUiState.Loaded(
                items = listOf(OccasionListItem(club, 15, 2), OccasionListItem(nowruz, 144, 4), OccasionListItem(teachers, 186, 1)),
                addable = BuiltInOccasion.entries - setOf(BuiltInOccasion.Nowruz, BuiltInOccasion.TeachersDay),
            ),
            onBack = {},
            onOpen = {},
            onAdd = {},
        )
    }

    @Test
    fun addOccasionSheet() = captureScreen("occasion_add_sheet", variant, tall = true) {
        SheetFrame { AddOccasionSheetContent(BuiltInOccasion.entries.drop(1), onPick = {}) }
    }

    @Test
    fun occasionEditor() = captureScreen("occasion_edit", variant, tall = true) {
        OccasionEditScreen(
            uiState = OccasionEditUiState.Loaded(rings, tags, people, LeadTime.DEFAULT, today),
            isNew = true,
            builtIn = null,
            name = TextFieldState(if (fa) "دورهمی خانوادگی" else "Family gathering"),
            picker = WheelDatePickerState.forDate(RecurringDate(CalendarSystem.LunarHijri, 10, 3), today, allowNoYear = true),
            onPicker = {},
            forEveryone = false,
            onForEveryone = {},
            ringIds = setOf(1),
            onRingIds = {},
            tagIds = emptySet(),
            onTagIds = {},
            personIds = setOf(4),
            onPersonIds = {},
            leadTimes = null,
            onLeadTimes = {},
            onBack = {},
            onSave = {},
            onDelete = {},
        )
    }

    @Test
    fun occasionOnTheDay() = captureScreen("occasion", variant) {
        OccasionScreen(
            uiState = OccasionUiState.Loaded(
                occasion = Occasion(id = 1, builtIn = BuiltInOccasion.Nowruz, date = BuiltInOccasion.Nowruz.date, forEveryone = true),
                people = people,
                date = today,
                today = today,
                reached = setOf(1, 3),
            ),
            onBack = {},
            onEdit = {},
            onPersonClick = {},
            onSendWishes = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = ScreenshotVariant.entries
    }
}

