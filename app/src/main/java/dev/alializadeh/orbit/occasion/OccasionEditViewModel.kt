package dev.alializadeh.orbit.occasion

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.calendar.inCalendar
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingRepository
import dev.alializadeh.orbit.ui.datepicker.WheelDatePickerState
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import dev.alializadeh.orbit.upcoming.UpcomingRepository
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the editor offers: Rings, Tags and People to choose from, and the default lead times. */
sealed interface OccasionEditUiState {
    data object Loading : OccasionEditUiState

    data class Loaded(
        val rings: List<Ring>,
        val tags: List<Tag>,
        /** Everyone, Archived People flagged (they're never reached, and never offered). */
        val people: List<UpcomingPerson>,
        val defaultLeadTimes: Set<LeadTime>,
        val today: LocalDate,
    ) : OccasionEditUiState
}

/** Where the editor is: editing, or done (saved or deleted), so the screen can leave. */
enum class OccasionEditExit { Saved, Deleted }

/**
 * The Occasion editor: an existing Occasion ([occasionId]), a new built-in one ([builtIn]), or a new
 * one of the user's own. The fields live here as snapshot state (the name in a [TextFieldState]),
 * so they survive App lock and configuration changes.
 */
@HiltViewModel(assistedFactory = OccasionEditViewModel.Factory::class)
class OccasionEditViewModel @AssistedInject constructor(
    @Assisted("occasionId") private val occasionId: Long?,
    @Assisted("builtIn") builtInName: String?,
    private val occasionRepository: OccasionRepository,
    ringRepository: RingRepository,
    personRepository: PersonRepository,
    upcomingRepository: UpcomingRepository,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    val isNew: Boolean get() = occasionId == null

    var builtIn: BuiltInOccasion? by mutableStateOf(builtInName?.let { name -> BuiltInOccasion.entries.firstOrNull { it.name == name } })
        private set

    val name = TextFieldState()
    var picker: WheelDatePickerState? by mutableStateOf(null)
    var forEveryone: Boolean by mutableStateOf(false)
    var ringIds: Set<Long> by mutableStateOf(emptySet())
    var tagIds: Set<Long> by mutableStateOf(emptySet())
    var personIds: Set<Long> by mutableStateOf(emptySet())
    var leadTimes: Set<LeadTime>? by mutableStateOf(null)

    private val exitState = MutableStateFlow<OccasionEditExit?>(null)
    val exit: StateFlow<OccasionEditExit?> = exitState.asStateFlow()

    val uiState: StateFlow<OccasionEditUiState> = combine(
        ringRepository.observeRings(),
        personRepository.observeTags(),
        upcomingRepository.observePeople(),
        preferencesRepository.preferences,
    ) { rings, tags, people, preferences ->
        OccasionEditUiState.Loaded(
            rings = rings.sortedBy(Ring::position),
            tags = tags.sortedBy { it.name.lowercase() },
            people = people,
            defaultLeadTimes = preferences.defaultLeadTimes,
            today = LocalDate.now(clock),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OccasionEditUiState.Loading)

    init {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            val draft = when {
                occasionId != null -> occasionRepository.get(occasionId)?.let(OccasionDraft::of)
                builtIn != null -> OccasionDraft.builtIn(builtIn!!)
                else -> {
                    // A new one of the user's own starts on today in the Primary calendar.
                    val calendar = preferencesRepository.preferences.first().primaryCalendar
                    val now = today.inCalendar(calendar)
                    OccasionDraft(date = RecurringDate(calendar, now.month, now.day), forEveryone = true)
                }
            } ?: return@launch
            builtIn = draft.builtIn
            name.setTextAndPlaceCursorAtEnd(draft.label.orEmpty())
            picker = WheelDatePickerState.forDate(draft.date, today, allowNoYear = true)
            forEveryone = draft.forEveryone
            ringIds = draft.ringIds
            tagIds = draft.tagIds
            personIds = draft.personIds
            leadTimes = draft.leadTimes
        }
    }

    /** The Occasion as it would be saved now; null until loaded. */
    fun draft(): OccasionDraft? {
        val picked = picker ?: return null
        return OccasionDraft(
            id = occasionId,
            builtIn = builtIn,
            label = name.text.toString(),
            date = picked.date.copy(year = null),
            forEveryone = forEveryone,
            ringIds = ringIds,
            tagIds = tagIds,
            personIds = personIds,
            leadTimes = leadTimes,
        ).normalised()
    }

    fun save() {
        val draft = draft()?.takeIf(OccasionDraft::canSave) ?: return
        viewModelScope.launch {
            occasionRepository.save(draft)
            exitState.value = OccasionEditExit.Saved
        }
    }

    fun delete() {
        val id = occasionId ?: return
        viewModelScope.launch {
            occasionRepository.delete(id)
            exitState.value = OccasionEditExit.Deleted
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("occasionId") occasionId: Long?, @Assisted("builtIn") builtIn: String?): OccasionEditViewModel
    }
}
