package dev.alializadeh.orbit.firstrun

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.language.AppLanguage
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.lock.ScreenLockResult
import dev.alializadeh.orbit.lock.ScreenLockStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The first-run steps, in order. Finishing the last one finishes first run. Restore is a branch
 * from Welcome ([FirstRunUiState.restoreUri]); once it succeeds, first run carries on from the
 * step after Welcome (App lock is device-bound, so the offer still comes).
 */
enum class FirstRunStep {
    Welcome,

    /** "Lock Orbit with your fingerprint or screen lock?": skipped when the phone has no screen lock. */
    AppLockOffer,
}

data class FirstRunUiState(
    val step: FirstRunStep = FirstRunStep.Welcome,
    val language: OrbitLanguage = OrbitLanguage.English,
    /** Follows [language] until the user picks one. */
    val primaryCalendar: CalendarSystem = language.defaultCalendar,
    val useLatinDigits: Boolean = false,
    val isFinishing: Boolean = false,
    /** Restoring from the Backup at this URI (picked on Welcome) instead of the Welcome choices. */
    val restoreUri: String? = null,
) {
    /** "Use Latin digits" is offered in Persian only. */
    val showsLatinDigits: Boolean get() = language == OrbitLanguage.Persian
}

/**
 * First run: shown until it's finished once on this device (device-bound state). Finishing creates
 * the seed Rings in the chosen language; the app then lands on Upcoming.
 *
 * Choosing a language applies it at once (the activity recreates, flipping to RTL for Persian);
 * this ViewModel outlives that, and the choices and the step survive process death in
 * [SavedStateHandle]. A Restore is also remembered in device-bound state
 * (`DeviceState.restoredDuringFirstRun`), so first run never comes back to Welcome after one, nor
 * stores Welcome's choices over the restored preferences.
 */
@HiltViewModel
class FirstRunViewModel @Inject constructor(
    private val appLanguage: AppLanguage,
    private val firstRunRepository: FirstRunRepository,
    private val screenLock: ScreenLockStatus,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val step = savedState.getStateFlow(KEY_STEP, FirstRunStep.entries.first())
    private val language = MutableStateFlow(appLanguage.current())
    private val chosenCalendar = savedState.getStateFlow<CalendarSystem?>(KEY_CALENDAR, null)
    private val useLatinDigits = savedState.getStateFlow(KEY_LATIN_DIGITS, false)
    private val isFinishing = MutableStateFlow(false)
    private val restoreUri = savedState.getStateFlow<String?>(KEY_RESTORE_URI, null)

    /** A Restore replaced the Welcome choices: finishing only marks first run finished. */
    private val restored: Boolean get() = savedState[KEY_RESTORED] ?: false

    init {
        // The process died after a Restore (before its step was saved): carry on after Welcome.
        viewModelScope.launch {
            if (firstRunRepository.restoredDuringFirstRun()) onRestored()
        }
    }

    val uiState: StateFlow<FirstRunUiState> =
        combine(
            combine(step, language, chosenCalendar, ::Triple),
            useLatinDigits,
            isFinishing,
            restoreUri,
        ) { (step, language, calendar), latinDigits, finishing, restoring ->
            FirstRunUiState(
                step = step,
                language = language,
                primaryCalendar = calendar ?: language.defaultCalendar,
                useLatinDigits = latinDigits,
                isFinishing = finishing,
                restoreUri = restoring,
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

    /** Restore from a backup: the Open picker returned [uri]. */
    fun startRestore(uri: String) {
        savedState[KEY_RESTORE_URI] = uri
    }

    /** Back out of the Restore, to Welcome. */
    fun cancelRestore() {
        savedState[KEY_RESTORE_URI] = null
    }

    /**
     * The Restore succeeded: its language, Primary calendar and Rings replace the Welcome choices
     * and the seed Rings. First run carries on after Welcome.
     */
    fun onRestored() {
        if (restored) return
        savedState[KEY_RESTORED] = true
        savedState[KEY_RESTORE_URI] = null
        if (step.value == FirstRunStep.Welcome) continueFromStep()
    }

    /**
     * "Turn on" in the App lock offer: how the system prompt ended. Unlocked turns App lock on and
     * finishes; without a screen lock (removed meanwhile) it just moves on; cancelled stays.
     */
    fun onTurnOnAppLock(result: ScreenLockResult) {
        when (result) {
            ScreenLockResult.Unlocked -> viewModelScope.launch {
                firstRunRepository.turnOnAppLock()
                continueFromStep()
            }
            ScreenLockResult.NoScreenLock -> continueFromStep()
            ScreenLockResult.Cancelled, ScreenLockResult.Failed -> Unit
        }
    }

    /** Moves to the next step, or finishes first run after the last one. */
    fun continueFromStep() {
        var next = FirstRunStep.entries.getOrNull(step.value.ordinal + 1)
        // Nothing to offer without a screen lock: skipped silently.
        if (next == FirstRunStep.AppLockOffer && !screenLock.hasSecureScreenLock()) {
            next = FirstRunStep.entries.getOrNull(next.ordinal + 1)
        }
        if (next != null) {
            savedState[KEY_STEP] = next
            return
        }
        if (isFinishing.value) return
        isFinishing.value = true
        viewModelScope.launch {
            if (restored) {
                firstRunRepository.finishAfterRestore()
            } else {
                firstRunRepository.finish(
                    WelcomeChoices(
                        language = language.value,
                        primaryCalendar = chosenCalendar.value ?: language.value.defaultCalendar,
                        useLatinDigits = useLatinDigits.value && language.value == OrbitLanguage.Persian,
                    ),
                )
            }
        }
    }

    private companion object {
        const val KEY_CALENDAR = "primaryCalendar"
        const val KEY_LATIN_DIGITS = "useLatinDigits"
        const val KEY_RESTORE_URI = "restoreUri"
        const val KEY_STEP = "step"
        const val KEY_RESTORED = "restored"
    }
}
