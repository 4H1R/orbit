package dev.alializadeh.orbit.person.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.PersonRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface PersonDetailUiState {
    data object Loading : PersonDetailUiState

    data class Loaded(val person: Person) : PersonDetailUiState

    /** The Person was deleted, or never existed. */
    data object NotFound : PersonDetailUiState
}

@HiltViewModel(assistedFactory = PersonDetailViewModel.Factory::class)
class PersonDetailViewModel @AssistedInject constructor(
    @Assisted personId: Long,
    personRepository: PersonRepository,
) : ViewModel() {

    val uiState: StateFlow<PersonDetailUiState> = personRepository.observePerson(personId)
        .map { person -> person?.let(PersonDetailUiState::Loaded) ?: PersonDetailUiState.NotFound }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(personId: Long): PersonDetailViewModel
    }
}
