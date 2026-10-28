package dev.alializadeh.orbit.settings

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.TagWithCount
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingWithCount
import dev.alializadeh.orbit.testing.NoCloser
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.SheetFrame
import dev.alializadeh.orbit.testing.captureScreen
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings with its Rings and Tags rows, Settings → Rings and its sheet, and Settings → Tags and its sheet. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class RingsTagsScreenshotTest(private val variant: ScreenshotVariant) {

    private val rings = if (variant.persian) PersianRings else EnglishRings
    private val tags = if (variant.persian) PersianTags else EnglishTags

    @Test
    fun settingsWithRingsAndTags() = captureScreen("settings_people_rows", variant) {
        SettingsScreen(
            uiState = SettingsUiState(
                theme = ThemeMode.System,
                colourSource = ColourSource.OrbitViolet,
                primaryCalendar = if (variant.persian) CalendarSystem.SolarHijri else CalendarSystem.Gregorian,
                ringNames = rings.map { it.ring.name },
                tagCount = tags.size,
            ),
            onOpen = {},
            onBackupSpikeClick = {},
        )
    }

    @Test
    fun ringsScreen() = captureScreen("rings", variant) {
        RingsScreen(
            uiState = RingsUiState.Loaded(rings),
            onBack = {},
            onAdd = { _, _ -> },
            onUpdate = { _, _, _ -> },
            onMovePeople = { _, _ -> },
            onRemove = {},
            onReorder = {},
        )
    }

    /** Six Rings: Add ring is disabled, with the reason. */
    @Test
    fun ringsFull() = captureScreen("rings_full", variant) {
        val extra = listOf(
            RingWithCount(Ring(5, if (variant.persian) "همکاران" else "Colleagues", 4, Cadence.months(2)), 4),
            RingWithCount(Ring(6, if (variant.persian) "همسایه‌ها" else "Neighbours", 5, null), 0),
        )
        RingsScreen(
            uiState = RingsUiState.Loaded(rings + extra),
            onBack = {},
            onAdd = { _, _ -> },
            onUpdate = { _, _, _ -> },
            onMovePeople = { _, _ -> },
            onRemove = {},
            onReorder = {},
        )
    }

    /** A Ring with People: Remove disabled, offering to move them first. */
    @Test
    fun ringSheetWithPeople() = captureScreen("ring_sheet", variant) {
        SheetFrame {
            RingSheetContent(
                ring = rings[1],
                rings = rings,
                onSave = { _, _ -> },
                onMovePeople = { _, _ -> },
                onRemove = {},
                sheet = NoCloser,
            )
        }
    }

    @Test
    fun ringSheetNew() = captureScreen("ring_sheet_new", variant) {
        SheetFrame {
            RingSheetContent(ring = null, rings = rings, onSave = { _, _ -> }, onMovePeople = { _, _ -> }, onRemove = {}, sheet = NoCloser)
        }
    }

    @Test
    fun tagsScreen() = captureScreen("tags", variant) {
        TagsScreen(
            uiState = TagsUiState.Loaded(tags),
            onBack = {},
            onRename = { _, _ -> },
            onConfirmMerge = {},
            onDismissMerge = {},
            onDelete = {},
        )
    }

    @Test
    fun tagSheet() = captureScreen("tag_sheet", variant) {
        SheetFrame {
            TagSheetContent(tag = tags.first(), onRename = {}, onDelete = {}, onShowPeople = null, sheet = NoCloser)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}

private fun ring(id: Long, name: String, cadence: Cadence?, people: Int) =
    RingWithCount(Ring(id = id, name = name, position = id.toInt() - 1, cadence = cadence), people)

private val EnglishRings = listOf(
    ring(1, "Inner", Cadence.days(14), 4),
    ring(2, "Close", Cadence.days(30), 6),
    ring(3, "Friends", Cadence.days(90), 23),
    ring(4, "Acquaintances", null, 1),
)

private val PersianRings = listOf(
    ring(1, "صمیمی", Cadence.days(14), 4),
    ring(2, "نزدیک", Cadence.days(30), 6),
    ring(3, "دوستان", Cadence.days(90), 23),
    ring(4, "آشنایان", null, 1),
)

private val EnglishTags = listOf(
    TagWithCount(Tag(1, "Gym"), 12),
    TagWithCount(Tag(2, "Book club"), 5),
    TagWithCount(Tag(3, "Work"), 1),
    TagWithCount(Tag(4, "Berlin"), 0),
)

private val PersianTags = listOf(
    TagWithCount(Tag(1, "باشگاه"), 12),
    TagWithCount(Tag(2, "باشگاه کتاب"), 5),
    TagWithCount(Tag(3, "کار"), 1),
    TagWithCount(Tag(4, "برلین"), 0),
)
