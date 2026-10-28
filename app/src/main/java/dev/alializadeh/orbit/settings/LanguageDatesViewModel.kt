package dev.alializadeh.orbit.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.LunarHijriAdjustment
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.language.AppLanguage
import dev.alializadeh.orbit.language.OrbitLanguage
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LanguageDatesUiState {
    data object Loading : LanguageDatesUiState

    data class Loaded(
        val primaryCalendar: CalendarSystem,
        val useLatinDigits: Boolean,
        /** For showing today's date in each Calendar. */
        val today: LocalDate,
        /** The Lunar date adjustment, −2…+2 days. */
        val lunarAdjustmentDays: Int = 0,
    ) : LanguageDatesUiState
}

/**
 * Settings → Language & dates. The language itself isn't state here: it's the UI locale, and
 * changing it recreates the activity.
 */
@HiltViewModel
class LanguageDatesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val appLanguage: AppLanguage,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<LanguageDatesUiState> = preferencesRepository.preferences
        .map { preferences ->
            LanguageDatesUiState.Loaded(
                primaryCalendar = preferences.primaryCalendar,
                useLatinDigits = preferences.useLatinDigits,
                today = LocalDate.now(clock),
                lunarAdjustmentDays = preferences.lunarAdjustmentDays,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LanguageDatesUiState.Loading)

    /** Switches the UI language; the Primary calendar stays as it is (it no longer follows). */
    fun selectLanguage(language: OrbitLanguage) = appLanguage.set(language)

    fun selectPrimaryCalendar(calendar: CalendarSystem) {
        viewModelScope.launch { preferencesRepository.setPrimaryCalendar(calendar) }
    }

    fun setUseLatinDigits(useLatinDigits: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseLatinDigits(useLatinDigits) }
    }

    /** Moves every Lunar Hijri date by [days] (−2…+2) from Umm al-Qura. */
    fun setLunarAdjustment(days: Int) {
        val clamped = days.coerceIn(-LunarHijriAdjustment.MAX_DAYS, LunarHijriAdjustment.MAX_DAYS)
        viewModelScope.launch { preferencesRepository.update { it.copy(lunarAdjustmentDays = clamped) } }
    }
}
