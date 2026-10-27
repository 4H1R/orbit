package dev.alializadeh.orbit.person.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.person.photo.PhotoStore
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A row of the People list. */
data class PersonRow(val summary: PersonSummary, val photo: File?)

sealed interface PeopleUiState {
    data object Loading : PeopleUiState

    /**
     * Every Person who isn't Archived, unsorted: the screen sorts them by the displayed name in
     * the UI language, which can change while this ViewModel lives (a language switch recreates
     * the activity, not the ViewModel).
     */
    data class Loaded(val people: List<PersonRow>) : PeopleUiState
}

@HiltViewModel
class PeopleViewModel @Inject constructor(
    personRepository: PersonRepository,
    photoStore: PhotoStore,
) : ViewModel() {

    val uiState: StateFlow<PeopleUiState> = personRepository.observePeople()
        .map { people -> PeopleUiState.Loaded(people.map { PersonRow(it, it.photoFileName?.let(photoStore::file)) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState.Loading)
}
