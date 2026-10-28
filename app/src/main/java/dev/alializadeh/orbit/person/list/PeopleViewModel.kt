package dev.alializadeh.orbit.person.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.due.DueState
import dev.alializadeh.orbit.person.DateSoon
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.upcoming.todayFlow
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A row of the People list: who, their photo, where they stand with their Cadence, and the date badge. */
data class PersonRow(
    val summary: PersonSummary,
    val photo: File?,
    val due: DueState,
    /** An Important date today or in the next 30 days. */
    val dateSoon: DateSoon? = null,
)

sealed interface PeopleUiState {
    data object Loading : PeopleUiState

    /**
     * Every Person who isn't Archived, unsorted: the screen sorts them by the displayed name in
     * the UI language, which can change while this ViewModel lives (a language switch recreates
     * the activity, not the ViewModel).
     */
    data class Loaded(val people: List<PersonRow>, val today: LocalDate = LocalDate.ofEpochDay(0)) : PeopleUiState
}

@HiltViewModel
class PeopleViewModel @Inject constructor(
    personRepository: PersonRepository,
    photoStore: PhotoStore,
    clock: Clock,
) : ViewModel() {

    private val dateChecks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val uiState: StateFlow<PeopleUiState> =
        combine(personRepository.observePeople(), todayFlow(clock, dateChecks)) { people, today ->
            PeopleUiState.Loaded(
                people = people.map {
                    PersonRow(
                        summary = it,
                        photo = it.photoFileName?.let(photoStore::file),
                        due = it.keepInTouch.on(today),
                        dateSoon = DateSoon.of(it.importantDates, today),
                    )
                },
                today = today,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState.Loading)

    /** The screen resumed: the day may have changed while Orbit was in the background. */
    fun checkDate() {
        dateChecks.tryEmit(Unit)
    }
}
