package dev.alializadeh.orbit.person

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.person.contact.ContactType
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

    @Test
    fun peopleList() = captureScreen("people_list", variant) {
        PeopleScreen(
            uiState = PeopleUiState.Loaded(data.people.map { PersonRow(it, photo = null) }),
            onPersonClick = {},
            onAddPerson = {},
        )
    }

    @Test
    fun peopleListEmpty() = captureScreen("people_list_empty", variant) {
        PeopleScreen(uiState = PeopleUiState.Loaded(emptyList()), onPersonClick = {}, onAddPerson = {})
    }

    @Test
    fun personDetailFull() = captureScreen("person_detail_full", variant, tall = true) {
        Detail(PersonDetailUiState.Loaded(data.fullProfile))
    }

    @Test
    fun personDetailSparse() = captureScreen("person_detail_sparse", variant) {
        Detail(PersonDetailUiState.Loaded(data.sparseProfile))
    }

    @Test
    fun personDetailArchived() = captureScreen("person_detail_archived", variant) {
        val profile = data.sparseProfile
        Detail(PersonDetailUiState.Loaded(profile.copy(person = profile.person.copy(archivedAt = Instant.EPOCH))))
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
    private fun Detail(uiState: PersonDetailUiState) {
        PersonDetailScreen(
            uiState = uiState,
            onBack = {},
            onEdit = {},
            onAddDetail = {},
            onArchive = {},
            onUnarchive = {},
            onDelete = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
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
)

private fun ring(id: Long, name: String, days: Int?) =
    Ring(id = id, name = name, position = id.toInt() - 1, cadence = days?.let(Cadence::days))

private fun summary(id: Long, name: String, other: String?, ring: String) =
    PersonSummary(id, PersonNames(name, other), photoFileName = null, ringName = ring)

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
)

private val EnglishRings = listOf(ring(1, "Inner", 14), ring(2, "Close", 30), ring(3, "Friends", 90), ring(4, "Acquaintances", null))
private val PersianRings = listOf(ring(1, "صمیمی", 14), ring(2, "نزدیک", 30), ring(3, "دوستان", 90), ring(4, "آشنایان", null))

private val EnglishData = ScreenshotData(
    rings = EnglishRings,
    people = listOf(
        summary(1, "Sara Ahmadi", "سارا احمدی", "Close"),
        summary(2, "Ali Rezaei", null, "Inner"),
        summary(3, "بابک", "Babak", "Friends"),
        summary(4, "Mina Karimi", null, "Acquaintances"),
        summary(5, "Tom O'Neill", null, "Friends"),
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
    ),
    sparseProfile = profile(name = "Mina Karimi", otherScriptName = null, nickname = null, ring = EnglishRings[3]),
    tagInput = "",
)

private val PersianData = ScreenshotData(
    rings = PersianRings,
    people = listOf(
        summary(1, "سارا احمدی", "Sara Ahmadi", "نزدیک"),
        summary(2, "علی رضایی", null, "صمیمی"),
        summary(3, "Babak", "بابک", "دوستان"),
        summary(4, "مینا کریمی", null, "آشنایان"),
        summary(5, "Tom O'Neill", null, "دوستان"),
        summary(6, "پریا", null, "دوستان"),
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
    ),
    sparseProfile = profile(name = "مینا کریمی", otherScriptName = null, nickname = null, ring = PersianRings[3]),
    tagInput = "",
)
