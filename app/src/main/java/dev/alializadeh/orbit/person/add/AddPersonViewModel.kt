package dev.alializadeh.orbit.person.add

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.person.PersonRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AddPersonUiState(
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    /** Set once the Person is stored; the screen then leaves. */
    val isSaved: Boolean = false,
)

@HiltViewModel
class AddPersonViewModel @Inject constructor(
    private val personRepository: PersonRepository,
) : ViewModel() {

    /** The Name field. Text input stays in a [TextFieldState] so typing is never out of sync. */
    val name = TextFieldState()

    private val saveProgress = MutableStateFlow(SaveProgress.Editing)

    val uiState: StateFlow<AddPersonUiState> =
        combine(snapshotFlow { name.text.isNotBlank() }, saveProgress) { hasName, progress ->
            AddPersonUiState(
                canSave = hasName && progress == SaveProgress.Editing,
                isSaving = progress == SaveProgress.Saving,
                isSaved = progress == SaveProgress.Saved,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddPersonUiState())

    fun save() {
        val text = name.text.toString()
        if (text.isBlank() || saveProgress.value != SaveProgress.Editing) return
        saveProgress.value = SaveProgress.Saving
        viewModelScope.launch {
            personRepository.addPerson(text)
            saveProgress.value = SaveProgress.Saved
        }
    }

    private enum class SaveProgress { Editing, Saving, Saved }
}
