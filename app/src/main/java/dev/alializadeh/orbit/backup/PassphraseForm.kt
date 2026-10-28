package dev.alializadeh.orbit.backup

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.settings.MaxContentWidth
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import dev.alializadeh.orbit.ui.icons.OrbitIcons

/** Which new-passphrase screen: the first Backup's setup, a new one after a failed check, or Change passphrase. */
enum class PassphraseFormMode { Setup, NewAfterCheck, Change }

/**
 * A new Backup passphrase being typed: twice, plus the acknowledged warning, and in [Change] mode
 * the current one first. Text lives in [TextFieldState]s (digits converted as typed), the rest in
 * snapshot state.
 */
@Stable
class PassphraseForm(val mode: PassphraseFormMode) {
    val current = TextFieldState()
    val passphrase = TextFieldState()
    val repeat = TextFieldState()
    var acknowledged by mutableStateOf(false)

    /** Change passphrase only: the current passphrase typed didn't match. */
    var currentWrong by mutableStateOf(false)

    val newPassphrase: String get() = PassphraseRules.normalise(passphrase.text)

    val problem: PassphraseRules.Problem? get() = PassphraseRules.problem(newPassphrase)

    /** The repeat differs, once it's as long as the first (so it isn't flagged mid-typing). */
    val repeatMismatch: Boolean
        get() = repeat.text.length >= passphrase.text.length && repeat.text.isNotEmpty() &&
            PassphraseRules.normalise(repeat.text) != newPassphrase

    val canSave: Boolean
        get() = problem == null && PassphraseRules.normalise(repeat.text) == newPassphrase && acknowledged &&
            (mode != PassphraseFormMode.Change || current.text.isNotEmpty())
}

/**
 * A full-screen new-passphrase form: what it's for, the fields, the warning to acknowledge, and
 * Save. No strength meter and no hint.
 */
@Composable
fun PassphraseFormScreen(
    form: PassphraseForm,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { OrbitTopAppBar(title = null, onBack = onBack) },
        snackbarHost = { OrbitSnackbarHost() },
        bottomBar = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
            ) {
                Button(
                    onClick = onSave,
                    enabled = form.canSave && !saving,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.passphrase_save))
                }
            }
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
                Text(
                    text = stringResource(
                        when (form.mode) {
                            PassphraseFormMode.Setup -> R.string.passphrase_setup_title
                            PassphraseFormMode.NewAfterCheck -> R.string.passphrase_new_title
                            PassphraseFormMode.Change -> R.string.passphrase_change_title
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(
                        if (form.mode == PassphraseFormMode.Setup) R.string.passphrase_setup_body else R.string.passphrase_change_body,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (form.mode == PassphraseFormMode.Change) {
                    PassphraseField(
                        state = form.current,
                        label = stringResource(R.string.passphrase_current_field),
                        supportingText = if (form.currentWrong) stringResource(R.string.passphrase_current_wrong) else null,
                        isError = form.currentWrong,
                    )
                }
                val problem = form.problem
                val notAscii = problem == PassphraseRules.Problem.NotAscii
                PassphraseField(
                    state = form.passphrase,
                    label = stringResource(
                        if (form.mode == PassphraseFormMode.Change) R.string.passphrase_new_field else R.string.passphrase_field,
                    ),
                    supportingText = stringResource(if (notAscii) R.string.passphrase_rule_ascii else R.string.passphrase_rule_length),
                    isError = notAscii,
                )
                PassphraseField(
                    state = form.repeat,
                    label = stringResource(R.string.passphrase_repeat_field),
                    supportingText = if (form.repeatMismatch) stringResource(R.string.passphrase_mismatch) else null,
                    isError = form.repeatMismatch,
                    imeAction = ImeAction.Done,
                )
                PassphraseWarning(
                    acknowledged = form.acknowledged,
                    onAcknowledgedChange = { form.acknowledged = it },
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
            }
        }
    }
}

/** "If you forget this passphrase…", with the "I understand" box that must be ticked. */
@Composable
private fun PassphraseWarning(acknowledged: Boolean, onAcknowledgedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.passphrase_warning), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .toggleable(value = acknowledged, role = Role.Checkbox, onValueChange = onAcknowledgedChange),
            ) {
                Checkbox(checked = acknowledged, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Text(stringResource(R.string.passphrase_warning_ack), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * A Backup passphrase field: hidden with a show/hide toggle, always left-to-right, Persian and
 * Arabic digits converted as typed, and the keyboard asked for a Latin layout (`hintLocales` en)
 * with no suggestions or learning (a password field).
 */
@Composable
fun PassphraseField(
    state: TextFieldState,
    label: String,
    supportingText: String?,
    isError: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        inputTransformation = PassphraseInputTransformation,
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
            hintLocales = LocaleList("en"),
        ),
        onKeyboardAction = onDone?.let { done -> KeyboardActionHandler { done() } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) OrbitIcons.VisibilityOff else OrbitIcons.Visibility,
                    contentDescription = stringResource(if (visible) R.string.passphrase_hide else R.string.passphrase_show),
                )
            }
        },
    )
}
