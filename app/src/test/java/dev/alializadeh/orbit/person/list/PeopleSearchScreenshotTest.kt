package dev.alializadeh.orbit.person.list

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.DateSoon
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PeopleSort
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.person.ScreenshotToday
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.search.InteractionNote
import dev.alializadeh.orbit.search.PeopleFilters
import dev.alializadeh.orbit.search.RingFilterSheetContent
import dev.alializadeh.orbit.search.SearchContact
import dev.alializadeh.orbit.search.SearchDocument
import dev.alializadeh.orbit.search.SearchIndex
import dev.alializadeh.orbit.search.TagFilterSheetContent
import dev.alializadeh.orbit.testing.NoCloser
import dev.alializadeh.orbit.testing.NoPeopleListActions
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
 * The People list of slice 8: search results with their "why it matched" lines, the chips with
 * filters on and the Filtered bar, the filter sheets, each sort's headers (letters with the
 * fast-scroll thumb, Rings, none), the Archived view and the empty states, in en and fa.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PeopleSearchScreenshotTest(private val variant: ScreenshotVariant) {

    private val data = if (variant.persian) PersianPeople else EnglishPeople

    /** A partial word: names (word start, then mid-word), a Tag, Interaction notes, Likes, Notes; one Archived. */
    @Test
    fun searchResults() = captureScreen("people_search_results", variant) {
        People(data.state(query = data.query), query = data.query)
    }

    /** A phone fragment (Persian digits in fa) finds the numbers whatever their punctuation. */
    @Test
    fun searchByPhone() = captureScreen("people_search_phone", variant) {
        People(data.state(query = data.phoneQuery), query = data.phoneQuery)
    }

    /** Sorted by Ring with its headers; one Ring, two Tags and Due picked; the Filtered bar. */
    @Test
    fun filtered() = captureScreen("people_filtered", variant) {
        People(
            data.state(
                sort = PeopleSort.Ring,
                filters = PeopleFilters(ringIds = setOf(2), tagIds = setOf(1, 2), due = true),
            ),
        )
    }

    /** Sorted by Name: letter headers (Persian letters in fa) and the fast-scroll thumb. */
    @Test
    fun byName() = captureScreen("people_sort_name", variant) {
        People(data.state(sort = PeopleSort.Name))
    }

    /** Sorted by Ring: a header per Ring, innermost first. */
    @Test
    fun byRing() = captureScreen("people_sort_ring", variant) {
        People(data.state(sort = PeopleSort.Ring))
    }

    /** Last Interaction: no headers; People with none first, then longest ago. */
    @Test
    fun byLastInteraction() = captureScreen("people_sort_last_interaction", variant) {
        People(data.state(sort = PeopleSort.LastInteraction))
    }

    @Test
    fun archivedView() = captureScreen("people_archived", variant) {
        People(data.state(archivedView = true))
    }

    @Test
    fun archivedEmpty() = captureScreen("people_archived_empty", variant) {
        People(data.state(archivedView = true, withArchived = false))
    }

    /** Nothing left with the filters on: Clear filters, and the Archived match below. */
    @Test
    fun noMatches() = captureScreen("people_no_matches", variant) {
        People(data.state(query = data.query, filters = PeopleFilters(due = true, ringIds = setOf(1))), query = data.query)
    }

    @Test
    fun ringSheet() = captureScreen("people_filter_ring_sheet", variant) {
        SheetFrame { RingFilterSheetContent(data.rings, setOf(2L), onChange = {}, sheet = NoCloser) }
    }

    @Test
    fun tagsSheet() = captureScreen("people_filter_tags_sheet", variant) {
        SheetFrame { TagFilterSheetContent(data.tags, setOf(1L, 2L), onChange = {}, sheet = NoCloser) }
    }

    @Composable
    private fun People(state: PeopleUiState, query: String = "") {
        PeopleScreen(
            uiState = state,
            query = TextFieldState(query),
            actions = NoPeopleListActions,
            onPersonClick = {},
            onAddPerson = {},
            onContactAction = {},
            onLogInteraction = {},
            onSnooze = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}

/** One language's People, and the list state the ViewModel would make of them. */
private class PeopleData(
    val rings: List<Ring>,
    val tags: List<Tag>,
    val people: List<PersonSummary>,
    val query: String,
    val phoneQuery: String,
) {
    fun state(
        query: String = "",
        sort: PeopleSort = PeopleSort.Name,
        filters: PeopleFilters = PeopleFilters(),
        archivedView: Boolean = false,
        withArchived: Boolean = true,
    ): PeopleUiState.Loaded {
        val everyone = if (withArchived) people else people.filterNot { it.isArchived }
        val rows = everyone.map {
            PersonRow(it, photo = null, due = it.keepInTouch.on(ScreenshotToday), dateSoon = DateSoon.of(it.importantDates, ScreenshotToday))
        }
        val hits = SearchIndex(everyone.map { it.search }).search(query)
        val (archived, active) = rows.partition { it.summary.isArchived }
        fun List<PersonRow>.matching() = if (hits == null) this else mapNotNull { row -> hits[row.summary.id]?.let { row.copy(hit = it) } }
        return PeopleUiState.Loaded(
            people = if (archivedView) archived.matching() else active.filter { filters.matches(it.filterFacts) }.matching(),
            today = ScreenshotToday,
            sort = sort,
            searching = hits != null,
            filters = filters,
            archivedView = archivedView,
            hasPeople = active.isNotEmpty(),
            archivedCount = archived.size,
            archivedMatches = if (hits == null || archivedView) 0 else archived.count { it.summary.id in hits },
            rings = rings,
            tags = tags,
        )
    }
}

private fun ring(id: Long, name: String, days: Int?) = Ring(id = id, name = name, position = id.toInt() - 1, cadence = days?.let(Cadence::days))

/** Someone in [ring], last talked [lastDaysAgo] days ago (null: never), added [addedDaysAgo] days ago. */
private fun person(
    id: Long,
    name: String,
    ring: Ring,
    lastDaysAgo: Long?,
    other: String? = null,
    addedDaysAgo: Long = 200 - id,
    tags: List<Tag> = emptyList(),
    canHelpWith: List<String> = emptyList(),
    likes: List<String> = emptyList(),
    howWeMet: String? = null,
    notes: String? = null,
    phone: String? = null,
    interactionNotes: List<Pair<Long, String>> = emptyList(),
    birthday: Pair<Int, Int>? = null,
    archived: Boolean = false,
): PersonSummary {
    val names = PersonNames(name, other)
    return PersonSummary(
        id = id,
        names = names,
        photoFileName = null,
        ringName = ring.name,
        keepInTouch = KeepInTouch(
            cadence = ring.cadence,
            addedOn = ScreenshotToday.minusDays(addedDaysAgo),
            lastInteraction = lastDaysAgo?.let(ScreenshotToday::minusDays),
            isArchived = archived,
        ),
        ringPosition = ring.position,
        primaryContactMethod = phone?.let { ContactMethod(id, id, ContactType.Phone, it, isPrimary = true, position = 0) },
        importantDates = birthday?.let { (month, day) ->
            listOf(ImportantDate(id = id, personId = id, type = ImportantDateType.Birthday, date = RecurringDate(CalendarSystem.Gregorian, month, day), position = 0))
        }.orEmpty(),
        ringId = ring.id,
        tagIds = tags.mapTo(HashSet(), Tag::id),
        addedAt = Instant.parse("2026-10-28T09:00:00Z").minusSeconds(addedDaysAgo * 86_400),
        search = SearchDocument(
            personId = id,
            names = names,
            tags = tags.map(Tag::name),
            canHelpWith = canHelpWith,
            likes = likes,
            howWeMet = howWeMet,
            notes = notes,
            contactMethods = listOfNotNull(phone?.let { SearchContact(ContactType.Phone, it) }),
            interactionNotes = interactionNotes.mapIndexed { i, (daysAgo, note) -> InteractionNote(id * 10 + i, ScreenshotToday.minusDays(daysAgo), note) },
        ),
    )
}

private val EnRings = listOf(ring(1, "Inner", 14), ring(2, "Close", 30), ring(3, "Friends", 90), ring(4, "Acquaintances", null))
private val EnTags = listOf(Tag(1, "Work"), Tag(2, "Gym"), Tag(3, "University"), Tag(4, "Book club"), Tag(5, "Berlin"), Tag(6, "Neighbours"))

private val EnglishPeople = PeopleData(
    rings = EnRings,
    tags = EnTags,
    query = "ber",
    phoneQuery = "0912 345",
    people = listOf(
        person(1, "Ali Rezaei", EnRings[0], 3, phone = "+98 912 345 6789", tags = listOf(EnTags[0])),
        person(2, "Amir Karimi", EnRings[2], 120, tags = listOf(EnTags[1])),
        person(3, "Babak Moradi", EnRings[1], 41, other = "بابک مرادی", tags = listOf(EnTags[0], EnTags[1])),
        person(4, "Bernard Shaw", EnRings[2], 10),
        person(5, "Camille Roux", EnRings[3], null),
        person(6, "Darius Farahani", EnRings[1], 12, tags = listOf(EnTags[1])),
        person(7, "Élodie Martin", EnRings[2], 200, birthday = 11 to 6),
        person(8, "Farid Nouri", EnRings[3], 60),
        person(9, "Golnar Rahimi", EnRings[1], 35, tags = listOf(EnTags[0]), birthday = 10 to 28),
        person(10, "Hana Suzuki", EnRings[2], 5),
        person(11, "Kian Jafari", EnRings[0], 20, tags = listOf(EnTags[1])),
        person(
            12, "Laleh Ahmadi", EnRings[1], 2,
            interactionNotes = listOf(
                2L to "She's moving to Berlin next spring with the whole family",
                40L to "Berlin flat viewings",
                90L to "Asked about Berlin",
            ),
        ),
        person(13, "Mina Karimi", EnRings[2], 95, likes = listOf("Jazz", "Blueberries")),
        person(14, "Nazanin Hosseini", EnRings[0], 15),
        person(
            15, "Omid Tehrani", EnRings[2], 30,
            notes = "Lives near the old observatory and is always up for a long walk in Kreuzberg on Sundays",
        ),
        person(16, "Parisa Bagheri", EnRings[1], null, addedDaysAgo = 3),
        person(17, "Reza Tehrani", EnRings[1], 50, phone = "0912 345 1122", tags = listOf(EnTags[0])),
        person(18, "Robert Klein", EnRings[3], 300),
        person(19, "Sara Ahmadi", EnRings[1], 34, other = "سارا احمدی", tags = listOf(EnTags[4], EnTags[2]), canHelpWith = listOf("Photography")),
        person(20, "Tom O'Neill", EnRings[2], 800, archived = true),
        person(21, "Yasmin Azizi", EnRings[2], 400, archived = true),
        person(22, "Zahra Moradi", EnRings[1], 150, howWeMet = "Language school in Berlin", archived = true),
    ),
)

private val FaRings = listOf(ring(1, "صمیمی", 14), ring(2, "نزدیک", 30), ring(3, "دوستان", 90), ring(4, "آشنایان", null))
private val FaTags = listOf(Tag(1, "کار"), Tag(2, "باشگاه"), Tag(3, "دانشگاه"), Tag(4, "کتاب‌خوانی"), Tag(5, "برلین"), Tag(6, "همسایه"))

private val PersianPeople = PeopleData(
    rings = FaRings,
    tags = FaTags,
    query = "بر",
    phoneQuery = "۰۹۱۲ ۳۴۵",
    people = listOf(
        person(1, "علی رضایی", FaRings[0], 3, other = "Ali Rezaei", phone = "+98 912 345 6789", tags = listOf(FaTags[0])),
        person(2, "آرش", FaRings[2], 120, tags = listOf(FaTags[1])),
        person(3, "احمد کریمی", FaRings[1], 41, tags = listOf(FaTags[0], FaTags[1])),
        person(4, "برزین", FaRings[2], 10),
        person(5, "Camille Roux", FaRings[3], null),
        person(6, "داریوش فراهانی", FaRings[1], 12, tags = listOf(FaTags[1])),
        person(7, "الهام صادقی", FaRings[2], 200, birthday = 11 to 6),
        person(8, "فرید نوری", FaRings[3], 60),
        person(9, "گلناز رحیمی", FaRings[1], 35, tags = listOf(FaTags[0]), birthday = 10 to 28),
        person(10, "پریا", FaRings[2], 5),
        person(11, "کیان جعفری", FaRings[0], 20, tags = listOf(FaTags[1])),
        person(
            12, "لاله احمدی", FaRings[1], 2,
            interactionNotes = listOf(
                2L to "بهار سال بعد با همهٔ خانواده به برلین می‌رود",
                40L to "دیدن خانه در برلین",
                90L to "پرسید از برلین",
            ),
        ),
        person(13, "مینا کریمی", FaRings[2], 95, likes = listOf("جاز", "تمبر")),
        person(14, "نازنین حسینی", FaRings[0], 15),
        person(15, "هما تهرانی", FaRings[2], 30, notes = "نزدیک رصدخانهٔ قدیمی زندگی می‌کند و یکشنبه‌ها همیشه برای پیاده‌روی بیرون می‌رود"),
        person(16, "شیرین", FaRings[1], null, addedDaysAgo = 3),
        person(17, "رضا تهرانی", FaRings[1], 50, phone = "0912 345 1122", tags = listOf(FaTags[0])),
        person(18, "اکبر", FaRings[3], 300),
        person(19, "سارا احمدی", FaRings[1], 34, other = "Sara Ahmadi", tags = listOf(FaTags[4], FaTags[2]), canHelpWith = listOf("عکاسی")),
        person(20, "Tom O'Neill", FaRings[2], 800, archived = true),
        person(21, "یاسمن عزیزی", FaRings[2], 400, archived = true),
        person(22, "زهرا مرادی", FaRings[1], 150, howWeMet = "آموزشگاه زبان در برلین", archived = true),
    ),
)
