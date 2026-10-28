package dev.alializadeh.orbit.backup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.ApplicationScope
import dev.alializadeh.orbit.OrbitLog
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why a Restore can't go on; each has its own message. */
enum class RestoreError {
    /** The passphrase field holds characters a Backup passphrase never has. */
    NotAsciiPassphrase,
    WrongPassphrase,
    NotABackup,
    Damaged,
    NewerVersion,
    NeedsMoreMemory,
    Unreadable,

    /** The swap itself failed; nothing changed. */
    RestoreFailed,
}

/** What the summary shows about a staged Backup. */
data class RestoreSummary(val createdAt: Instant, val people: Int, val interactions: Int, val photos: Int)

sealed interface RestoreUiState {
    /** The full-screen passphrase step; unlimited retries. */
    data class Passphrase(val error: RestoreError? = null, val opening: Boolean = false) : RestoreUiState

    /**
     * The summary, with the replace confirmation when the device has People ([devicePeople] > 0),
     * offering "Back up current data first" when its data changed since the last Backup.
     */
    data class Summary(
        val summary: RestoreSummary,
        val devicePeople: Int,
        val dataChanged: Boolean,
        val error: RestoreError? = null,
    ) : RestoreUiState {
        val confirmsReplace: Boolean get() = devicePeople > 0
        val offersBackUpFirst: Boolean get() = devicePeople > 0 && dataChanged
    }

    data object Restoring : RestoreUiState

    /** Restored: the app moves on (to Upcoming, or first run's next step). */
    data object Done : RestoreUiState
}

/**
 * Restore from the Backup at [uri] (picked with the Open picker), from Settings → Backup or from
 * Welcome: the passphrase, staging and validation, the summary and confirmation, then the swap.
 */
@HiltViewModel(assistedFactory = RestoreViewModel.Factory::class)
class RestoreViewModel @AssistedInject constructor(
    @Assisted private val uri: String,
    private val restorer: Restorer,
    private val documents: BackupDocuments,
    statusRepository: BackupStatusRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    val passphrase = TextFieldState()

    private sealed interface Step {
        data class Passphrase(val error: RestoreError? = null, val opening: Boolean = false) : Step
        data class Staged(val staged: StagedBackup, val error: RestoreError? = null) : Step
        data object Restoring : Step
        data object Done : Step
    }

    private val step = MutableStateFlow<Step>(Step.Passphrase())

    val uiState: StateFlow<RestoreUiState> = combine(step, statusRepository.status) { step, status ->
        when (step) {
            is Step.Passphrase -> RestoreUiState.Passphrase(step.error, step.opening)
            is Step.Staged -> RestoreUiState.Summary(
                summary = RestoreSummary(
                    createdAt = step.staged.manifest.createdAt,
                    people = step.staged.people,
                    interactions = step.staged.interactions,
                    photos = step.staged.photos,
                ),
                devicePeople = status.peopleCount,
                dataChanged = status.dataChanged,
                error = step.error,
            )

            Step.Restoring -> RestoreUiState.Restoring
            Step.Done -> RestoreUiState.Done
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RestoreUiState.Passphrase())

    init {
        // Editing the passphrase clears the last error.
        viewModelScope.launch {
            snapshotFlow { passphrase.text.toString() }.drop(1).collect {
                val current = step.value
                if (current is Step.Passphrase && current.error != null && !current.opening) step.value = Step.Passphrase()
            }
        }
    }

    /** Open backup: decrypt and validate everything into staging. */
    fun open() {
        val current = step.value as? Step.Passphrase ?: return
        if (current.opening || passphrase.text.isEmpty()) return
        val typed = PassphraseRules.normalise(passphrase.text)
        if (!PassphraseRules.isAscii(typed)) {
            step.value = Step.Passphrase(RestoreError.NotAsciiPassphrase)
            return
        }
        step.value = Step.Passphrase(opening = true)
        viewModelScope.launch {
            step.value = try {
                Step.Staged(restorer.stage({ documents.openInput(uri) }, typed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                OrbitLog.i(TAG, "Can't stage the backup", e)
                Step.Passphrase(e.toRestoreError())
            } catch (e: IOException) {
                OrbitLog.i(TAG, "Can't read the backup", e)
                Step.Passphrase(RestoreError.Unreadable)
            } catch (e: OutOfMemoryError) {
                Step.Passphrase(RestoreError.NeedsMoreMemory)
            }
        }
    }

    /** Replace (or Restore, with no People on the device): the swap. */
    fun restore() {
        val staged = (step.value as? Step.Staged)?.staged ?: return
        step.value = Step.Restoring
        viewModelScope.launch {
            step.value = try {
                restorer.apply(staged)
                Step.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OrbitLog.w(TAG, "Restore failed", e)
                Step.Staged(staged, RestoreError.RestoreFailed)
            }
        }
    }

    /**
     * A staged Backup nobody will restore is deleted, off the main thread. One still being staged
     * is deleted by [Restorer.stage] itself: this scope's cancel makes it drop its result.
     */
    override fun onCleared() {
        val staged = (step.value as? Step.Staged)?.staged ?: return
        applicationScope.launch(Dispatchers.IO) { restorer.discard(staged) }
    }

    private fun BackupException.toRestoreError() = when (this) {
        is BackupException.WrongPassphrase -> RestoreError.WrongPassphrase
        is BackupException.NotABackup -> RestoreError.NotABackup
        is BackupException.Corrupt -> RestoreError.Damaged
        is BackupException.NewerVersion -> RestoreError.NewerVersion
        is BackupException.NeedsMoreMemory -> RestoreError.NeedsMoreMemory
    }

    @AssistedFactory
    interface Factory {
        fun create(uri: String): RestoreViewModel
    }

    private companion object {
        const val TAG = "OrbitRestore"
    }
}
