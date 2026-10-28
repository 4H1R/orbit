package dev.alializadeh.orbit.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object ChangePassphraseKey : NavKey

/**
 * Change passphrase: the current one, then the new one twice and the warning. Only future Backups
 * use it; older files still open with the old one.
 */
@HiltViewModel
class ChangePassphraseViewModel @Inject constructor(
    private val passphrases: BackupPassphraseStore,
) : ViewModel() {

    val form = PassphraseForm(PassphraseFormMode.Change)

    private val saving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = saving.asStateFlow()

    private val saved = MutableStateFlow(false)

    /** True once the new passphrase is remembered: the screen closes. */
    val isSaved: StateFlow<Boolean> = saved.asStateFlow()

    fun save() {
        if (!form.canSave || saving.value) return
        saving.value = true
        viewModelScope.launch {
            if (passphrases.matches(form.current.text.toString())) {
                passphrases.remember(form.newPassphrase)
                saved.value = true
            } else {
                form.currentWrong = true
            }
            saving.value = false
        }
    }

    /** Typing in the current-passphrase field clears its error. */
    fun currentEdited() {
        form.currentWrong = false
    }
}

@Composable
fun ChangePassphraseRoute(onClose: () -> Unit, viewModel: ChangePassphraseViewModel = hiltViewModel()) {
    val saving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saved by viewModel.isSaved.collectAsStateWithLifecycle()
    val snackbars = LocalOrbitSnackbars.current
    val changed = stringResource(R.string.passphrase_changed_snackbar)
    LaunchedEffect(viewModel) {
        snapshotFlow { viewModel.form.current.text.toString() }.drop(1).collect { viewModel.currentEdited() }
    }
    LaunchedEffect(saved) {
        if (saved) {
            snackbars?.show(changed)
            onClose()
        }
    }
    PassphraseFormScreen(form = viewModel.form, saving = saving, onBack = onClose, onSave = viewModel::save)
}
