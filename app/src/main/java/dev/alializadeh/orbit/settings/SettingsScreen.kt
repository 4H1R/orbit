package dev.alializadeh.orbit.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.backup.BACKUP_MIME_TYPE
import dev.alializadeh.orbit.backup.spike.BackupSpikeRunner
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolateLtr
import dev.alializadeh.orbit.ui.components.NavigationRow
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.datepicker.calendarName
import kotlinx.serialization.Serializable

/** The Settings tab's root. */
@Serializable
data object SettingsKey : NavKey

@Composable
fun SettingsRoute(
    onOpen: (NavKey) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val createBackupDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri -> if (uri != null) viewModel.runBackupSpike(uri) }
    SettingsScreen(
        uiState = uiState,
        onOpen = onOpen,
        onBackupSpikeClick = { createBackupDocument.launch(viewModel.suggestedBackupFileName()) },
    )
}

/**
 * Settings: a grouped list where each row shows a one-line summary and opens a sub-screen pushed
 * on the Settings tab. Later slices add Rings, Tags, Reminders, App lock, Backup and About.
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onOpen: (NavKey) -> Unit,
    onBackupSpikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitSettingsScaffold(title = stringResource(R.string.nav_tab_settings), onBack = null, modifier = modifier) {
        val separator = stringResource(R.string.separator)
        OrbitListGroup(title = null) {
            NavigationRow(
                title = stringResource(R.string.settings_appearance_title),
                summary = listOf(themeName(uiState.theme), colourSourceName(uiState.colourSource))
                    .joinToString(separator),
                index = 0,
                count = 2,
                onClick = { onOpen(AppearanceSettingsKey) },
            )
            NavigationRow(
                title = stringResource(R.string.settings_language_dates_title),
                summary = listOf(
                    languageName(LocalOrbitFormats.current.language),
                    calendarName(uiState.primaryCalendar),
                ).joinToString(separator),
                index = 1,
                count = 2,
                onClick = { onOpen(LanguageDatesSettingsKey) },
            )
        }
        Spacer(Modifier.height(24.dp))
        BackupSpikeItem(status = uiState.backupSpike, onClick = onBackupSpikeClick)
    }
}

/** TEMPORARY: the slice 0 Backup format spike. Slice 5 replaces it with Settings → Backup. */
@Composable
private fun BackupSpikeItem(status: BackupSpikeStatus, onClick: () -> Unit) {
    val passphrase = bidiIsolateLtr(BackupSpikeRunner.PASSPHRASE)
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        enabled = status != BackupSpikeStatus.Running,
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_spike_backup_body, passphrase))
                when (status) {
                    BackupSpikeStatus.Idle -> Unit
                    BackupSpikeStatus.Running ->
                        Text(stringResource(R.string.settings_spike_backup_running))

                    BackupSpikeStatus.Verified -> Text(
                        text = stringResource(R.string.settings_spike_backup_verified),
                        color = MaterialTheme.colorScheme.primary,
                    )

                    is BackupSpikeStatus.Failed -> Text(
                        text = stringResource(R.string.settings_spike_backup_failed, status.reason),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    ) {
        Text(stringResource(R.string.settings_spike_backup_title))
    }
}
