package dev.alializadeh.orbit

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.ui.theme.OrbitAppearance
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiState {
    /** The first preferences read hasn't completed; the splash screen stays up. */
    data object Loading : MainUiState

    data class Ready(
        val appearance: OrbitAppearance,
        val useLatinDigits: Boolean,
        val primaryCalendar: CalendarSystem,
        val firstRunFinished: Boolean,
        /** The device-state file was unreadable and replaced: tell the user once. */
        val resetNoticePending: Boolean = false,
    ) : MainUiState
}

/** What the host activity needs before drawing anything: the theme and whether first run is done. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
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
                primaryCalendar = preferences.primaryCalendar,
                firstRunFinished = device.firstRunFinished,
                resetNoticePending = device.resetNoticePending,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    /** The "settings were reset" notice has been shown. */
    fun resetNoticeShown() {
        viewModelScope.launch { preferencesRepository.updateDeviceState { it.copy(resetNoticePending = false) } }
    }
}
