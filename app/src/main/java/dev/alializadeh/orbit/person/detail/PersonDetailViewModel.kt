package dev.alializadeh.orbit.person.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.person.PersonField
import dev.alializadeh.orbit.person.PersonProfile
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.photo.PhotoStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why Person detail is leaving on its own. */
enum class DetailExit { Archived, Deleted }

sealed interface PersonDetailUiState {
    data object Loading : PersonDetailUiState

    data class Loaded(
        val profile: PersonProfile,
        val photo: File? = null,
        val confirmingDelete: Boolean = false,
        /** Set once Archive or Delete is done; the screen then leaves. */
        val exit: DetailExit? = null,
    ) : PersonDetailUiState {
        /** What the Add details row offers: the fields with nothing in them yet. */
        val missingFields: List<PersonField>
            get() = PersonField.entries.filter { field ->
                val person = profile.person
                when (field) {
                    PersonField.Photo -> person.photoFileName == null
                    PersonField.ContactMethods -> profile.contactMethods.isEmpty()
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
}

@HiltViewModel(assistedFactory = PersonDetailViewModel.Factory::class)
class PersonDetailViewModel @AssistedInject constructor(
    @Assisted private val personId: Long,
    private val personRepository: PersonRepository,
    private val photoStore: PhotoStore,
) : ViewModel() {

    private val phase = MutableStateFlow(Phase.Showing)
    private var lastLoaded: PersonDetailUiState.Loaded? = null

    val uiState: StateFlow<PersonDetailUiState> =
        combine(personRepository.observeProfile(personId), phase) { profile, phase ->
            val exit = when (phase) {
                Phase.Archived -> DetailExit.Archived
                Phase.Deleted -> DetailExit.Deleted
                else -> null
            }
            when {
                profile != null -> PersonDetailUiState.Loaded(
                    profile = profile,
                    photo = profile.person.photoFileName?.let(photoStore::file),
                    confirmingDelete = phase == Phase.ConfirmingDelete,
                    exit = exit,
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

    private enum class Phase { Showing, ConfirmingDelete, Deleting, Archived, Deleted }

    @AssistedFactory
    interface Factory {
        fun create(personId: Long): PersonDetailViewModel
    }
}
