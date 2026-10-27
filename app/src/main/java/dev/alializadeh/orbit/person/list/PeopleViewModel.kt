package dev.alializadeh.orbit.person.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.PersonRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface PeopleUiState {
    data object Loading : PeopleUiState

    /** Every Person, sorted by Name. */
    data class Loaded(val people: List<Person>) : PeopleUiState
}

@HiltViewModel
class PeopleViewModel @Inject constructor(
    personRepository: PersonRepository,
) : ViewModel() {

    val uiState: StateFlow<PeopleUiState> = personRepository.observePeople()
        .map(PeopleUiState::Loaded)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState.Loading)
}
