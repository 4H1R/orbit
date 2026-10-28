package dev.alializadeh.orbit.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.backup.BackupStatusRepository
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.data.BackedUpPreferences
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.occasion.OccasionRepository
import dev.alializadeh.orbit.lock.AppLockSettings
import dev.alializadeh.orbit.lock.ScreenLockStatus
import dev.alializadeh.orbit.person.TagRepository
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingRepository
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The Settings root: what each row's summary needs. The language comes from the UI locale. */
data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.System,
    val colourSource: ColourSource = ColourSource.OrbitViolet,
    val primaryCalendar: CalendarSystem = CalendarSystem.Gregorian,
    /** The Rings' names, innermost first: the Rings row's summary. */
    val ringNames: List<String> = emptyList(),
    val tagCount: Int = 0,
    val occasionCount: Int = 0,
    /** The day of the last Backup, or null if there's never been one: the Backup row's summary. */
    val lastBackup: LocalDate? = null,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val reminderTime: LocalTime = BackedUpPreferences.DEFAULT_REMINDER_TIME,
    val defaultLeadTimes: Set<LeadTime> = LeadTime.DEFAULT,
    val appLock: AppLockSettings = AppLockSettings(),
    /** App lock is on but the phone has no screen lock. */
    val appLockPaused: Boolean = false,
    /** Orbit's version: the About row's summary. */
    val version: AppVersion = AppVersion("", 0),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    ringRepository: RingRepository,
    tagRepository: TagRepository,
    occasionRepository: OccasionRepository,
    backupStatusRepository: BackupStatusRepository,
    private val screenLock: ScreenLockStatus,
    clock: Clock,
    version: AppVersion,
) : ViewModel() {

    private val hasScreenLock = MutableStateFlow(screenLock.hasSecureScreenLock())

    val uiState: StateFlow<SettingsUiState> =
        combine(
            preferencesRepository.preferences,
            ringRepository.observeRings(),
            combine(tagRepository.observeTags(), occasionRepository.observeOccasions(), ::Pair),
            backupStatusRepository.status,
            combine(preferencesRepository.deviceState, hasScreenLock, ::Pair),
        ) { preferences, rings, (tags, occasions), backup, (device, screenLock) ->
            SettingsUiState(
                theme = preferences.theme,
                colourSource = preferences.colourSource,
                primaryCalendar = preferences.primaryCalendar,
                ringNames = rings.map(Ring::name),
                tagCount = tags.size,
                occasionCount = occasions.size,
                lastBackup = backup.lastBackupAt?.atZone(clock.zone)?.toLocalDate(),
                today = LocalDate.now(clock),
                reminderTime = preferences.reminderTime,
                defaultLeadTimes = preferences.defaultLeadTimes,
                appLock = device.appLock,
                appLockPaused = device.appLock.enabled && !screenLock,
                version = version,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(version = version))

    /** On every resume: the screen lock may have changed meanwhile. */
    fun refreshScreenLock() {
        hasScreenLock.value = screenLock.hasSecureScreenLock()
    }
}
