package dev.alializadeh.orbit.firstrun

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.language.AppLanguage
import dev.alializadeh.orbit.language.OrbitLanguage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The first-run steps, in order. Finishing the last one finishes first run. Slice 7 adds the App
 * lock offer after Welcome; slice 5 adds Restore as a branch from Welcome.
 */
enum class FirstRunStep {
    Welcome,
}

data class FirstRunUiState(
    val step: FirstRunStep = FirstRunStep.Welcome,
    val language: OrbitLanguage = OrbitLanguage.English,
    /** Follows [language] until the user picks one. */
    val primaryCalendar: CalendarSystem = language.defaultCalendar,
    val useLatinDigits: Boolean = false,
    val isFinishing: Boolean = false,
) {
    /** "Use Latin digits" is offered in Persian only. */
    val showsLatinDigits: Boolean get() = language == OrbitLanguage.Persian
}

/**
 * First run: shown until it's finished once on this device (device-bound state). Finishing creates
 * the seed Rings in the chosen language; the app then lands on Upcoming.
 *
 * Choosing a language applies it at once (the activity recreates, flipping to RTL for Persian);
 * this ViewModel outlives that, and the choices survive process death in [SavedStateHandle].
 */
@HiltViewModel
class FirstRunViewModel @Inject constructor(
    private val appLanguage: AppLanguage,
    private val firstRunRepository: FirstRunRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val step = MutableStateFlow(FirstRunStep.entries.first())
    private val language = MutableStateFlow(appLanguage.current())
    private val chosenCalendar = savedState.getStateFlow<CalendarSystem?>(KEY_CALENDAR, null)
    private val useLatinDigits = savedState.getStateFlow(KEY_LATIN_DIGITS, false)
    private val isFinishing = MutableStateFlow(false)

    val uiState: StateFlow<FirstRunUiState> =
        combine(step, language, chosenCalendar, useLatinDigits, isFinishing) {
                step, language, calendar, latinDigits, finishing ->
            FirstRunUiState(
                step = step,
                language = language,
                primaryCalendar = calendar ?: language.defaultCalendar,
                useLatinDigits = latinDigits,
                isFinishing = finishing,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FirstRunUiState(language = language.value))

    fun selectLanguage(language: OrbitLanguage) {
        this.language.value = language
        appLanguage.set(language)
    }

    /** Once touched, the Primary calendar no longer follows the language. */
    fun selectPrimaryCalendar(calendar: CalendarSystem) {
        savedState[KEY_CALENDAR] = calendar
    }

    fun setUseLatinDigits(useLatinDigits: Boolean) {
        savedState[KEY_LATIN_DIGITS] = useLatinDigits
    }

    /** Moves to the next step, or finishes first run after the last one. */
    fun continueFromStep() {
        val next = FirstRunStep.entries.getOrNull(step.value.ordinal + 1)
        if (next != null) {
            step.value = next
            return
        }
        if (isFinishing.value) return
        isFinishing.value = true
        viewModelScope.launch {
            firstRunRepository.finish(
                WelcomeChoices(
                    language = language.value,
                    primaryCalendar = chosenCalendar.value ?: language.value.defaultCalendar,
                    useLatinDigits = useLatinDigits.value && language.value == OrbitLanguage.Persian,
                ),
            )
        }
    }

    private companion object {
        const val KEY_CALENDAR = "primaryCalendar"
        const val KEY_LATIN_DIGITS = "useLatinDigits"
    }
}
