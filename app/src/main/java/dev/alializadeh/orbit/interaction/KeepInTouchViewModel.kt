package dev.alializadeh.orbit.interaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.due.SnoozeDuration
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.names
import dev.alializadeh.orbit.person.photo.PhotoStore
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which keep-in-touch sheet is open, and on whom. */
sealed interface KeepInTouchSheet {
    /** Log interaction, starting from [personId]. */
    data class Log(val personId: Long) : KeepInTouchSheet

    /** An existing Interaction, reopened to edit or delete it. */
    data class Edit(val interactionId: Long) : KeepInTouchSheet

    data class Snooze(val personId: Long) : KeepInTouchSheet
}

/** A Person as the Log interaction sheet shows them (a chip) or offers them (the picker). */
data class PickablePerson(
    val id: Long,
    val names: PersonNames,
    val photo: File? = null,
    val isArchived: Boolean = false,
)

/** What an open Log interaction sheet starts from. */
data class LogSheetState(
    /** Null for a new Interaction. */
    val interactionId: Long?,
    val type: InteractionType,
    val date: LocalDate,
    val note: String,
    /** Who's in it to start with: the starting Person, or everyone in the Interaction being edited. */
    val people: List<PickablePerson>,
    /** Who "+ Add" can offer: everyone who isn't Archived. */
    val candidates: List<PickablePerson>,
    val today: LocalDate,
)

data class SnoozeSheetState(val personId: Long, val names: PersonNames, val today: LocalDate)

data class KeepInTouchUiState(
    val log: LogSheetState? = null,
    val snooze: SnoozeSheetState? = null,
)

/**
 * The Log interaction and Snooze sheets, shared by every screen that offers them (Person detail,
 * All interactions, Upcoming, the People list, later the Orbit view). Each screen gets its own
 * instance (scoped to its back-stack entry) and draws it with `KeepInTouchSheets`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KeepInTouchViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val interactionRepository: InteractionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val photoStore: PhotoStore,
    private val clock: Clock,
) : ViewModel() {

    private val sheet = MutableStateFlow<KeepInTouchSheet?>(null)

    val uiState: StateFlow<KeepInTouchUiState> = sheet
        .flatMapLatest { sheet ->
            when (sheet) {
                null -> flowOf(KeepInTouchUiState())
                is KeepInTouchSheet.Log -> combine(
                    personRepository.observeAllPeople(),
                    preferencesRepository.deviceState.map { it.lastInteractionType },
                ) { people, lastType ->
                    val today = LocalDate.now(clock)
                    val start = people.firstOrNull { it.id == sheet.personId }
                    KeepInTouchUiState(
                        log = start?.let {
                            LogSheetState(
                                interactionId = null,
                                type = lastType,
                                date = today,
                                note = "",
                                people = listOf(pickable(it)),
                                candidates = candidates(people),
                                today = today,
                            )
                        },
                    )
                }
                is KeepInTouchSheet.Edit -> combine(
                    personRepository.observeAllPeople(),
                    interactionRepository.observeInteraction(sheet.interactionId),
                ) { people, found ->
                    KeepInTouchUiState(
                        log = found?.let {
                            LogSheetState(
                                interactionId = it.interaction.id,
                                type = it.interaction.type,
                                date = it.interaction.date,
                                note = it.interaction.note.orEmpty(),
                                people = it.people.sortedBy(Person::id).map(::pickable),
                                candidates = candidates(people),
                                today = LocalDate.now(clock),
                            )
                        },
                    )
                }
                is KeepInTouchSheet.Snooze -> personRepository.observeAllPeople().map { people ->
                    KeepInTouchUiState(
                        snooze = people.firstOrNull { it.id == sheet.personId }?.let {
                            SnoozeSheetState(it.id, it.names, LocalDate.now(clock))
                        },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeepInTouchUiState())

    private fun pickable(person: Person) =
        PickablePerson(person.id, person.names, person.photoFileName?.let(photoStore::file), person.isArchived)

    private fun candidates(people: List<Person>) = people.filterNot(Person::isArchived).map(::pickable)

    /** Opens Log interaction for [personId]: there's no Log interaction without a starting Person. */
    fun logInteraction(personId: Long) {
        sheet.value = KeepInTouchSheet.Log(personId)
    }

    /** Reopens an Interaction to edit or delete it. */
    fun editInteraction(interactionId: Long) {
        sheet.value = KeepInTouchSheet.Edit(interactionId)
    }

    /** Opens the Snooze sheet for [personId]. */
    fun openSnooze(personId: Long) {
        sheet.value = KeepInTouchSheet.Snooze(personId)
    }

    /** The open sheet has gone. */
    fun dismiss() {
        sheet.value = null
    }

    /** Logs or edits the Interaction, and remembers its type as the next default. */
    fun save(draft: InteractionDraft) {
        val d = draft.normalised()
        if (d.personIds.isEmpty() || d.date.isAfter(LocalDate.now(clock))) return
        viewModelScope.launch {
            interactionRepository.save(d)
            preferencesRepository.updateDeviceState { it.copy(lastInteractionType = d.type) }
        }
    }

    /** Deletes the Interaction for everyone in it. */
    fun delete(interactionId: Long) {
        viewModelScope.launch { interactionRepository.delete(interactionId) }
    }

    /**
     * Snoozes [personId] for [duration] from today, then hands [onSnoozed] the Undo (which
     * restores the Snooze they had before, and is safe to run after this ViewModel is gone).
     */
    fun snooze(personId: Long, duration: SnoozeDuration, onSnoozed: (undo: suspend () -> Unit) -> Unit = {}) {
        val until = duration.endFrom(LocalDate.now(clock))
        val repository = personRepository
        viewModelScope.launch {
            val before = repository.setSnoozedUntil(personId, until)
            onSnoozed { repository.setSnoozedUntil(personId, before) }
        }
    }

    /** Ends [personId]'s Snooze now: they're Due again if they're behind. */
    fun cancelSnooze(personId: Long) {
        viewModelScope.launch { personRepository.setSnoozedUntil(personId, null) }
    }
}
