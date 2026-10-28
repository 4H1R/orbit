package dev.alializadeh.orbit.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.RelativeDays
import dev.alializadeh.orbit.format.text
import dev.alializadeh.orbit.settings.OrbitSettingsScaffold
import dev.alializadeh.orbit.ui.components.ChoiceRow
import dev.alializadeh.orbit.ui.components.NavigationRow
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.upcoming.todayFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object BackupSettingsKey : NavKey

sealed interface BackupSettingsUiState {
    data object Loading : BackupSettingsUiState

    data class Loaded(
        val today: LocalDate,
        /** The day of the last Backup (or of the restored Backup); null if never. */
        val lastBackup: LocalDate?,
        val hasPassphrase: Boolean,
        val nudgeInterval: BackupNudgeInterval,
    ) : BackupSettingsUiState
}

@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    statusRepository: BackupStatusRepository,
    private val preferences: PreferencesRepository,
    clock: Clock,
) : ViewModel() {

    private val dateChecks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val uiState: StateFlow<BackupSettingsUiState> =
        combine(statusRepository.status, todayFlow(clock, dateChecks)) { status, today ->
            BackupSettingsUiState.Loaded(
                today = today,
                lastBackup = status.lastBackupAt?.atZone(clock.zone)?.toLocalDate(),
                hasPassphrase = status.hasPassphrase,
                nudgeInterval = status.nudgeInterval,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupSettingsUiState.Loading)

    fun setNudgeInterval(interval: BackupNudgeInterval) {
        viewModelScope.launch { preferences.update { it.copy(backupNudgeInterval = interval) } }
    }

    fun checkDate() {
        dateChecks.tryEmit(Unit)
    }
}

@Composable
fun BackupSettingsRoute(
    onBack: () -> Unit,
    onBackUpNow: () -> Unit,
    onRestore: (uri: String) -> Unit,
    onChangePassphrase: () -> Unit,
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.checkDate()
        onPauseOrDispose {}
    }
    // Providers don't know an .age MIME type, so every file is offered.
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestore(uri.toString())
    }
    BackupSettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onBackUpNow = onBackUpNow,
        onRestore = { openDocument.launch(arrayOf("*/*")) },
        onChangePassphrase = onChangePassphrase,
        onNudgeIntervalSelect = viewModel::setNudgeInterval,
    )
}

/**
 * Settings → Backup: Back up now (with the last Backup's date), Restore, Change passphrase (once
 * there is one), and how soon Upcoming nudges.
 */
@Composable
fun BackupSettingsScreen(
    uiState: BackupSettingsUiState,
    onBack: () -> Unit,
    onBackUpNow: () -> Unit,
    onRestore: () -> Unit,
    onChangePassphrase: () -> Unit,
    onNudgeIntervalSelect: (BackupNudgeInterval) -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitSettingsScaffold(title = stringResource(R.string.settings_backup_title), onBack = onBack, modifier = modifier) {
        if (uiState !is BackupSettingsUiState.Loaded) return@OrbitSettingsScaffold
        val rows = if (uiState.hasPassphrase) 3 else 2
        OrbitListGroup(title = null) {
            NavigationRow(
                title = stringResource(R.string.backup_now),
                summary = lastBackupLine(uiState.lastBackup, uiState.today, withDate = true),
                index = 0,
                count = rows,
                onClick = onBackUpNow,
            )
            NavigationRow(
                title = stringResource(R.string.backup_settings_restore),
                summary = stringResource(R.string.backup_settings_restore_summary),
                index = 1,
                count = rows,
                onClick = onRestore,
            )
            if (uiState.hasPassphrase) {
                NavigationRow(
                    title = stringResource(R.string.backup_settings_change_passphrase),
                    summary = null,
                    index = 2,
                    count = rows,
                    onClick = onChangePassphrase,
                )
            }
        }
        OrbitListGroup(title = stringResource(R.string.backup_settings_nudge_title), modifier = Modifier.padding(top = 8.dp)) {
            BackupNudgeInterval.entries.forEachIndexed { index, interval ->
                ChoiceRow(
                    title = nudgeIntervalName(interval),
                    summary = null,
                    selected = interval == uiState.nudgeInterval,
                    index = index,
                    count = BackupNudgeInterval.entries.size,
                    onClick = { onNudgeIntervalSelect(interval) },
                )
            }
        }
        Text(
            text = stringResource(R.string.backup_settings_nudge_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/**
 * "Last backup 3 days ago" (with [withDate]: "Last backup 3 days ago · 25 Oct"), or "No backup
 * yet".
 */
@Composable
fun lastBackupLine(lastBackup: LocalDate?, today: LocalDate, withDate: Boolean = false): String {
    if (lastBackup == null) return stringResource(R.string.settings_backup_never)
    val formats = LocalOrbitFormats.current
    val ago = stringResource(R.string.backup_nudge_last, RelativeDays.ago(lastBackup, today).text())
    if (!withDate || lastBackup >= today.minusDays(1)) return ago
    return ago + stringResource(R.string.separator) + formats.dates.format(lastBackup, formats.primaryCalendar, today)
}

@Composable
fun nudgeIntervalName(interval: BackupNudgeInterval): String {
    val days = interval.days ?: return stringResource(R.string.backup_nudge_interval_off)
    return pluralStringResource(R.plurals.backup_nudge_interval_days, days, LocalOrbitFormats.current.numbers.format(days))
}
