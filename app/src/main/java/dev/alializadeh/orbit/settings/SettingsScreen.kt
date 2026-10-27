package dev.alializadeh.orbit.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.text.BidiFormatter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.backup.BACKUP_MIME_TYPE
import dev.alializadeh.orbit.backup.spike.BackupSpikeRunner
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import kotlinx.serialization.Serializable

/** The Settings tab's root. */
@Serializable
data object SettingsKey : NavKey

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val createBackupDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri -> if (uri != null) viewModel.runBackupSpike(uri) }
    SettingsScreen(
        uiState = uiState,
        onBackupSpikeClick = { createBackupDocument.launch(viewModel.suggestedBackupFileName()) },
    )
}

/** Settings. Its sub-screens arrive with later slices; for now it only hosts the Backup spike. */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBackupSpikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { OrbitTopAppBar(title = stringResource(R.string.nav_tab_settings)) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            BackupSpikeItem(status = uiState.backupSpike, onClick = onBackupSpikeClick)
        }
    }
}

/** TEMPORARY: the slice 0 Backup format spike. Slice 5 replaces it with Settings → Backup. */
@Composable
private fun BackupSpikeItem(status: BackupSpikeStatus, onClick: () -> Unit) {
    val passphrase = BidiFormatter.getInstance().unicodeWrap(BackupSpikeRunner.PASSPHRASE)
    ListItem(
        onClick = onClick,
        enabled = status != BackupSpikeStatus.Running,
        supportingContent = {
            Column {
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
