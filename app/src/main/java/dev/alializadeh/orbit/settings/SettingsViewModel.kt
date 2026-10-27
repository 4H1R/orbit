package dev.alializadeh.orbit.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.backup.BackupException
import dev.alializadeh.orbit.backup.spike.BackupSpikeRunner
import dev.alializadeh.orbit.backup.suggestedBackupFileName
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Settings root: what each row's summary needs. The language comes from the UI locale. */
data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.System,
    val colourSource: ColourSource = ColourSource.OrbitViolet,
    val primaryCalendar: CalendarSystem = CalendarSystem.Gregorian,
    val backupSpike: BackupSpikeStatus = BackupSpikeStatus.Idle,
)

/** TEMPORARY: the slice 0 Backup format spike's progress. Slice 5 replaces it. */
sealed interface BackupSpikeStatus {
    data object Idle : BackupSpikeStatus
    data object Running : BackupSpikeStatus
    data object Verified : BackupSpikeStatus
    data class Failed(val reason: String) : BackupSpikeStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    private val backupSpikeRunner: BackupSpikeRunner,
    private val clock: Clock,
) : ViewModel() {

    private val backupSpike = MutableStateFlow<BackupSpikeStatus>(BackupSpikeStatus.Idle)

    val uiState: StateFlow<SettingsUiState> =
        combine(preferencesRepository.preferences, backupSpike) { preferences, spike ->
            SettingsUiState(
                theme = preferences.theme,
                colourSource = preferences.colourSource,
                primaryCalendar = preferences.primaryCalendar,
                backupSpike = spike,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun suggestedBackupFileName(): String = suggestedBackupFileName(LocalDate.now(clock))

    /** Writes a spike Backup to [uri] (from the Save picker), then re-reads and verifies it. */
    fun runBackupSpike(uri: Uri) {
        if (backupSpike.value == BackupSpikeStatus.Running) return
        backupSpike.value = BackupSpikeStatus.Running
        viewModelScope.launch {
            backupSpike.value = try {
                backupSpikeRunner.backUpAndVerify(uri)
                BackupSpikeStatus.Verified
            } catch (e: BackupException) {
                BackupSpikeStatus.Failed(e.toString())
            } catch (e: IOException) {
                BackupSpikeStatus.Failed(e.toString())
            }
        }
    }
}
