package dev.alializadeh.orbit.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.backup.BackupStatusRepository
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.person.TagRepository
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingRepository
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
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
    /** The day of the last Backup, or null if there's never been one: the Backup row's summary. */
    val lastBackup: LocalDate? = null,
    val today: LocalDate = LocalDate.ofEpochDay(0),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    ringRepository: RingRepository,
    tagRepository: TagRepository,
    backupStatusRepository: BackupStatusRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(
            preferencesRepository.preferences,
            ringRepository.observeRings(),
            tagRepository.observeTags(),
            backupStatusRepository.status,
        ) { preferences, rings, tags, backup ->
            SettingsUiState(
                theme = preferences.theme,
                colourSource = preferences.colourSource,
                primaryCalendar = preferences.primaryCalendar,
                ringNames = rings.map(Ring::name),
                tagCount = tags.size,
                lastBackup = backup.lastBackupAt?.atZone(clock.zone)?.toLocalDate(),
                today = LocalDate.now(clock),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
}
