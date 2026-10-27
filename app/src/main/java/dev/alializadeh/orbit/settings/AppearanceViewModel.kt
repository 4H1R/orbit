package dev.alializadeh.orbit.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppearanceUiState {
    data object Loading : AppearanceUiState

    data class Loaded(
        val theme: ThemeMode,
        val colourSource: ColourSource,
        /** The "Your colour" seed, kept while another source is chosen. */
        val customColour: Color?,
    ) : AppearanceUiState
}

/** Settings → Appearance. Changes apply to the whole app as soon as they're stored. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> = preferencesRepository.preferences
        .map { preferences ->
            AppearanceUiState.Loaded(
                theme = preferences.theme,
                colourSource = preferences.colourSource,
                customColour = preferences.customSeedArgb?.let(::Color),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceUiState.Loading)

    fun selectTheme(theme: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setTheme(theme) }
    }

    fun selectColourSource(source: ColourSource) {
        viewModelScope.launch { preferencesRepository.setColourSource(source) }
    }

    /** Chooses "Your colour" with [colour] (a swatch or a free pick). */
    fun selectCustomColour(colour: Color) {
        viewModelScope.launch { preferencesRepository.setCustomColour(colour.toArgb()) }
    }
}
