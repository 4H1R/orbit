package dev.alializadeh.orbit.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.backup.BackupSettingsKey
import dev.alializadeh.orbit.backup.lastBackupLine
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.lock.AppLockSettingsKey
import dev.alializadeh.orbit.lock.relockAfterName
import dev.alializadeh.orbit.person.dates.leadTimesText
import dev.alializadeh.orbit.reminders.RemindersSettingsKey
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
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshScreenLock()
        onPauseOrDispose {}
    }
    SettingsScreen(uiState = uiState, onOpen = onOpen)
}

/**
 * Settings: a grouped list where each row shows a one-line summary and opens a sub-screen pushed
 * on the Settings tab. A later slice adds About.
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onOpen: (NavKey) -> Unit,
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
        Spacer(Modifier.height(16.dp))
        OrbitListGroup(title = null) {
            NavigationRow(
                title = stringResource(R.string.settings_rings_title),
                summary = uiState.ringNames.joinToString(separator) { bidiIsolate(it) },
                index = 0,
                count = 2,
                onClick = { onOpen(RingsSettingsKey) },
            )
            NavigationRow(
                title = stringResource(R.string.settings_tags_title),
                summary = if (uiState.tagCount == 0) {
                    stringResource(R.string.settings_tags_none)
                } else {
                    pluralStringResource(
                        R.plurals.settings_tags_summary,
                        uiState.tagCount,
                        LocalOrbitFormats.current.numbers.format(uiState.tagCount),
                    )
                },
                index = 1,
                count = 2,
                onClick = { onOpen(TagsSettingsKey) },
            )
        }
        Spacer(Modifier.height(16.dp))
        OrbitListGroup(title = null) {
            NavigationRow(
                title = stringResource(R.string.settings_reminders_title),
                summary = listOf(
                    LocalOrbitFormats.current.numbers.time(uiState.reminderTime),
                    leadTimesText(uiState.defaultLeadTimes),
                ).joinToString(separator),
                index = 0,
                count = 2,
                onClick = { onOpen(RemindersSettingsKey) },
            )
            NavigationRow(
                title = stringResource(R.string.settings_app_lock_title),
                summary = when {
                    !uiState.appLock.enabled -> stringResource(R.string.settings_app_lock_off)
                    uiState.appLockPaused -> stringResource(R.string.settings_app_lock_paused)
                    else -> listOf(
                        stringResource(R.string.settings_app_lock_on),
                        relockAfterName(uiState.appLock.relockAfter),
                    ).joinToString(separator)
                },
                index = 1,
                count = 2,
                onClick = { onOpen(AppLockSettingsKey) },
            )
        }
        Spacer(Modifier.height(16.dp))
        OrbitListGroup(title = null) {
            NavigationRow(
                title = stringResource(R.string.settings_backup_title),
                summary = lastBackupLine(uiState.lastBackup, uiState.today),
                index = 0,
                count = 1,
                onClick = { onOpen(BackupSettingsKey) },
            )
        }
    }
}
