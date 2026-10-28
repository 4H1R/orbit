package dev.alializadeh.orbit.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.lock.rememberScreenLockGate
import dev.alializadeh.orbit.ui.components.SensitiveClipboard
import dev.alializadeh.orbit.ui.expressive.OrbitBlockingSheet
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.icons.OrbitIcons

/**
 * Draws Back up now over the whole app: the full-screen passphrase setup, the passphrase check
 * dialog, the Save picker, the blocking progress sheet, the result sheet and the failure dialog.
 * Place it after the app's content so the passphrase form covers everything.
 */
@Composable
fun BackupHost(viewModel: BackupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        viewModel.onDocumentPicked(uri?.toString())
    }
    val screenLock = rememberScreenLockGate()
    val unlockToShow = stringResource(R.string.passphrase_unlock_show)
    val unlockToSetNew = stringResource(R.string.passphrase_unlock_new)
    // A shown passphrase never outlives Orbit leaving the screen (not a rotation).
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.hidePassphrase()
    }

    when (val current = state) {
        BackupFlowState.Idle -> Unit
        is BackupFlowState.SettingUpPassphrase -> {
            BackHandler(onBack = viewModel::dismiss)
            PassphraseFormScreen(
                form = current.form,
                saving = current.saving,
                onBack = viewModel::dismiss,
                onSave = viewModel::savePassphrase,
            )
        }

        is BackupFlowState.CheckingPassphrase -> PassphraseCheckDialog(
            state = current,
            field = viewModel.checkField,
            onCheck = viewModel::check,
            onShow = { screenLock.require(unlockToShow, viewModel::reveal) },
            onSetNew = { screenLock.require(unlockToSetNew, viewModel::setNewPassphrase) },
            onBackUpAnyway = viewModel::backUpAnyway,
            onDismiss = viewModel::dismiss,
        )

        is BackupFlowState.PickingDocument -> LaunchedEffect(current) {
            if (!current.launched) {
                viewModel.onPickerLaunched()
                saveDocument.launch(current.fileName)
            }
        }

        is BackupFlowState.Working -> OrbitBlockingSheet {
            BackupProgressContent(current.stage, onCancel = viewModel::cancelWork)
        }

        is BackupFlowState.Done -> OrbitBottomSheet(onDismiss = viewModel::dismiss) { sheet ->
            BackupResultContent(current.outcome, onDone = { sheet.close {} })
        }

        is BackupFlowState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.backup_failed_title)) },
            text = {
                Text(stringResource(if (current.needsMoreMemory) R.string.backup_failed_memory_body else R.string.backup_failed_body))
            },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.common_done)) } },
        )
    }
}

/**
 * Every third Backup, before the Save picker: type the passphrase. A wrong one never blocks the
 * Backup: it offers Show passphrase and Set a new one (both behind the screen lock) and Back up
 * anyway.
 */
@Composable
fun PassphraseCheckDialog(
    state: BackupFlowState.CheckingPassphrase,
    field: TextFieldState,
    onCheck: () -> Unit,
    onShow: () -> Unit,
    onSetNew: () -> Unit,
    onBackUpAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // The typed or shown passphrase: kept out of screenshots, screen recordings and Recents.
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.passphrase_check_title)) },
        text = { PassphraseCheckContent(state, field, onCheck, onShow, onSetNew) },
        confirmButton = {
            if (state.revealed != null) {
                TextButton(onClick = onBackUpAnyway) { Text(stringResource(R.string.backup_now)) }
            } else {
                TextButton(onClick = onCheck) { Text(stringResource(R.string.passphrase_check_confirm)) }
            }
        },
        dismissButton = {
            if (state.wrong && state.revealed == null) {
                TextButton(onClick = onBackUpAnyway) { Text(stringResource(R.string.passphrase_check_back_up_anyway)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/** The check dialog's body, on its own so screenshot tests can draw it in place. */
@Composable
fun PassphraseCheckContent(
    state: BackupFlowState.CheckingPassphrase,
    field: TextFieldState,
    onCheck: () -> Unit,
    onShow: () -> Unit,
    onSetNew: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.passphrase_check_body))
        if (state.revealed != null) {
            Text(stringResource(R.string.passphrase_check_revealed), style = MaterialTheme.typography.labelLarge)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Copy marks the clip sensitive: hidden in the copy preview and keyboard clipboard.
                SensitiveClipboard {
                    SelectionContainer {
                        Text(
                            text = state.revealed,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = TextDirection.Ltr,
                            ),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            return@Column
        }
        PassphraseField(
            state = field,
            label = stringResource(R.string.passphrase_field),
            supportingText = if (state.wrong) stringResource(R.string.passphrase_check_wrong) else null,
            isError = state.wrong,
            imeAction = ImeAction.Done,
            onDone = onCheck,
        )
        if (state.wrong) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShow) { Text(stringResource(R.string.passphrase_check_show)) }
                TextButton(onClick = onSetNew) { Text(stringResource(R.string.passphrase_check_set_new)) }
            }
        }
    }
}

/** The blocking progress sheet: writing, then checking, with Cancel. */
@Composable
fun BackupProgressContent(stage: BackupMaker.Stage, onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
    ) {
        OrbitLoadingIndicator()
        Text(
            text = stringResource(
                when (stage) {
                    BackupMaker.Stage.Writing -> R.string.backup_progress_writing
                    BackupMaker.Stage.Verifying -> R.string.backup_progress_verifying
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
    }
}

/** "Backup saved and checked · Backed up 84 people · 41 photos". */
@Composable
fun BackupResultContent(outcome: BackupOutcome, onDone: () -> Unit) {
    val numbers = LocalOrbitFormats.current.numbers
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Icon(
            imageVector = OrbitIcons.Backup,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(R.string.backup_result_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            text = pluralStringResource(
                R.plurals.backed_up_result,
                outcome.people,
                numbers.format(outcome.people),
                pluralStringResource(R.plurals.backup_result_photos, outcome.photos, numbers.format(outcome.photos)),
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.backup_result_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.common_done))
        }
    }
}
