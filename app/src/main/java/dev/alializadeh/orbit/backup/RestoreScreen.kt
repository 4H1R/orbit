package dev.alializadeh.orbit.backup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.calendar.inCalendar
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import java.time.ZoneId
import kotlinx.serialization.Serializable

/** Restore from the Backup the Open picker returned, pushed on the Settings tab. */
@Serializable
data class RestoreKey(val uri: String) : NavKey

/**
 * The Restore flow for [uri]. [onRestored] runs once the data is replaced; [onBackUpFirst] (null
 * where it can't apply, as on Welcome) starts Back up now from the confirmation.
 */
@Composable
fun RestoreRoute(
    uri: String,
    onClose: () -> Unit,
    onRestored: () -> Unit,
    onBackUpFirst: (() -> Unit)?,
    viewModel: RestoreViewModel = hiltViewModel<RestoreViewModel, RestoreViewModel.Factory>(
        key = "restore:$uri",
        creationCallback = { factory -> factory.create(uri) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState) {
        if (uiState == RestoreUiState.Done) onRestored()
    }
    RestoreScreen(
        uiState = uiState,
        passphrase = viewModel.passphrase,
        onClose = onClose,
        onOpen = viewModel::open,
        onRestore = viewModel::restore,
        onBackUpFirst = onBackUpFirst,
    )
}

/** Restore's full-screen steps: passphrase, summary with the replace confirmation, progress. */
@Composable
fun RestoreScreen(
    uiState: RestoreUiState,
    passphrase: TextFieldState,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onBackUpFirst: (() -> Unit)?,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    // Nothing may interrupt the swap. Opening can be left (a provider may hang): staging then
    // throws its result away.
    val busy = uiState is RestoreUiState.Restoring || uiState is RestoreUiState.Done
    BackHandler(enabled = busy) {}
    Scaffold(
        modifier = modifier,
        topBar = { OrbitTopAppBar(title = null, onBack = if (busy) null else onClose) },
        snackbarHost = { OrbitSnackbarHost() },
        bottomBar = {
            RestoreButtons(uiState, passphrase, onOpen, onRestore, onBackUpFirst)
        },
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (uiState) {
                    is RestoreUiState.Passphrase -> PassphraseStep(uiState, passphrase, onOpen)
                    is RestoreUiState.Summary -> SummaryStep(uiState, zone)
                    RestoreUiState.Restoring, RestoreUiState.Done -> Progress(stringResource(R.string.restore_progress))
                }
            }
        }
    }
}

@Composable
private fun PassphraseStep(uiState: RestoreUiState.Passphrase, passphrase: TextFieldState, onOpen: () -> Unit) {
    Title(stringResource(R.string.restore_title))
    Text(
        text = stringResource(R.string.restore_passphrase_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    val fieldError = when (uiState.error) {
        RestoreError.WrongPassphrase -> stringResource(R.string.restore_wrong_passphrase)
        RestoreError.NotAsciiPassphrase -> stringResource(R.string.passphrase_rule_ascii)
        else -> null
    }
    PassphraseField(
        state = passphrase,
        label = stringResource(R.string.passphrase_field),
        supportingText = fieldError,
        isError = fieldError != null,
        imeAction = ImeAction.Done,
        onDone = onOpen,
    )
    if (fieldError == null && uiState.error != null) ErrorCard(restoreErrorText(uiState.error))
    if (uiState.opening) Progress(stringResource(R.string.restore_checking))
}

@Composable
private fun SummaryStep(uiState: RestoreUiState.Summary, zone: ZoneId) {
    val formats = LocalOrbitFormats.current
    val numbers = formats.numbers
    val summary = uiState.summary
    val date = formats.dates.format(summary.createdAt.atZone(zone).toLocalDate().inCalendar(formats.primaryCalendar))
    Title(stringResource(R.string.restore_summary_title, date))
    Text(
        text = listOf(
            pluralStringResource(R.plurals.restore_summary_people, summary.people, numbers.format(summary.people)),
            pluralStringResource(R.plurals.restore_summary_interactions, summary.interactions, numbers.format(summary.interactions)),
            pluralStringResource(R.plurals.restore_summary_photos, summary.photos, numbers.format(summary.photos)),
        ).joinToString(stringResource(R.string.separator)),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.restore_summary_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (uiState.confirmsReplace) {
        val colors = MaterialTheme.colorScheme
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.errorContainer,
            contentColor = colors.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.restore_replace_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = pluralStringResource(
                            R.plurals.restore_replace_body,
                            uiState.devicePeople,
                            numbers.format(uiState.devicePeople),
                            date,
                            pluralStringResource(R.plurals.restore_summary_people, summary.people, numbers.format(summary.people)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    uiState.error?.let { ErrorCard(restoreErrorText(it)) }
}

@Composable
private fun RestoreButtons(
    uiState: RestoreUiState,
    passphrase: TextFieldState,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onBackUpFirst: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
    ) {
        val width = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()
        when (uiState) {
            is RestoreUiState.Passphrase -> Button(
                onClick = onOpen,
                enabled = !uiState.opening && passphrase.text.isNotEmpty(),
                modifier = width,
            ) {
                Text(stringResource(R.string.restore_open))
            }

            is RestoreUiState.Summary -> {
                if (uiState.offersBackUpFirst && onBackUpFirst != null) {
                    OutlinedButton(onClick = onBackUpFirst, modifier = width) {
                        Text(stringResource(R.string.restore_back_up_first))
                    }
                }
                if (uiState.confirmsReplace) {
                    Button(
                        onClick = onRestore,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = width,
                    ) {
                        Text(stringResource(R.string.restore_replace_confirm))
                    }
                } else {
                    Button(onClick = onRestore, modifier = width) { Text(stringResource(R.string.restore_confirm)) }
                }
            }

            RestoreUiState.Restoring, RestoreUiState.Done -> Unit
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Progress(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    ) {
        OrbitLoadingIndicator()
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorCard(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun restoreErrorText(error: RestoreError): String = stringResource(
    when (error) {
        RestoreError.NotAsciiPassphrase -> R.string.passphrase_rule_ascii
        RestoreError.WrongPassphrase -> R.string.restore_wrong_passphrase
        RestoreError.NotABackup -> R.string.restore_not_a_backup
        RestoreError.Damaged -> R.string.restore_damaged
        RestoreError.NewerVersion -> R.string.restore_newer_version
        RestoreError.NeedsMoreMemory -> R.string.restore_needs_memory
        RestoreError.Unreadable -> R.string.restore_unreadable
        RestoreError.RestoreFailed -> R.string.restore_failed
    },
)
