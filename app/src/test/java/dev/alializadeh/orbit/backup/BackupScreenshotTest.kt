package dev.alializadeh.orbit.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.testing.ScreenshotVariant
import dev.alializadeh.orbit.testing.SheetFrame
import dev.alializadeh.orbit.testing.captureScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Backup and Restore in en/LTR and fa/RTL, light and dark: passphrase setup, the check dialog,
 * Change passphrase, the progress and result sheets, Settings → Backup, and Restore's passphrase
 * step, summary and replace confirmation.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BackupScreenshotTest(private val variant: ScreenshotVariant) {

    private val today = LocalDate.of(2026, 10, 28)
    private val backupDate = Instant.parse("2026-10-12T09:00:00Z")

    @Test
    fun passphraseSetup() = captureScreen("passphrase_setup", variant, tall = true) {
        PassphraseFormScreen(form = PassphraseForm(PassphraseFormMode.Setup), saving = false, onBack = {}, onSave = {})
    }

    /** Typed twice, the warning acknowledged: Save is enabled. */
    @Test
    fun passphraseSetupFilled() = captureScreen("passphrase_setup_filled", variant) {
        val form = PassphraseForm(PassphraseFormMode.Setup).apply {
            passphrase.setTextAndPlaceCursorAtEnd("correct horse battery staple")
            repeat.setTextAndPlaceCursorAtEnd("correct horse battery staple")
            acknowledged = true
        }
        PassphraseFormScreen(form = form, saving = false, onBack = {}, onSave = {})
    }

    /** A Persian letter in the passphrase, and a repeat that doesn't match. */
    @Test
    fun passphraseSetupErrors() = captureScreen("passphrase_setup_errors", variant) {
        val form = PassphraseForm(PassphraseFormMode.Setup).apply {
            passphrase.setTextAndPlaceCursorAtEnd("orbit سلام 2026")
            repeat.setTextAndPlaceCursorAtEnd("orbit something else")
        }
        PassphraseFormScreen(form = form, saving = false, onBack = {}, onSave = {})
    }

    @Test
    fun changePassphrase() = captureScreen("passphrase_change", variant, tall = true) {
        val form = PassphraseForm(PassphraseFormMode.Change).apply {
            current.setTextAndPlaceCursorAtEnd("the old passphrase")
            currentWrong = true
        }
        PassphraseFormScreen(form = form, saving = false, onBack = {}, onSave = {})
    }

    @Test
    fun checkDialog() = captureScreen("passphrase_check", variant) {
        CheckDialogFrame(BackupFlowState.CheckingPassphrase(), TextFieldState())
    }

    /** A wrong attempt: Show passphrase, Set a new one, Back up anyway. */
    @Test
    fun checkDialogWrong() = captureScreen("passphrase_check_wrong", variant) {
        CheckDialogFrame(BackupFlowState.CheckingPassphrase(wrong = true), TextFieldState("correct horse"))
    }

    /** After Show passphrase and the screen lock. */
    @Test
    fun checkDialogRevealed() = captureScreen("passphrase_check_revealed", variant) {
        CheckDialogFrame(
            BackupFlowState.CheckingPassphrase(wrong = true, revealed = "correct horse battery staple"),
            TextFieldState(),
        )
    }

    @Test
    fun progressSheet() = captureScreen("backup_progress", variant) {
        SheetFrame { BackupProgressContent(BackupMaker.Stage.Verifying, onCancel = {}) }
    }

    @Test
    fun resultSheet() = captureScreen("backup_result", variant) {
        SheetFrame { BackupResultContent(BackupOutcome(people = 84, photos = 41), onDone = {}) }
    }

    @Test
    fun backupSettings() = captureScreen("backup_settings", variant, tall = true) {
        BackupSettingsScreen(
            uiState = BackupSettingsUiState.Loaded(
                today = today,
                lastBackup = today.minusDays(16),
                hasPassphrase = true,
                nudgeInterval = BackupNudgeInterval.Days30,
            ),
            onBack = {},
            onBackUpNow = {},
            onRestore = {},
            onChangePassphrase = {},
            onNudgeIntervalSelect = {},
        )
    }

    @Test
    fun backupSettingsNever() = captureScreen("backup_settings_never", variant) {
        BackupSettingsScreen(
            uiState = BackupSettingsUiState.Loaded(today, lastBackup = null, hasPassphrase = false, BackupNudgeInterval.Days30),
            onBack = {},
            onBackUpNow = {},
            onRestore = {},
            onChangePassphrase = {},
            onNudgeIntervalSelect = {},
        )
    }

    @Test
    fun restorePassphrase() = captureScreen("restore_passphrase", variant) {
        Restore(RestoreUiState.Passphrase(error = RestoreError.WrongPassphrase), TextFieldState("not it at all"))
    }

    @Test
    fun restoreOpening() = captureScreen("restore_opening", variant) {
        Restore(RestoreUiState.Passphrase(opening = true), TextFieldState("correct horse battery staple"))
    }

    @Test
    fun restoreNotABackup() = captureScreen("restore_not_a_backup", variant) {
        Restore(RestoreUiState.Passphrase(error = RestoreError.NotABackup), TextFieldState("correct horse battery staple"))
    }

    /** People on the device whose data changed since the last Backup: the full confirmation. */
    @Test
    fun restoreSummaryReplace() = captureScreen("restore_summary_replace", variant, tall = true) {
        Restore(
            RestoreUiState.Summary(RestoreSummary(backupDate, people = 84, interactions = 312, photos = 41), devicePeople = 12, dataChanged = true),
        )
    }

    /** A first-run phone: nothing to replace. */
    @Test
    fun restoreSummaryEmptyPhone() = captureScreen("restore_summary", variant) {
        Restore(
            RestoreUiState.Summary(RestoreSummary(backupDate, people = 1, interactions = 1, photos = 0), devicePeople = 0, dataChanged = true),
        )
    }

    @Test
    fun restoring() = captureScreen("restore_progress", variant) {
        Restore(RestoreUiState.Restoring)
    }

    @Composable
    private fun Restore(uiState: RestoreUiState, passphrase: TextFieldState = TextFieldState()) {
        RestoreScreen(
            uiState = uiState,
            passphrase = passphrase,
            onClose = {},
            onOpen = {},
            onRestore = {},
            onBackUpFirst = {},
            zone = ZoneOffset.UTC,
        )
    }

    /** The check dialog drawn in place (an AlertDialog is its own window, which the capture misses). */
    @Composable
    private fun CheckDialogFrame(state: BackupFlowState.CheckingPassphrase, field: TextFieldState) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                modifier = Modifier.padding(horizontal = 48.dp),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.passphrase_check_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    PassphraseCheckContent(state, field, onCheck = {}, onShow = {}, onSetNew = {})
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    ) {
                        val dismiss = if (state.wrong && state.revealed == null) R.string.passphrase_check_back_up_anyway else R.string.common_cancel
                        val confirm = if (state.revealed != null) R.string.backup_now else R.string.passphrase_check_confirm
                        TextButton(onClick = {}) { Text(stringResource(dismiss)) }
                        TextButton(onClick = {}) { Text(stringResource(confirm)) }
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.entries
    }
}
