package dev.alializadeh.orbit.person.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.due.DueState
import dev.alializadeh.orbit.due.keepInTouch
import dev.alializadeh.orbit.interaction.InteractionRepository
import dev.alializadeh.orbit.interaction.InteractionWithPeople
import dev.alializadeh.orbit.person.FollowUpDraft
import dev.alializadeh.orbit.person.GiftIdeaDraft
import dev.alializadeh.orbit.person.ImportantDateDraft
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.LoanDraft
import dev.alializadeh.orbit.person.PersonField
import dev.alializadeh.orbit.person.PersonProfile
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.reminders.ReminderPermissionAsks
import dev.alializadeh.orbit.upcoming.todayFlow
import java.io.File
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why Person detail is leaving on its own. */
enum class DetailExit { Archived, Deleted }

sealed interface PersonDetailUiState {
    data object Loading : PersonDetailUiState

    data class Loaded(
        val profile: PersonProfile,
        val photo: File? = null,
        /** Today, for occurrences, years counts and the year shown in dates. */
        val today: LocalDate = LocalDate.ofEpochDay(0),
        /** The default lead times, for the date sheet's Reminders line. */
        val defaultLeadTimes: Set<LeadTime> = LeadTime.DEFAULT,
        val confirmingDelete: Boolean = false,
        /** Set once Archive or Delete is done; the screen then leaves. */
        val exit: DetailExit? = null,
        /** Their Interactions, newest first (the section shows the latest [LATEST_INTERACTIONS]). */
        val interactions: List<InteractionWithPeople> = emptyList(),
        /** Where they stand with their Cadence: the header's Cadence line. */
        val due: DueState? = null,
    ) : PersonDetailUiState {
        /** What the Add details row offers: the fields with nothing in them yet. */
        val missingFields: List<PersonField>
            get() = PersonField.entries.filter { field ->
                val person = profile.person
                when (field) {
                    PersonField.Photo -> person.photoFileName == null
                    PersonField.ContactMethods -> profile.contactMethods.isEmpty()
                    PersonField.Birthday -> profile.birthday == null
                    PersonField.ImportantDates -> profile.importantDates.all { it.type == ImportantDateType.Birthday }
                    PersonField.Nickname -> person.nickname.isNullOrBlank()
                    PersonField.OtherScriptName -> person.otherScriptName.isNullOrBlank()
                    PersonField.Tags -> profile.tags.isEmpty()
                    PersonField.CanHelpWith -> profile.canHelpWith.isEmpty()
                    PersonField.Likes -> profile.likes.isEmpty()
                    PersonField.Dislikes -> profile.dislikes.isEmpty()
                    PersonField.HowWeMet -> person.howWeMet.isNullOrBlank()
                    PersonField.Notes -> person.notes.isNullOrBlank()
                }
            }
    }

    /** The Person was deleted, or never existed. */
    data object NotFound : PersonDetailUiState

    companion object {
        /** Person detail lists this many Interactions, then "All interactions (n)". */
        const val LATEST_INTERACTIONS = 5
    }
}

/** Changes Person detail makes in place: Important dates (edit, delete), Gift ideas, Follow-ups and Loans. */
interface PersonDetailEdits {
    fun saveImportantDate(draft: ImportantDateDraft)
    fun deleteImportantDate(id: Long)
    fun saveGiftIdea(draft: GiftIdeaDraft)
    fun deleteGiftIdea(id: Long)
    fun saveFollowUp(draft: FollowUpDraft)
    fun deleteFollowUp(id: Long)
    fun saveLoan(draft: LoanDraft)
    fun deleteLoan(id: Long)
}

@HiltViewModel(assistedFactory = PersonDetailViewModel.Factory::class)
class PersonDetailViewModel @AssistedInject constructor(
    @Assisted private val personId: Long,
    private val personRepository: PersonRepository,
    interactionRepository: InteractionRepository,
    private val photoStore: PhotoStore,
    preferencesRepository: PreferencesRepository,
    private val clock: Clock,
    private val reminderAsks: ReminderPermissionAsks,
) : ViewModel(), PersonDetailEdits {

    private val phase = MutableStateFlow(Phase.Showing)
    private val dateChecks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var lastLoaded: PersonDetailUiState.Loaded? = null

    val uiState: StateFlow<PersonDetailUiState> =
        combine(
            personRepository.observeProfile(personId),
            interactionRepository.observeForPerson(personId),
            phase,
            preferencesRepository.preferences.map { it.defaultLeadTimes },
            todayFlow(clock, dateChecks),
        ) { profile, interactions, phase, defaultLeadTimes, today ->
            val exit = when (phase) {
                Phase.Archived -> DetailExit.Archived
                Phase.Deleted -> DetailExit.Deleted
                else -> null
            }
            when {
                profile != null -> PersonDetailUiState.Loaded(
                    profile = profile,
                    photo = profile.person.photoFileName?.let(photoStore::file),
                    today = today,
                    defaultLeadTimes = defaultLeadTimes,
                    confirmingDelete = phase == Phase.ConfirmingDelete,
                    exit = exit,
                    interactions = interactions,
                    due = profile.person
                        .keepInTouch(profile.ring, interactions.maxOfOrNull { it.interaction.date }, clock.zone)
                        .on(today),
                ).also { lastLoaded = it }
                // Being deleted: keep showing them while the screen leaves.
                phase == Phase.Deleting || phase == Phase.Deleted ->
                    lastLoaded?.copy(confirmingDelete = false, exit = exit) ?: PersonDetailUiState.NotFound
                else -> PersonDetailUiState.NotFound
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailUiState.Loading)

    /** Undoes [archive]; safe to run after this ViewModel is gone (from the snackbar). */
    val undoArchive: suspend () -> Unit = { personRepository.setArchived(personId, archived = false) }

    fun archive() {
        viewModelScope.launch {
            personRepository.setArchived(personId, archived = true)
            phase.value = Phase.Archived
        }
    }

    fun unarchive() {
        viewModelScope.launch { personRepository.setArchived(personId, archived = false) }
    }

    fun askToDelete() {
        phase.value = Phase.ConfirmingDelete
    }

    fun dismissDelete() {
        if (phase.value == Phase.ConfirmingDelete) phase.value = Phase.Showing
    }

    fun delete() {
        phase.value = Phase.Deleting
        viewModelScope.launch {
            personRepository.delete(personId)
            phase.value = Phase.Deleted
        }
    }

    override fun saveImportantDate(draft: ImportantDateDraft) {
        viewModelScope.launch {
            personRepository.saveImportantDate(personId, draft)
            reminderAsks.remindersNeeded()
        }
    }

    override fun deleteImportantDate(id: Long) {
        viewModelScope.launch { personRepository.deleteImportantDate(id) }
    }

    override fun saveGiftIdea(draft: GiftIdeaDraft) {
        if (draft.normalised().text.isEmpty()) return
        viewModelScope.launch { personRepository.saveGiftIdea(personId, draft) }
    }

    override fun deleteGiftIdea(id: Long) {
        viewModelScope.launch { personRepository.deleteGiftIdea(id) }
    }

    override fun saveFollowUp(draft: FollowUpDraft) {
        if (draft.normalised().text.isEmpty()) return
        viewModelScope.launch {
            personRepository.saveFollowUp(personId, draft)
            reminderAsks.remindersNeeded()
        }
    }

    override fun deleteFollowUp(id: Long) {
        viewModelScope.launch { personRepository.deleteFollowUp(id) }
    }

    override fun saveLoan(draft: LoanDraft) {
        if (draft.normalised().text.isEmpty()) return
        viewModelScope.launch {
            personRepository.saveLoan(personId, draft)
            if (draft.returnBy != null) reminderAsks.remindersNeeded()
        }
    }

    override fun deleteLoan(id: Long) {
        viewModelScope.launch { personRepository.deleteLoan(id) }
    }

    /** Marks the Follow-up done today. */
    fun markFollowUpDone(id: Long) {
        viewModelScope.launch { personRepository.setFollowUpDone(id, LocalDate.now(clock)) }
    }

    /** Undoes [markFollowUpDone]; safe to run after this ViewModel is gone (from the snackbar). */
    val undoFollowUpDone: suspend (id: Long) -> Unit = { id -> personRepository.setFollowUpDone(id, null) }

    /** Marks the Loan returned today. */
    fun markLoanReturned(id: Long) {
        viewModelScope.launch { personRepository.setLoanReturned(id, LocalDate.now(clock)) }
    }

    /** Undoes [markLoanReturned]; safe to run after this ViewModel is gone (from the snackbar). */
    val undoLoanReturned: suspend (id: Long) -> Unit = { id -> personRepository.setLoanReturned(id, null) }

    /** The screen resumed: the day may have changed (Due, years counts, "today"). */
    fun checkDate() {
        dateChecks.tryEmit(Unit)
    }

    fun markGiven(id: Long, date: LocalDate) {
        viewModelScope.launch { personRepository.setGiftIdeaGiven(id, date) }
    }

    /** Undoes [markGiven]; safe to run after this ViewModel is gone (from the snackbar). */
    val undoGiven: suspend (id: Long) -> Unit = { id -> personRepository.setGiftIdeaGiven(id, null) }

    private enum class Phase { Showing, ConfirmingDelete, Deleting, Archived, Deleted }

    @AssistedFactory
    interface Factory {
        fun create(personId: Long): PersonDetailViewModel
    }
}
