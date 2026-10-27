package dev.alializadeh.orbit

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.ui.theme.OrbitAppearance
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    /** The first preferences read hasn't completed; the splash screen stays up. */
    data object Loading : MainUiState

    data class Ready(
        val appearance: OrbitAppearance,
        val useLatinDigits: Boolean,
        val firstRunFinished: Boolean,
    ) : MainUiState
}

/** What the host activity needs before drawing anything: the theme and whether first run is done. */
@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> =
        combine(preferencesRepository.preferences, preferencesRepository.deviceState) { preferences, device ->
            MainUiState.Ready(
                appearance = OrbitAppearance(
                    themeMode = preferences.theme,
                    colourSource = preferences.colourSource,
                    customSeed = preferences.customSeedArgb?.let(::Color),
                ),
                useLatinDigits = preferences.useLatinDigits,
                firstRunFinished = device.firstRunFinished,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}
