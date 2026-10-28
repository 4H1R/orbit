package dev.alializadeh.orbit.person

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.interaction.AllInteractionsScreen
import dev.alializadeh.orbit.interaction.AllInteractionsUiState
import dev.alializadeh.orbit.interaction.Interaction
import dev.alializadeh.orbit.interaction.InteractionType
import dev.alializadeh.orbit.interaction.InteractionWithPeople
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.person.detail.PersonDetailKeepInTouch
import dev.alializadeh.orbit.person.edit.DateRow
import dev.alializadeh.orbit.person.detail.PersonDetailEdits
import dev.alializadeh.orbit.person.detail.PersonDetailScreen
import dev.alializadeh.orbit.person.detail.PersonDetailUiState
import dev.alializadeh.orbit.person.edit.ContactRow
import dev.alializadeh.orbit.person.edit.CropUiState
import dev.alializadeh.orbit.person.edit.PersonFormActions
import dev.alializadeh.orbit.person.edit.PersonFormFields
import dev.alializadeh.orbit.person.edit.PersonFormScreen
import dev.alializadeh.orbit.person.edit.PersonFormUiState
import dev.alializadeh.orbit.person.edit.TagSuggestion
import dev.alializadeh.orbit.person.list.PeopleScreen
import dev.alializadeh.orbit.person.list.PeopleUiState
import dev.alializadeh.orbit.person.list.PersonRow
import dev.alializadeh.orbit.person.photo.CropImage
import dev.alializadeh.orbit.person.photo.PhotoCropScreen
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.captureScreen
import java.time.Instant
import java.time.LocalDate
import androidx.compose.foundation.text.input.TextFieldState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of the People list, Person detail, the Add/Edit form and the crop screen in
 * en/LTR and fa/RTL, light and dark.
 *
 * Record: `./gradlew recordRoborazziDebug`. Verify: `./gradlew verifyRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PersonScreenshotTest(private val variant: ScreenshotVariant) {

    private val data = if (variant.persian) PersianData else EnglishData

    /** Rows with "Ring · last talked…", the Due chip and the date badge (soon, and filled today). */
    @Test
    fun peopleList() = captureScreen("people_list", variant) {
        People(
            PeopleUiState.Loaded(
                data.people.map {
                    PersonRow(it, photo = null, due = it.keepInTouch.on(ScreenshotToday), dateSoon = DateSoon.of(it.importantDates, ScreenshotToday))
                },
                today = ScreenshotToday,
            ),
        )
    }

    @Test
    fun peopleListEmpty() = captureScreen("people_list_empty", variant) {
        People(PeopleUiState.Loaded(emptyList()))
    }

    /** Last talked 12 days ago, every 30 days; seven Interactions, so "All interactions (7)". */
    @Test
    fun personDetailFull() = captureScreen("person_detail_full", variant, tall = true) {
        Detail(
            PersonDetailUiState.Loaded(
                data.fullProfile,
                today = ScreenshotToday,
                interactions = data.interactions,
                due = keepInTouch(Cadence.days(30), lastDaysAgo = 12).on(ScreenshotToday),
            ),
        )
    }

    /** No Cadence and no Interactions: "No interactions yet" in the header and the section. */
    @Test
    fun personDetailSparse() = captureScreen("person_detail_sparse", variant, tall = true) {
        Detail(
            PersonDetailUiState.Loaded(
                data.sparseProfile,
                today = ScreenshotToday,
                due = keepInTouch(cadence = null, lastDaysAgo = null).on(ScreenshotToday),
            ),
        )
    }

    /** Due: the wavy progress full, **Due** and Snooze. */
    @Test
    fun personDetailDue() = captureScreen("person_detail_due", variant) {
        Detail(
            PersonDetailUiState.Loaded(
                data.dueProfile,
                today = ScreenshotToday,
                interactions = data.interactions.drop(1),
                due = keepInTouch(Cadence.days(30), lastDaysAgo = 34).on(ScreenshotToday),
            ),
        )
    }

    /** Snoozed: "Snoozed until 4 Nov · Cancel snooze". */
    @Test
    fun personDetailSnoozed() = captureScreen("person_detail_snoozed", variant) {
        Detail(
            PersonDetailUiState.Loaded(
                data.dueProfile,
                today = ScreenshotToday,
                interactions = data.interactions.drop(1),
                due = keepInTouch(Cadence.days(30), lastDaysAgo = 34, snoozedUntil = ScreenshotToday.plusDays(7)).on(ScreenshotToday),
            ),
        )
    }

    @Test
    fun allInteractions() = captureScreen("all_interactions", variant, tall = true) {
        AllInteractionsScreen(
            uiState = AllInteractionsUiState.Loaded(
                personId = data.fullProfile.person.id,
                names = data.fullProfile.names,
                interactions = data.interactions,
                today = ScreenshotToday,
            ),
            onBack = {},
            onLogInteraction = {},
            onOpen = {},
        )
    }

    @Test
    fun personDetailArchived() = captureScreen("person_detail_archived", variant) {
        val profile = data.sparseProfile
        Detail(
            PersonDetailUiState.Loaded(
                profile.copy(person = profile.person.copy(archivedAt = Instant.EPOCH)),
                today = ScreenshotToday,
            ),
        )
    }

    @Test
    fun personFormNew() = captureScreen("person_form_new", variant) {
        PersonFormScreen(
            uiState = PersonFormUiState(
                isNew = true,
                isLoading = false,
                rings = data.rings,
                ringId = data.rings.last().id,
                visibleFields = PersonField.entries.filterNot(PersonField::isBehindMoreDetails).toSet(),
            ),
            fields = PersonFormFields(),
            actions = NoActions,
            onChoosePhoto = {},
            onClose = {},
            onDiscard = {},
        )
    }

    @Test
    fun personFormExpanded() = captureScreen("person_form_expanded", variant, tall = true) {
        val profile = data.fullProfile
        val fields = PersonFormFields(
            name = TextFieldState(profile.person.name),
            nickname = TextFieldState(profile.person.nickname.orEmpty()),
            otherScriptName = TextFieldState(profile.person.otherScriptName.orEmpty()),
            howWeMet = TextFieldState(profile.person.howWeMet.orEmpty()),
            notes = TextFieldState(profile.person.notes.orEmpty()),
            tagInput = TextFieldState(data.tagInput),
        )
        PersonFormScreen(
            uiState = PersonFormUiState(
                isNew = false,
                isLoading = false,
                rings = data.rings,
                ringId = profile.person.ringId,
                cadence = CadenceSetting.Own(Cadence.weeks(2)),
                contacts = profile.contactMethods.take(2).mapIndexed { index, method ->
                    ContactRow(index.toLong(), method.type, method.isPrimary, method.value, method.label.orEmpty())
                },
                tags = profile.tags.map(Tag::name),
                importantDates = profile.importantDates.mapIndexed { index, date ->
                    DateRow(index.toLong(), ImportantDateDraft.of(date))
                },
                today = ScreenshotToday,
                items = mapOf(
                    PersonItemKind.CanHelpWith to profile.canHelpWith,
                    PersonItemKind.Like to profile.likes,
                    PersonItemKind.Dislike to profile.dislikes,
                ),
                visibleFields = PersonField.entries.toSet(),
                showsMoreDetails = false,
                hasChanges = true,
                canSave = true,
            ),
            fields = fields,
            actions = NoActions,
            onChoosePhoto = {},
            onClose = {},
            onDiscard = {},
        )
    }

    @Test
    fun photoCrop() = captureScreen("photo_crop", variant) {
        PhotoCropScreen(state = CropUiState.Ready(TestPattern), onCancel = {}, onDone = {})
    }

    @androidx.compose.runtime.Composable
    private fun People(uiState: PeopleUiState) {
        PeopleScreen(
            uiState = uiState,
            onPersonClick = {},
            onAddPerson = {},
            onContactAction = {},
            onLogInteraction = {},
            onSnooze = {},
        )
    }

    @androidx.compose.runtime.Composable
    private fun Detail(uiState: PersonDetailUiState) {
        PersonDetailScreen(
            uiState = uiState,
            keepInTouch = NoKeepInTouch,
            onBack = {},
            onEdit = {},
            onAddDetail = {},
            onArchive = {},
            onUnarchive = {},
            onDelete = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            edits = NoEdits,
            onMarkGiven = { _, _ -> },
            onOpenLink = {},
            onContactAction = {},
            onCopy = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}

private object NoActions : PersonFormActions {
    override fun selectRing(id: Long) = Unit
    override fun setCadence(setting: CadenceSetting) = Unit
    override fun showMoreDetails() = Unit
    override fun focusDone() = Unit
    override fun addContact() = Unit
    override fun removeContact(key: Long) = Unit
    override fun setPrimary(key: Long) = Unit
    override fun setContactType(key: Long, type: ContactType) = Unit
    override fun addImportantDate(type: ImportantDateType) = Unit
    override fun editImportantDate(key: Long) = Unit
    override fun saveImportantDate(editingKey: Long?, draft: ImportantDateDraft) = Unit
    override fun deleteImportantDate(key: Long) = Unit
    override fun dismissDateSheet() = Unit
    override fun submitTag() = Unit
    override fun pickTag(suggestion: TagSuggestion) = Unit
    override fun removeTag(name: String) = Unit
    override fun submitItem(kind: PersonItemKind) = Unit
    override fun pickItem(kind: PersonItemKind, text: String) = Unit
    override fun editItem(kind: PersonItemKind, index: Int) = Unit
    override fun removeItem(kind: PersonItemKind, index: Int) = Unit
    override fun removePhoto() = Unit
    override fun save() = Unit
    override fun saveAnyway() = Unit
    override fun dismissDialog() = Unit
}

private object NoKeepInTouch : PersonDetailKeepInTouch {
    override fun logInteraction() = Unit
    override fun openInteraction(interactionId: Long) = Unit
    override fun allInteractions() = Unit
    override fun snooze() = Unit
    override fun cancelSnooze() = Unit
}

/** Keep-in-touch facts on [ScreenshotToday]: added 200 days ago, last talked [lastDaysAgo] days ago. */
private fun keepInTouch(cadence: Cadence?, lastDaysAgo: Long?, snoozedUntil: LocalDate? = null) = KeepInTouch(
    cadence = cadence,
    addedOn = ScreenshotToday.minusDays(200),
    lastInteraction = lastDaysAgo?.let(ScreenshotToday::minusDays),
    snoozedUntil = snoozedUntil,
)

private object NoEdits : PersonDetailEdits {
    override fun saveImportantDate(draft: ImportantDateDraft) = Unit
    override fun deleteImportantDate(id: Long) = Unit
    override fun saveGiftIdea(draft: GiftIdeaDraft) = Unit
    override fun deleteGiftIdea(id: Long) = Unit
}

/** A 1200 × 800 gradient with a few discs: something to see under the crop circle. */
private object TestPattern : CropImage {
    override val width = 1200
    override val height = 800

    override fun imageBitmap(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), 0xFF5B4BD6.toInt(), 0xFFFF8A5B.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawCircle(600f, 380f, 160f, paint)
        paint.color = 0x99000000.toInt()
        canvas.drawCircle(300f, 600f, 90f, paint)
        canvas.drawCircle(950f, 200f, 120f, paint)
        return bitmap.asImageBitmap()
    }
}

private class ScreenshotData(
    val rings: List<Ring>,
    val people: List<PersonSummary>,
    val fullProfile: PersonProfile,
    val sparseProfile: PersonProfile,
    val tagInput: String,
    /** Sara's Interactions, newest first, some with [others]. */
    val interactions: List<InteractionWithPeople> = emptyList(),
) {
    /** The sparse Person in the Close Ring (every 30 days), for the Due and Snoozed headers. */
    val dueProfile: PersonProfile
        get() = sparseProfile.copy(person = sparseProfile.person.copy(ringId = rings[1].id), ring = rings[1])
}

/** Sara's Interactions: (type, days ago, others, note). */
private fun interactions(
    sara: Person,
    reza: Person,
    mina: Person,
    entries: List<InteractionEntry>,
) = entries.mapIndexed { index, (type, daysAgo, others, note) ->
    InteractionWithPeople(
        Interaction(id = index + 1L, type = type, date = ScreenshotToday.minusDays(daysAgo), note = note),
        listOf(sara) + others.map { if (it == 2) reza else mina },
    )
}

private data class InteractionEntry(val type: InteractionType, val daysAgo: Long, val others: List<Int> = emptyList(), val note: String? = null)

private fun otherPerson(id: Long, name: String) = Person(id = id, name = name, ringId = 1, createdAt = Instant.EPOCH)

/** An Important date of Person [id] on Gregorian [month]/[day] (for the date badge). */
private fun dateOn(id: Long, month: Int, day: Int) =
    ImportantDate(id = id * 10, personId = id, type = ImportantDateType.Birthday, date = RecurringDate(CalendarSystem.Gregorian, month, day), position = 0)

private fun ring(id: Long, name: String, days: Int?) =
    Ring(id = id, name = name, position = id.toInt() - 1, cadence = days?.let(Cadence::days))

/** A People-list row in [ring], last talked [lastDaysAgo] days ago (null: never; added 10 days ago). */
private fun summary(id: Long, name: String, other: String?, ring: Ring, lastDaysAgo: Long?, dates: List<ImportantDate> = emptyList()) =
    PersonSummary(
        id = id,
        names = PersonNames(name, other),
        photoFileName = null,
        ringName = ring.name,
        keepInTouch = KeepInTouch(
            cadence = ring.cadence,
            addedOn = ScreenshotToday.minusDays(10),
            lastInteraction = lastDaysAgo?.let(ScreenshotToday::minusDays),
        ),
        ringPosition = ring.position,
        importantDates = dates,
    )

private fun profile(
    name: String,
    otherScriptName: String?,
    nickname: String?,
    ring: Ring,
    tags: List<String> = emptyList(),
    contacts: List<Triple<ContactType, String, String?>> = emptyList(),
    canHelpWith: List<String> = emptyList(),
    likes: List<String> = emptyList(),
    dislikes: List<String> = emptyList(),
    howWeMet: String? = null,
    notes: String? = null,
    importantDates: List<Triple<ImportantDateType, String?, RecurringDate>> = emptyList(),
    giftIdeas: List<GiftIdea> = emptyList(),
) = PersonProfile(
    person = Person(
        id = 1,
        name = name,
        otherScriptName = otherScriptName,
        nickname = nickname,
        howWeMet = howWeMet,
        notes = notes,
        ringId = ring.id,
        createdAt = Instant.EPOCH,
    ),
    ring = ring,
    tags = tags.mapIndexed { index, tag -> Tag(index + 1L, tag) },
    contactMethods = contacts.mapIndexed { index, (type, value, label) ->
        ContactMethod(index + 1L, 1, type, value, label, isPrimary = index == 0, position = index)
    },
    canHelpWith = canHelpWith,
    likes = likes,
    dislikes = dislikes,
    importantDates = importantDates.mapIndexed { index, (type, label, date) ->
        ImportantDate(id = index + 1L, personId = 1, type = type, label = label, date = date, position = index)
    },
    giftIdeas = giftIdeas,
)

/** The day every screenshot is taken on: Wednesday 28 Oct 2026, 6 Aban 1405. */
internal val ScreenshotToday: LocalDate = LocalDate.of(2026, 10, 28)

private fun idea(id: Long, text: String, link: String? = null, note: String? = null, givenOn: LocalDate? = null) =
    GiftIdea(id = id, personId = 1, text = text, link = link, note = note, givenOn = givenOn, createdAt = Instant.ofEpochSecond(id))

private val EnglishRings = listOf(ring(1, "Inner", 14), ring(2, "Close", 30), ring(3, "Friends", 90), ring(4, "Acquaintances", null))
private val PersianRings = listOf(ring(1, "صمیمی", 14), ring(2, "نزدیک", 30), ring(3, "دوستان", 90), ring(4, "آشنایان", null))

private val EnglishData = ScreenshotData(
    rings = EnglishRings,
    people = listOf(
        summary(1, "Sara Ahmadi", "سارا احمدی", EnglishRings[1], lastDaysAgo = 34, dates = listOf(dateOn(1, 11, 2))),
        summary(2, "Ali Rezaei", null, EnglishRings[0], lastDaysAgo = 1),
        summary(3, "بابک", "Babak", EnglishRings[2], lastDaysAgo = null, dates = listOf(dateOn(3, 10, 28))),
        summary(4, "Mina Karimi", null, EnglishRings[3], lastDaysAgo = 95),
        summary(5, "Tom O'Neill", null, EnglishRings[2], lastDaysAgo = 800),
    ),
    fullProfile = profile(
        name = "Sara Ahmadi",
        otherScriptName = "سارا احمدی",
        nickname = "Sari",
        ring = EnglishRings[1],
        tags = listOf("University", "Book club", "Berlin"),
        contacts = listOf(
            Triple(ContactType.Phone, "+49 151 2345 6789", "mobile"),
            Triple(ContactType.WhatsApp, "+49 151 2345 6789", null),
            Triple(ContactType.Email, "sara.ahmadi@example.com", "work"),
            Triple(ContactType.Instagram, "@sara.draws", null),
            Triple(ContactType.Address, "Torstraße 1, Berlin", "home"),
        ),
        canHelpWith = listOf("Photography", "German paperwork"),
        likes = listOf("Jazz", "Hiking", "Persian food", "Murakami"),
        dislikes = listOf("Cilantro", "Early mornings"),
        howWeMet = "Book club at the university, autumn 2019.",
        notes = "Allergic to cats. Prefers tea over coffee.",
        importantDates = listOf(
            Triple(ImportantDateType.Birthday, null, RecurringDate(CalendarSystem.SolarHijri, 1, 14, 1370)),
            Triple(ImportantDateType.Anniversary, null, RecurringDate(CalendarSystem.Gregorian, 6, 12, 2019)),
            Triple(ImportantDateType.Custom, "Moved to Berlin", RecurringDate(CalendarSystem.SolarHijri, 7, 1)),
        ),
        giftIdeas = listOf(
            idea(1, "Fountain pen (Lamy 2000)", link = "lamy.com/2000", note = "Black, fine nib"),
            idea(2, "Illustrated Divan of Hafez"),
            idea(3, "Silk scarf", givenOn = LocalDate.of(2026, 3, 20)),
        ),
    ),
    sparseProfile = profile(name = "Mina Karimi", otherScriptName = null, nickname = null, ring = EnglishRings[3]),
    tagInput = "",
    interactions = interactions(
        sara = otherPerson(1, "Sara Ahmadi"),
        reza = otherPerson(2, "Reza Tehrani"),
        mina = otherPerson(3, "Mina Karimi"),
        entries = listOf(
            InteractionEntry(InteractionType.Call, 12, note = "Talked about the new job in Hamburg"),
            InteractionEntry(InteractionType.InPerson, 30, others = listOf(2, 3), note = "Dinner at Mina's. Sara brought the photos from Yazd"),
            InteractionEntry(InteractionType.Message, 45),
            InteractionEntry(InteractionType.Other, 70, note = "Sent a birthday card"),
            InteractionEntry(InteractionType.Call, 100),
            InteractionEntry(InteractionType.InPerson, 150, others = listOf(2)),
            InteractionEntry(InteractionType.Message, 400, note = "Happy Nowruz"),
        ),
    ),
)

private val PersianData = ScreenshotData(
    rings = PersianRings,
    people = listOf(
        summary(1, "سارا احمدی", "Sara Ahmadi", PersianRings[1], lastDaysAgo = 34, dates = listOf(dateOn(1, 11, 2))),
        summary(2, "علی رضایی", null, PersianRings[0], lastDaysAgo = 1),
        summary(3, "Babak", "بابک", PersianRings[2], lastDaysAgo = null, dates = listOf(dateOn(3, 10, 28))),
        summary(4, "مینا کریمی", null, PersianRings[3], lastDaysAgo = 95),
        summary(5, "Tom O'Neill", null, PersianRings[2], lastDaysAgo = 800),
        summary(6, "پریا", null, PersianRings[2], lastDaysAgo = 20),
    ),
    fullProfile = profile(
        name = "سارا احمدی",
        otherScriptName = "Sara Ahmadi",
        nickname = "ساری",
        ring = PersianRings[1],
        tags = listOf("دانشگاه", "باشگاه کتاب", "برلین"),
        contacts = listOf(
            Triple(ContactType.Phone, "+49 151 2345 6789", "همراه"),
            Triple(ContactType.WhatsApp, "+49 151 2345 6789", null),
            Triple(ContactType.Email, "sara.ahmadi@example.com", "کار"),
            Triple(ContactType.Instagram, "@sara.draws", null),
            Triple(ContactType.Address, "تهران، خیابان ولیعصر، پلاک ۱۲", "خانه"),
        ),
        canHelpWith = listOf("عکاسی", "کارهای اداری آلمان"),
        likes = listOf("جاز", "کوه‌پیمایی", "غذای ایرانی", "موراکامی"),
        dislikes = listOf("گشنیز", "سحرخیزی"),
        howWeMet = "باشگاه کتاب دانشگاه، پاییز ۱۳۹۸.",
        notes = "به گربه حساسیت دارد. چای را به قهوه ترجیح می‌دهد.",
        importantDates = listOf(
            Triple(ImportantDateType.Birthday, null, RecurringDate(CalendarSystem.SolarHijri, 1, 14, 1370)),
            Triple(ImportantDateType.Anniversary, null, RecurringDate(CalendarSystem.Gregorian, 6, 12, 2019)),
            Triple(ImportantDateType.Custom, "مهاجرت به برلین", RecurringDate(CalendarSystem.SolarHijri, 7, 1)),
        ),
        giftIdeas = listOf(
            idea(1, "خودنویس لامی ۲۰۰۰", link = "lamy.com/2000", note = "مشکی، نوک ریز"),
            idea(2, "دیوان مصور حافظ"),
            idea(3, "شال ابریشمی", givenOn = LocalDate.of(2026, 3, 20)),
        ),
    ),
    sparseProfile = profile(name = "مینا کریمی", otherScriptName = null, nickname = null, ring = PersianRings[3]),
    tagInput = "",
    interactions = interactions(
        sara = otherPerson(1, "سارا احمدی"),
        reza = otherPerson(2, "رضا تهرانی"),
        mina = otherPerson(3, "مینا کریمی"),
        entries = listOf(
            InteractionEntry(InteractionType.Call, 12, note = "دربارهٔ کار تازه‌اش در هامبورگ حرف زدیم"),
            InteractionEntry(InteractionType.InPerson, 30, others = listOf(2, 3), note = "شام خانهٔ مینا. سارا عکس‌های یزد را آورد"),
            InteractionEntry(InteractionType.Message, 45),
            InteractionEntry(InteractionType.Other, 70, note = "کارت تبریک تولد فرستادم"),
            InteractionEntry(InteractionType.Call, 100),
            InteractionEntry(InteractionType.InPerson, 150, others = listOf(2)),
            InteractionEntry(InteractionType.Message, 400, note = "نوروز مبارک"),
        ),
    ),
)
