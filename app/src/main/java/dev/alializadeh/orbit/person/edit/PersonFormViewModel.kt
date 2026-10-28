package dev.alializadeh.orbit.person.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.person.CadenceSetting
import dev.alializadeh.orbit.person.ContactMethodDraft
import dev.alializadeh.orbit.person.ImportantDateDraft
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonDraft
import dev.alializadeh.orbit.person.PersonField
import dev.alializadeh.orbit.person.PersonItem
import dev.alializadeh.orbit.person.PersonItemKind
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.person.nameKey
import dev.alializadeh.orbit.person.photo.CropImage
import dev.alializadeh.orbit.person.photo.CropRect
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.reminders.ReminderPermissionAsks
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingRepository
import dev.alializadeh.orbit.ring.outermost
import java.io.File
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One Contact method row of the form. Snapshot state, so the form recomposes as it's edited. */
@Stable
class ContactRow(val key: Long, type: ContactType, isPrimary: Boolean, value: String = "", label: String = "") {
    var type: ContactType by mutableStateOf(type)
    var isPrimary: Boolean by mutableStateOf(isPrimary)
    val value = TextFieldState(value)
    val label = TextFieldState(label)

    fun draft() = ContactMethodDraft(type, value.text.toString(), label.text.toString(), isPrimary)
}

/** One Important date row of the form; [key] is the form's own, stable while it's open. */
data class DateRow(val key: Long, val draft: ImportantDateDraft)

/** The Important date sheet over the form: editing the row [editingKey], or a new date of [newType]. */
data class DateSheet(val editingKey: Long?, val newType: ImportantDateType)

/** A line under the Tags field: an existing Tag, or Create "…" for a new one. */
data class TagSuggestion(val name: String, val isNew: Boolean)

/** A dialog the form is showing. */
sealed interface FormDialog {
    data object Discard : FormDialog

    /** The duplicate-name notice for [name]. */
    data class DuplicateName(val name: String) : FormDialog
}

/** The crop screen over the form, from picking a photo until it's saved or cancelled. */
sealed interface CropUiState {
    data object Loading : CropUiState

    data class Ready(val image: CropImage) : CropUiState

    data object Failed : CropUiState

    data object Saving : CropUiState
}

data class PersonFormUiState(
    val isNew: Boolean,
    val isLoading: Boolean = true,
    /** Editing a Person who no longer exists: the screen leaves. */
    val notFound: Boolean = false,
    val rings: List<Ring> = emptyList(),
    val ringId: Long? = null,
    val cadence: CadenceSetting = CadenceSetting.FromRing,
    val photo: File? = null,
    val contacts: List<ContactRow> = emptyList(),
    /** Every Important date, the Birthday first. */
    val importantDates: List<DateRow> = emptyList(),
    val dateSheet: DateSheet? = null,
    val defaultLeadTimes: Set<LeadTime> = LeadTime.DEFAULT,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    /** The row just added, whose value field takes focus. */
    val newContactKey: Long? = null,
    val tags: List<String> = emptyList(),
    val tagSuggestions: List<TagSuggestion> = emptyList(),
    val items: Map<PersonItemKind, List<String>> = emptyMap(),
    val itemSuggestions: Map<PersonItemKind, List<String>> = emptyMap(),
    /** The item whose text is in its Add field being edited. */
    val editingItem: ItemRef? = null,
    val visibleFields: Set<PersonField> = emptySet(),
    val showsMoreDetails: Boolean = true,
    /** The field to scroll to and focus once (an Add details chip), then null. */
    val focus: PersonField? = null,
    val canSave: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: FormDialog? = null,
    val crop: CropUiState? = null,
    /** Set once saved; the screen then leaves (Add lands on the new Person's detail). */
    val savedPersonId: Long? = null,
) {
    val selectedRing: Ring? get() = rings.firstOrNull { it.id == ringId }

    val birthday: DateRow? get() = importantDates.firstOrNull { it.draft.type == ImportantDateType.Birthday }

    /** The Important dates other than the Birthday, behind More details. */
    val otherDates: List<DateRow> get() = importantDates.filter { it.draft.type != ImportantDateType.Birthday }
}

data class ItemRef(val kind: PersonItemKind, val index: Int)

/** The form's text fields, held by the ViewModel. */
class PersonFormFields(
    val name: TextFieldState = TextFieldState(),
    val nickname: TextFieldState = TextFieldState(),
    val otherScriptName: TextFieldState = TextFieldState(),
    val howWeMet: TextFieldState = TextFieldState(),
    val notes: TextFieldState = TextFieldState(),
    val tagInput: TextFieldState = TextFieldState(),
    val itemInputs: Map<PersonItemKind, TextFieldState> = PersonItemKind.entries.associateWith { TextFieldState() },
)

/** What the form's controls do; the ViewModel implements it, screenshot tests pass a no-op. */
interface PersonFormActions {
    fun selectRing(id: Long)
    fun setCadence(setting: CadenceSetting)
    fun showMoreDetails()
    fun focusDone()
    fun addContact()
    fun removeContact(key: Long)
    fun setPrimary(key: Long)
    fun setContactType(key: Long, type: ContactType)
    fun addImportantDate(type: ImportantDateType)
    fun editImportantDate(key: Long)
    fun saveImportantDate(editingKey: Long?, draft: ImportantDateDraft)
    fun deleteImportantDate(key: Long)
    fun dismissDateSheet()
    fun submitTag()
    fun pickTag(suggestion: TagSuggestion)
    fun removeTag(name: String)
    fun submitItem(kind: PersonItemKind)
    fun pickItem(kind: PersonItemKind, text: String)
    fun editItem(kind: PersonItemKind, index: Int)
    fun removeItem(kind: PersonItemKind, index: Int)
    fun removePhoto()
    fun save()
    fun saveAnyway()
    fun dismissDialog()
}

/**
 * The Add/Edit Person form. Text lives in [TextFieldState]s and the rest in snapshot state, all
 * read by one `snapshotFlow` into [uiState]. Changes are detected by comparing the normalised
 * [PersonDraft] with the one the form opened with. Photos cropped here belong to the form until
 * it's saved: leaving without saving deletes them.
 */
@HiltViewModel(assistedFactory = PersonFormViewModel.Factory::class)
class PersonFormViewModel @AssistedInject constructor(
    @Assisted private val personId: Long?,
    @Assisted focus: PersonField?,
    private val personRepository: PersonRepository,
    ringRepository: RingRepository,
    private val photoStore: PhotoStore,
    preferencesRepository: PreferencesRepository,
    clock: Clock,
    private val reminderAsks: ReminderPermissionAsks,
) : ViewModel(), PersonFormActions {

    private val today: LocalDate = LocalDate.now(clock)

    val fields = PersonFormFields()
    private val name get() = fields.name
    private val nickname get() = fields.nickname
    private val otherScriptName get() = fields.otherScriptName
    private val howWeMet get() = fields.howWeMet
    private val notes get() = fields.notes
    private val tagInput get() = fields.tagInput
    private val itemInputs get() = fields.itemInputs

    private val contacts = mutableStateListOf<ContactRow>()
    private val importantDates = mutableStateListOf<DateRow>()
    private var dateSheet by mutableStateOf<DateSheet?>(null)
    private var defaultLeadTimes by mutableStateOf(LeadTime.DEFAULT)
    private val tags = mutableStateListOf<String>()
    private val items = PersonItemKind.entries.associateWith { mutableStateListOf<String>() }
    private var ringId by mutableStateOf<Long?>(null)
    private var cadenceSetting by mutableStateOf<CadenceSetting>(CadenceSetting.FromRing)
    private var photoFileName by mutableStateOf<String?>(null)
    private var editingItem by mutableStateOf<ItemRef?>(null)
    private var revealed by mutableStateOf(setOfNotNull(focus))
    private var moreDetailsOpen by mutableStateOf(false)
    private var pendingFocus by mutableStateOf(focus)
    private var newContactKey by mutableStateOf<Long?>(null)
    private var dialog by mutableStateOf<FormDialog?>(null)
    private var crop by mutableStateOf<CropUiState?>(null)
    private var progress by mutableStateOf<Progress>(Progress.Loading)
    private var knownTags by mutableStateOf<List<Tag>>(emptyList())

    private var initialDraft: PersonDraft? = null
    private var nextContactKey = 1L
    private var nextDateKey = 1L

    /** Photos cropped while the form is open and not saved yet. */
    private val pendingPhotos = mutableSetOf<String>()

    private val itemSuggestionSources: Flow<Map<PersonItemKind, List<String>>> = combine(
        PersonItemKind.entries.map { kind ->
            personRepository.observeItemSuggestions(kind, personId)
        },
    ) { lists -> PersonItemKind.entries.zip(lists).toMap() }

    val uiState: StateFlow<PersonFormUiState> = combine(
        snapshotFlow { snapshot() },
        ringRepository.observeRings(),
        itemSuggestionSources,
    ) { form, rings, itemTexts ->
        form.copy(
            rings = rings,
            itemSuggestions = PersonItemKind.entries.associateWith { kind ->
                itemSuggestions(kind, itemTexts[kind].orEmpty())
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonFormUiState(isNew = personId == null))

    init {
        viewModelScope.launch { personRepository.observeTags().collect { knownTags = it } }
        viewModelScope.launch { preferencesRepository.preferences.collect { defaultLeadTimes = it.defaultLeadTimes } }
        viewModelScope.launch {
            if (personId == null) {
                ringId = ringRepository.observeRings().first().outermost()?.id
            } else {
                val profile = personRepository.getProfile(personId)
                if (profile == null) {
                    progress = Progress.NotFound
                    return@launch
                }
                fill(PersonDraft.of(profile))
            }
            initialDraft = currentDraft()
            progress = Progress.Editing
            // The Add details chips for dates open the date sheet at once.
            when (focus) {
                PersonField.Birthday -> addImportantDate(ImportantDateType.Birthday)
                PersonField.ImportantDates -> addImportantDate(ImportantDateType.Anniversary)
                else -> Unit
            }
        }
    }

    private fun fill(draft: PersonDraft) {
        name.setTextAndPlaceCursorAtEnd(draft.name)
        nickname.setTextAndPlaceCursorAtEnd(draft.nickname.orEmpty())
        otherScriptName.setTextAndPlaceCursorAtEnd(draft.otherScriptName.orEmpty())
        howWeMet.setTextAndPlaceCursorAtEnd(draft.howWeMet.orEmpty())
        notes.setTextAndPlaceCursorAtEnd(draft.notes.orEmpty())
        ringId = draft.ringId
        cadenceSetting = draft.cadence
        photoFileName = draft.photoFileName
        tags.addAll(draft.tags)
        PersonItemKind.entries.forEach { items.getValue(it).addAll(draft.items(it)) }
        draft.contactMethods.forEach {
            contacts += ContactRow(nextContactKey++, it.type, it.isPrimary, it.value, it.label.orEmpty())
        }
        draft.importantDates.forEach { importantDates += DateRow(nextDateKey++, it) }
        // Edit opens with every field that has content.
        revealed = revealed + PersonField.moreDetails.filter { field -> hasContent(field, draft) }
    }

    private fun hasContent(field: PersonField, draft: PersonDraft): Boolean = when (field) {
        PersonField.Photo -> draft.photoFileName != null
        PersonField.ContactMethods -> draft.contactMethods.isNotEmpty()
        PersonField.Birthday -> draft.importantDates.any { it.type == ImportantDateType.Birthday }
        PersonField.ImportantDates -> draft.importantDates.any { it.type != ImportantDateType.Birthday }
        PersonField.Nickname -> !draft.nickname.isNullOrBlank()
        PersonField.OtherScriptName -> !draft.otherScriptName.isNullOrBlank()
        PersonField.Tags -> draft.tags.isNotEmpty()
        PersonField.CanHelpWith -> draft.canHelpWith.isNotEmpty()
        PersonField.Likes -> draft.likes.isNotEmpty()
        PersonField.Dislikes -> draft.dislikes.isNotEmpty()
        PersonField.HowWeMet -> !draft.howWeMet.isNullOrBlank()
        PersonField.Notes -> !draft.notes.isNullOrBlank()
    }

    /** The draft as the form stands, counting text typed into an Add field but not yet added. */
    private fun currentDraft(): PersonDraft {
        val pendingTag = tagInput.text.toString().trim()
        return PersonDraft(
            id = personId,
            name = name.text.toString(),
            nickname = nickname.text.toString(),
            otherScriptName = otherScriptName.text.toString(),
            photoFileName = photoFileName,
            howWeMet = howWeMet.text.toString(),
            notes = notes.text.toString(),
            ringId = ringId,
            cadence = cadenceSetting,
            tags = if (pendingTag.isEmpty()) tags.toList() else tags + canonicalTag(pendingTag),
            contactMethods = contacts.map(ContactRow::draft),
            canHelpWith = itemsWithPending(PersonItemKind.CanHelpWith),
            likes = itemsWithPending(PersonItemKind.Like),
            dislikes = itemsWithPending(PersonItemKind.Dislike),
            importantDates = sortedDates().map(DateRow::draft),
        )
    }

    /** The Birthday first, then the other dates in the order they were added. */
    private fun sortedDates(): List<DateRow> = importantDates.sortedBy { it.draft.type != ImportantDateType.Birthday }

    private fun birthdayRow(): DateRow? = importantDates.firstOrNull { it.draft.type == ImportantDateType.Birthday }

    private fun itemsWithPending(kind: PersonItemKind): List<String> {
        val list = items.getValue(kind).toList()
        val pending = itemInputs.getValue(kind).text.toString().trim().take(PersonItem.MAX_LENGTH)
        val editing = editingItem?.takeIf { it.kind == kind }
        return when {
            editing != null && editing.index in list.indices ->
                list.toMutableList().apply { if (pending.isEmpty()) removeAt(editing.index) else set(editing.index, pending) }
            pending.isEmpty() -> list
            else -> list + pending
        }
    }

    private fun snapshot(): PersonFormUiState {
        val progress = progress
        val draft = currentDraft()
        val loaded = progress != Progress.Loading && progress != Progress.NotFound
        val hasChanges = loaded && draft.normalised() != initialDraft?.normalised()
        return PersonFormUiState(
            isNew = personId == null,
            isLoading = !loaded,
            notFound = progress == Progress.NotFound,
            ringId = ringId,
            cadence = cadenceSetting,
            photo = photoFileName?.let(photoStore::file),
            contacts = contacts.toList(),
            importantDates = sortedDates(),
            dateSheet = dateSheet,
            defaultLeadTimes = defaultLeadTimes,
            today = today,
            newContactKey = newContactKey,
            tags = tags.toList(),
            tagSuggestions = tagSuggestions(),
            items = items.mapValues { it.value.toList() },
            editingItem = editingItem,
            visibleFields = PersonField.entries.filter { !it.isBehindMoreDetails || moreDetailsOpen || it in revealed }
                .toSet(),
            showsMoreDetails = !moreDetailsOpen && !PersonField.moreDetails.all { it in revealed },
            focus = pendingFocus,
            canSave = progress == Progress.Editing && draft.name.isNotBlank() && ringId != null,
            hasChanges = hasChanges,
            dialog = dialog,
            crop = crop,
            savedPersonId = (progress as? Progress.Saved)?.personId,
        )
    }

    // Ring and Cadence

    override fun selectRing(id: Long) {
        ringId = id
    }

    override fun setCadence(setting: CadenceSetting) {
        cadenceSetting = setting
    }

    override fun showMoreDetails() {
        moreDetailsOpen = true
    }

    /** The field asked for by [PersonFormUiState.focus] has been scrolled to. */
    override fun focusDone() {
        pendingFocus = null
    }

    // Contact methods

    /** Adds an empty Phone row; the first row becomes the primary. */
    override fun addContact() {
        val row = ContactRow(nextContactKey++, ContactType.Phone, isPrimary = contacts.none(ContactRow::isPrimary))
        contacts += row
        newContactKey = row.key
    }

    /** Removes a row; if it had the star, the next row (or else the last) gets it. */
    override fun removeContact(key: Long) {
        val index = contacts.indexOfFirst { it.key == key }
        if (index < 0) return
        val removed = contacts.removeAt(index)
        if (removed.isPrimary && contacts.isNotEmpty()) contacts[index.coerceAtMost(contacts.lastIndex)].isPrimary = true
    }

    override fun setPrimary(key: Long) {
        contacts.forEach { it.isPrimary = it.key == key }
    }

    override fun setContactType(key: Long, type: ContactType) {
        contacts.firstOrNull { it.key == key }?.type = type
    }

    // Important dates

    /** Add birthday or Add important date: the date sheet for a new date of [type]. */
    override fun addImportantDate(type: ImportantDateType) {
        // There's at most one Birthday: asking for another edits the one there is.
        val birthday = birthdayRow()
        dateSheet = if (type == ImportantDateType.Birthday && birthday != null) {
            DateSheet(editingKey = birthday.key, newType = type)
        } else {
            DateSheet(editingKey = null, newType = type)
        }
    }

    override fun editImportantDate(key: Long) {
        val row = importantDates.firstOrNull { it.key == key } ?: return
        dateSheet = DateSheet(editingKey = row.key, newType = row.draft.type)
    }

    /** Done on the date sheet: updates the row [editingKey], or adds a new one. */
    override fun saveImportantDate(editingKey: Long?, draft: ImportantDateDraft) {
        dateSheet = null
        val d = draft.normalised()
        val index = importantDates.indexOfFirst { it.key == editingKey }
        val otherBirthday = importantDates.any { it.draft.type == ImportantDateType.Birthday && it.key != editingKey }
        // The sheet hides Birthday when there's one already; never keep a second.
        if (d.type == ImportantDateType.Birthday && otherBirthday) return
        if (index >= 0) {
            importantDates[index] = importantDates[index].copy(draft = d.copy(id = importantDates[index].draft.id))
        } else {
            importantDates += DateRow(nextDateKey++, d.copy(id = null))
        }
    }

    /** Delete on the date sheet. */
    override fun deleteImportantDate(key: Long) {
        dateSheet = null
        importantDates.removeAll { it.key == key }
    }

    override fun dismissDateSheet() {
        dateSheet = null
    }

    // Tags

    private fun tagSuggestions(): List<TagSuggestion> {
        val allTags = knownTags
        val input = tagInput.text.toString().trim()
        if (input.isEmpty()) return emptyList()
        val chosen = tags.mapTo(HashSet()) { it.lowercase() }
        val matches = allTags
            .filter { it.name.contains(input, ignoreCase = true) && it.name.lowercase() !in chosen }
            .take(MAX_SUGGESTIONS)
            .map { TagSuggestion(it.name, isNew = false) }
        val exists = input.lowercase() in chosen || allTags.any { it.name.equals(input, ignoreCase = true) }
        if (exists) return matches
        val create = TagSuggestion(input, isNew = true)
        // When nothing matches, Create "…" is the top suggestion.
        return if (matches.isEmpty()) listOf(create) else matches + create
    }

    /** An existing Tag's own spelling for [name] if there is one ("gym" → "Gym"). */
    private fun canonicalTag(name: String): String =
        (tags + knownTags.map(Tag::name)).firstOrNull { it.equals(name, ignoreCase = true) }
            ?: name

    /** Enter in the Tags field: adds the typed Tag. */
    override fun submitTag() {
        val input = tagInput.text.toString().trim()
        if (input.isNotEmpty()) addTag(canonicalTag(input))
        tagInput.clearText()
    }

    override fun pickTag(suggestion: TagSuggestion) {
        addTag(suggestion.name)
        tagInput.clearText()
    }

    private fun addTag(name: String) {
        if (tags.none { it.equals(name, ignoreCase = true) }) tags += name
    }

    override fun removeTag(name: String) {
        tags.remove(name)
    }

    // Likes, Dislikes and Can help with

    private fun itemSuggestions(kind: PersonItemKind, usedElsewhere: List<String>): List<String> {
        val input = itemInputs.getValue(kind).text.toString().trim()
        if (input.isEmpty()) return emptyList()
        val current = items.getValue(kind).mapTo(HashSet()) { it.lowercase() }
        return usedElsewhere
            .filter { it.contains(input, ignoreCase = true) && it.lowercase() !in current && !it.equals(input, true) }
            .take(MAX_SUGGESTIONS)
    }

    /** Enter in an Add… field: adds the item, or saves the one being edited. */
    override fun submitItem(kind: PersonItemKind) {
        val input = itemInputs.getValue(kind)
        val text = input.text.toString().trim().take(PersonItem.MAX_LENGTH)
        val list = items.getValue(kind)
        val editing = editingItem?.takeIf { it.kind == kind }
        when {
            editing != null && editing.index in list.indices ->
                if (text.isEmpty()) list.removeAt(editing.index) else list[editing.index] = text
            text.isNotEmpty() && list.none { it.equals(text, ignoreCase = true) } -> list += text
        }
        if (editing != null) editingItem = null
        input.clearText()
    }

    override fun pickItem(kind: PersonItemKind, text: String) {
        itemInputs.getValue(kind).setTextAndPlaceCursorAtEnd(text)
        submitItem(kind)
    }

    /** Tapping a chip puts its text in the Add… field to edit it there. */
    override fun editItem(kind: PersonItemKind, index: Int) {
        val list = items.getValue(kind)
        if (index !in list.indices) return
        editingItem = ItemRef(kind, index)
        itemInputs.getValue(kind).setTextAndPlaceCursorAtEnd(list[index])
    }

    override fun removeItem(kind: PersonItemKind, index: Int) {
        val list = items.getValue(kind)
        if (index !in list.indices) return
        list.removeAt(index)
        val editing = editingItem?.takeIf { it.kind == kind } ?: return
        when {
            editing.index == index -> {
                editingItem = null
                itemInputs.getValue(kind).clearText()
            }
            editing.index > index -> editingItem = editing.copy(index = editing.index - 1)
        }
    }

    // Photo

    /** A photo was picked: decode it for the crop screen. */
    fun startCrop(uri: String) {
        crop = CropUiState.Loading
        viewModelScope.launch {
            val image = photoStore.loadForCrop(uri)
            crop = if (image == null) CropUiState.Failed else CropUiState.Ready(image)
        }
    }

    fun cancelCrop() {
        crop = null
    }

    fun finishCrop(rect: CropRect) {
        val ready = crop as? CropUiState.Ready ?: return
        crop = CropUiState.Saving
        viewModelScope.launch {
            val fileName = photoStore.saveCrop(ready.image, rect)
            replacePhoto(fileName)
            crop = null
        }
    }

    override fun removePhoto() = replacePhoto(null)

    private fun replacePhoto(fileName: String?) {
        photoFileName?.let { old ->
            // A photo cropped in this form and never saved goes at once; the saved one only
            // once the change is saved.
            if (pendingPhotos.remove(old)) photoStore.delete(old)
        }
        photoFileName = fileName
        if (fileName != null) pendingPhotos += fileName
    }

    // Leaving and saving

    /** Back or the close button: true when the form can just close, else it asks "Discard changes?". */
    fun requestClose(): Boolean {
        if (!snapshot().hasChanges) return true
        dialog = FormDialog.Discard
        return false
    }

    override fun dismissDialog() {
        dialog = null
    }

    /** "Discard": the screen closes; photos cropped here are deleted. */
    fun discard() {
        dialog = null
        deletePendingPhotos()
    }

    override fun save() {
        if (!snapshot().canSave) return
        val draft = currentDraft()
        progress = Progress.Saving
        viewModelScope.launch {
            val initialNames = listOfNotNull(initialDraft?.name, initialDraft?.otherScriptName).map(::nameKey)
            val changedNames = listOfNotNull(draft.name, draft.otherScriptName)
                .filter { it.isNotBlank() && nameKey(it) !in initialNames }
            val clash = personRepository.findNameClash(changedNames, personId)
            if (clash != null) {
                progress = Progress.Editing
                dialog = FormDialog.DuplicateName(clash.trim())
            } else {
                store(draft)
            }
        }
    }

    /** "Save anyway" on the duplicate-name notice. */
    override fun saveAnyway() {
        dialog = null
        if (progress != Progress.Editing) return
        progress = Progress.Saving
        val draft = currentDraft()
        viewModelScope.launch { store(draft) }
    }

    private suspend fun store(draft: PersonDraft) {
        val id = personRepository.save(draft)
        // The first save that reminders are for asks for notifications (ticket 07).
        val ringCadence = uiState.value.rings.firstOrNull { it.id == draft.ringId }?.cadence
        val cadence = when (val setting = draft.cadence) {
            CadenceSetting.FromRing -> ringCadence
            is CadenceSetting.Own -> setting.cadence
        }
        if (draft.importantDates.isNotEmpty() || cadence != null) reminderAsks.remindersNeeded()
        pendingPhotos.clear()
        progress = Progress.Saved(id)
    }

    private fun deletePendingPhotos() {
        pendingPhotos.forEach(photoStore::delete)
        pendingPhotos.clear()
    }

    override fun onCleared() {
        // Left without saving (or the process is going): cropped photos nobody refers to.
        if (progress !is Progress.Saved) deletePendingPhotos()
    }

    private sealed interface Progress {
        data object Loading : Progress
        data object NotFound : Progress
        data object Editing : Progress
        data object Saving : Progress
        data class Saved(val personId: Long) : Progress
    }

    @AssistedFactory
    interface Factory {
        fun create(personId: Long?, focus: PersonField?): PersonFormViewModel
    }

    private companion object {
        const val MAX_SUGGESTIONS = 5
    }
}
