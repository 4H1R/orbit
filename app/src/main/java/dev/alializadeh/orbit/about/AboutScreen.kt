package dev.alializadeh.orbit.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.IoDispatcher
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolateLtr
import dev.alializadeh.orbit.lock.OrbitMark
import dev.alializadeh.orbit.settings.DestructiveRow
import dev.alializadeh.orbit.settings.OrbitSettingsScaffold
import dev.alializadeh.orbit.settings.SettingsFooter
import dev.alializadeh.orbit.settings.SettingsGroupGap
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.NavigationRow
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object AboutKey : NavKey

data class AboutUiState(
    val version: AppVersion,
    /** Crash reports in the log; null until it's been read. */
    val crashes: Int? = null,
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    version: AppVersion,
    private val crashLog: CrashLog,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val state = MutableStateFlow(AboutUiState(version))
    val uiState: StateFlow<AboutUiState> = state.asStateFlow()

    /** On every resume: a crash may have been recorded since (in an earlier process). */
    fun refresh() {
        viewModelScope.launch {
            val count = withContext(io) { crashLog.count() }
            state.value = state.value.copy(crashes = count)
        }
    }

    fun clearCrashLog(then: () -> Unit) {
        viewModelScope.launch {
            withContext(io) { crashLog.clear() }
            state.value = state.value.copy(crashes = 0)
            then()
        }
    }

    /** The file to share. */
    fun crashLogFile() = crashLog.logFile
}

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    onOpenLicences: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    val snackbars = LocalOrbitSnackbars.current
    val cleared = stringResource(R.string.about_crash_log_cleared)
    AboutScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenLicences = onOpenLicences,
        onShareCrashLog = { shareCrashLog(context, viewModel.crashLogFile()) },
        onClearCrashLog = { viewModel.clearCrashLog { snackbars?.show(cleared) } },
    )
}

/**
 * Settings → About: Orbit's version, the open-source licences, and the crash log (Share, or "No
 * crashes recorded"; Clear once there's something in it).
 */
@Composable
fun AboutScreen(
    uiState: AboutUiState,
    onBack: () -> Unit,
    onOpenLicences: () -> Unit,
    onShareCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitSettingsScaffold(title = stringResource(R.string.settings_about_title), onBack = onBack, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            OrbitMark(Modifier.padding(bottom = 12.dp))
            Text(stringResource(R.string.about_product_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.about_version) + stringResource(R.string.separator) + versionText(uiState.version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.welcome_line),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        OrbitListGroup(title = null) {
            NavigationRow(
                title = stringResource(R.string.about_licences),
                summary = stringResource(R.string.about_licences_summary),
                index = 0,
                count = 1,
                onClick = onOpenLicences,
            )
        }
        val crashes = uiState.crashes ?: 0
        OrbitListGroup(title = stringResource(R.string.about_crash_log_title)) {
            NavigationRow(
                title = stringResource(R.string.about_crash_log_share),
                summary = if (crashes == 0) {
                    stringResource(R.string.about_crash_log_none)
                } else {
                    pluralStringResource(R.plurals.about_crash_log_count, crashes, LocalOrbitFormats.current.numbers.format(crashes))
                },
                index = 0,
                count = 1,
                onClick = onShareCrashLog,
                enabled = crashes > 0,
            )
        }
        SettingsFooter(stringResource(R.string.about_crash_log_footer))
        if (crashes > 0) {
            SettingsGroupGap()
            OrbitListGroup(title = null) {
                DestructiveRow(
                    title = stringResource(R.string.about_crash_log_clear),
                    icon = Icons.Filled.Delete,
                    index = 0,
                    count = 1,
                    onClick = onClearCrashLog,
                )
            }
        }
    }
}

/** "0.1.0 (1)": an identifier, so it stays in Latin digits and left to right in both languages. */
@Composable
private fun versionText(version: AppVersion): String =
    stringResource(R.string.about_version_value, bidiIsolateLtr(version.name), bidiIsolateLtr(version.code.toString()))

/**
 * Shares the crash log through the system share sheet. Plain [Context.startActivity] (not for a
 * result): sending it leaves Orbit, so App lock counts the time away.
 */
private fun shareCrashLog(context: Context, file: java.io.File) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, context.packageName + CRASH_LOG_AUTHORITY_SUFFIX, file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_crash_log_subject))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser passes the read grant on only through ClipData.
    send.clipData = ClipData.newRawUri(null, uri)
    try {
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.about_crash_log_share)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    } catch (_: ActivityNotFoundException) {
        // Every phone has the share sheet.
    }
}

/** The FileProvider (manifest) that shares `filesDir/crash`: `<applicationId>.crashlog`. */
const val CRASH_LOG_AUTHORITY_SUFFIX = ".crashlog"
