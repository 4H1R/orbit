package dev.alializadeh.orbit.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import dev.alializadeh.orbit.settings.SettingsFooter
import dev.alializadeh.orbit.settings.SettingsGroupGap
import dev.alializadeh.orbit.ui.components.ChoiceRow
import dev.alializadeh.orbit.ui.components.NavigationRow
import dev.alializadeh.orbit.ui.components.OrbitIconCircle
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.icons.OrbitIcons
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
    passphrases: BackupPassphraseStore,
    clock: Clock,
) : ViewModel() {

    init {
        // A remembered passphrase the Keystore can't open any more is forgotten here, so Change
        // passphrase isn't offered for it.
        viewModelScope.launch { passphrases.current() }
    }

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
        BackupStatusCard(lastBackup = uiState.lastBackup, today = uiState.today, onBackUpNow = onBackUpNow)
        SettingsGroupGap()
        val rows = if (uiState.hasPassphrase) 2 else 1
        OrbitListGroup(title = null) {
            NavigationRow(
                title = stringResource(R.string.backup_settings_restore),
                summary = stringResource(R.string.backup_settings_restore_summary),
                index = 0,
                count = rows,
                onClick = onRestore,
                icon = OrbitIcons.Restore,
            )
            if (uiState.hasPassphrase) {
                NavigationRow(
                    title = stringResource(R.string.backup_settings_change_passphrase),
                    summary = null,
                    index = 1,
                    count = rows,
                    onClick = onChangePassphrase,
                    icon = OrbitIcons.Key,
                )
            }
        }
        OrbitListGroup(title = stringResource(R.string.backup_settings_nudge_title)) {
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
        SettingsFooter(stringResource(R.string.backup_settings_nudge_footer))
    }
}

/**
 * The page's primary action with the state it acts on: when the last Backup was (or that there's
 * none yet), what a Backup is, and Back up now.
 */
@Composable
private fun BackupStatusCard(lastBackup: LocalDate?, today: LocalDate, onBackUpNow: () -> Unit) {
    val formats = LocalOrbitFormats.current
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = ListItemDefaults.segmentedColors().containerColor,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OrbitIconCircle(OrbitIcons.Backup)
                Column(Modifier.weight(1f)) {
                    Text(lastBackupLine(lastBackup, today), style = MaterialTheme.typography.titleMedium)
                    if (lastBackup != null && lastBackup < today.minusDays(1)) {
                        Text(
                            formats.dates.format(lastBackup, formats.primaryCalendar, today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.backup_nudge_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Button(onClick = onBackUpNow, modifier = Modifier.align(Alignment.End).padding(top = 12.dp)) {
                Icon(OrbitIcons.Backup, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.backup_now), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
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
