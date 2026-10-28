package dev.alializadeh.orbit.backup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.OrbitLog
import dev.alializadeh.orbit.data.PreferencesRepository
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Where Back up now is. */
sealed interface BackupFlowState {
    data object Idle : BackupFlowState

    /** The full-screen new-passphrase form ([PassphraseFormMode.Setup] or [PassphraseFormMode.NewAfterCheck]). */
    data class SettingUpPassphrase(val form: PassphraseForm, val saving: Boolean = false) : BackupFlowState

    /** Every third Backup: the dialog asking for the passphrase before the Save picker. */
    data class CheckingPassphrase(
        /** The last attempt didn't match: offer Show passphrase, Set a new one, Back up anyway. */
        val wrong: Boolean = false,
        /** After Show passphrase (and the screen lock): the remembered passphrase. */
        val revealed: String? = null,
    ) : BackupFlowState

    /** The Save picker is to be opened with this suggested name; [launched] once it is. */
    data class PickingDocument(val fileName: String, val launched: Boolean = false) : BackupFlowState

    /** The blocking progress sheet. */
    data class Working(val stage: BackupMaker.Stage) : BackupFlowState

    /** The result sheet: written, re-read and verified. */
    data class Done(val outcome: BackupOutcome) : BackupFlowState

    /**
     * Nothing was kept: the partial file is deleted and "last Backup" is unchanged. [needsMoreMemory]:
     * the phone didn't give Orbit enough memory to encrypt it.
     */
    data class Failed(val needsMoreMemory: Boolean = false) : BackupFlowState
}

/**
 * Back up now, from wherever it starts (Upcoming's nudge, Settings → Backup, Restore's "Back up
 * current data first"). Scoped to the activity and drawn above every screen by `BackupHost`.
 *
 * First Backup: passphrase setup, then the Save picker. Every third Backup: the passphrase check
 * first; a wrong attempt never blocks the Backup. Then the Save picker (every time), the blocking
 * progress sheet (Cancel deletes the partial file), and the result sheet once the written file has
 * been read back and verified.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val passphrases: BackupPassphraseStore,
    private val maker: BackupMaker,
    private val preferences: PreferencesRepository,
    private val notices: RestoreNotices,
    private val clock: Clock,
) : ViewModel() {

    private val flow = MutableStateFlow<BackupFlowState>(BackupFlowState.Idle)
    val state: StateFlow<BackupFlowState> = flow.asStateFlow()

    /** The check dialog's field. */
    val checkField = TextFieldState()

    /** "Restored 84 people", waiting for the app shell to show it. */
    val restoredPeople: StateFlow<Int?> = notices.restoredPeople

    private var work: Job? = null

    /** Back up now. Ignored while a Backup is already under way. */
    fun start() {
        val current = flow.value
        if (current !is BackupFlowState.Idle && current !is BackupFlowState.Done && current !is BackupFlowState.Failed) return
        viewModelScope.launch {
            flow.value = when {
                passphrases.current() == null -> BackupFlowState.SettingUpPassphrase(PassphraseForm(PassphraseFormMode.Setup))
                BackupRules.passphraseCheckDue(preferences.deviceState.first()) -> {
                    checkField.clearText()
                    BackupFlowState.CheckingPassphrase()
                }

                else -> picking()
            }
        }
    }

    /** Saves the new passphrase from the form, then goes on to the Save picker. */
    fun savePassphrase() {
        val setup = flow.value as? BackupFlowState.SettingUpPassphrase ?: return
        if (!setup.form.canSave || setup.saving) return
        flow.value = setup.copy(saving = true)
        viewModelScope.launch {
            flow.value = if (passphrases.remember(setup.form.newPassphrase)) picking() else BackupFlowState.Failed()
        }
    }

    fun check() {
        if (flow.value !is BackupFlowState.CheckingPassphrase) return
        viewModelScope.launch {
            if (passphrases.matches(checkField.text.toString())) {
                preferences.updateDeviceState(BackupRules::passphraseTyped)
                flow.value = picking()
            } else {
                flow.value = BackupFlowState.CheckingPassphrase(wrong = true)
            }
        }
    }

    /** Show passphrase, once the screen lock has been passed. */
    fun reveal() {
        if (flow.value !is BackupFlowState.CheckingPassphrase) return
        viewModelScope.launch {
            flow.value = BackupFlowState.CheckingPassphrase(wrong = true, revealed = passphrases.current())
        }
    }

    /**
     * Orbit went to the background: a shown passphrase is hidden again (it would wait there for
     * whoever opens Orbit next); showing it again needs the screen lock again.
     */
    fun hidePassphrase() {
        val checking = flow.value as? BackupFlowState.CheckingPassphrase ?: return
        if (checking.revealed != null) flow.value = checking.copy(revealed = null)
    }

    /** Set a new one, once the screen lock has been passed. */
    fun setNewPassphrase() {
        if (flow.value !is BackupFlowState.CheckingPassphrase) return
        flow.value = BackupFlowState.SettingUpPassphrase(PassphraseForm(PassphraseFormMode.NewAfterCheck))
    }

    /** Goes on with the remembered passphrase after a failed or skipped check. */
    fun backUpAnyway() {
        if (flow.value !is BackupFlowState.CheckingPassphrase) return
        viewModelScope.launch {
            preferences.updateDeviceState(BackupRules::passphraseTyped)
            flow.value = picking()
        }
    }

    fun onPickerLaunched() {
        val picking = flow.value as? BackupFlowState.PickingDocument ?: return
        flow.value = picking.copy(launched = true)
    }

    /** The Save picker's answer: null when the user backed out. */
    fun onDocumentPicked(uri: String?) {
        if (flow.value !is BackupFlowState.PickingDocument) return
        if (uri == null) {
            flow.value = BackupFlowState.Idle
            return
        }
        flow.value = BackupFlowState.Working(BackupMaker.Stage.Writing)
        work = viewModelScope.launch {
            flow.value = try {
                val passphrase = passphrases.current() ?: throw IOException("No passphrase")
                val outcome = maker.backUp(uri, passphrase, onStage = { flow.value = BackupFlowState.Working(it) })
                BackupFlowState.Done(outcome)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException.NeedsMoreMemory) {
                OrbitLog.w(TAG, "Backup failed: out of memory")
                BackupFlowState.Failed(needsMoreMemory = true)
            } catch (e: Exception) {
                // Storage, the provider, SQLite or the verification: whatever it was, nothing was kept.
                OrbitLog.w(TAG, "Backup failed", e)
                BackupFlowState.Failed()
            }
        }
    }

    /** Cancel on the progress sheet: stops writing; the partial file is deleted. */
    fun cancelWork() {
        if (flow.value !is BackupFlowState.Working) return
        work?.cancel()
        flow.value = BackupFlowState.Idle
    }

    /** Closes the passphrase form, the check dialog, the result sheet or the failure dialog. */
    fun dismiss() {
        if (flow.value is BackupFlowState.Working) return
        flow.value = BackupFlowState.Idle
    }

    fun consumeRestored() = notices.consume()

    private fun picking() = BackupFlowState.PickingDocument(suggestedBackupFileName(LocalDate.now(clock)))

    private companion object {
        const val TAG = "OrbitBackup"
    }
}
