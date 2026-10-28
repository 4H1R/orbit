package dev.alializadeh.orbit.wishes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.ui.icon
import dev.alializadeh.orbit.person.ui.label
import dev.alializadeh.orbit.ui.components.LocalOrbitSnackbars
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import dev.alializadeh.orbit.ui.components.sensitiveClipData
import dev.alializadeh.orbit.ui.expressive.OrbitBottomSheet
import dev.alializadeh.orbit.ui.expressive.SheetCloser
import kotlinx.coroutines.launch

/**
 * Draws [viewModel]'s Wishes sheet when one is open, over everything (the shell calls it). Send
 * copies the message first when the app can't take a text, opens the app with plain
 * `startActivity` (time away for App lock), then logs the message if asked.
 */
@Composable
fun WishesHost(viewModel: WishesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet = state ?: return
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbars = LocalOrbitSnackbars.current
    val noApp = stringResource(R.string.contact_no_app)
    val copied = stringResource(R.string.wishes_copied_snackbar)
    WishesSheet(
        state = sheet,
        message = viewModel.message,
        onLanguage = viewModel::setLanguage,
        onChannel = viewModel::setChannel,
        onLogAsMessage = viewModel::setLogAsMessage,
        onSend = send@{
            val launch = viewModel.launch() ?: return@send
            if (launch.copyFirst) {
                val text = viewModel.message.text.toString().trim()
                scope.launch { clipboard.setClipEntry(ClipEntry(sensitiveClipData(text))) }
            }
            try {
                context.startActivity(launch.intent(context))
            } catch (_: ActivityNotFoundException) {
                snackbars?.show(noApp)
                return@send
            }
            viewModel.sent()
            // Android 13 and later confirm the copy themselves, but not where to paste it.
            if (launch.copyFirst) snackbars?.show(copied)
        },
        onDismiss = viewModel::dismiss,
    )
}

/** The intent [this] starts: the share sheet goes through a chooser. */
private fun WishLaunch.intent(context: Context): Intent {
    if (intentAction == Intent.ACTION_SEND) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sendText)
        return Intent.createChooser(send, context.getString(R.string.wishes_send))
    }
    val intent = Intent(intentAction, uri?.toUri())
    smsBody?.let { intent.putExtra(SMS_BODY, it) }
    return intent
}

/** The extra SMS apps read their text from. */
private const val SMS_BODY = "sms_body"

@Composable
fun WishesSheet(
    state: WishesSheetState,
    message: TextFieldState,
    onLanguage: (OrbitLanguage) -> Unit,
    onChannel: (String) -> Unit,
    onLogAsMessage: (Boolean) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    OrbitBottomSheet(onDismiss = onDismiss) { sheet ->
        WishesSheetContent(state, message, onLanguage, onChannel, onLogAsMessage, onSend, sheet)
    }
}

/**
 * The inside of [WishesSheet]: the message's language, the message, Send with (each Contact method
 * that can carry a message, then Other app), Log as a message, then Send. Drawn by itself in
 * screenshot tests.
 */
@Composable
internal fun WishesSheetContent(
    state: WishesSheetState,
    message: TextFieldState,
    onLanguage: (OrbitLanguage) -> Unit,
    onChannel: (String) -> Unit,
    onLogAsMessage: (Boolean) -> Unit,
    onSend: () -> Unit,
    sheet: SheetCloser,
) {
    val formats = LocalOrbitFormats.current
    val name = state.names.displayName(formats.language)
    val separator = stringResource(R.string.separator)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.wishes_sheet_title, bidiIsolate(name)),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        val languageLabel = stringResource(R.string.wishes_language)
        OrbitConnectedChoice(
            options = OrbitLanguage.entries,
            selected = state.language,
            onSelect = onLanguage,
            label = { it.ownName() },
            modifier = Modifier.semantics { contentDescription = languageLabel },
        )
        OutlinedTextField(
            state = message,
            label = { Text(stringResource(R.string.wishes_message)) },
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
            // The message's own direction, whatever the UI's: a Persian message in English UI too.
            textStyle = LocalTextStyle.current.copy(
                textDirection = if (state.language == OrbitLanguage.Persian) TextDirection.Rtl else TextDirection.Ltr,
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.wishes_send_with),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.channels.forEach { channel ->
                val label = when (channel) {
                    WishChannel.OtherApp -> stringResource(R.string.wishes_other_app)
                    is WishChannel.Contact -> listOfNotNull(channel.kind.label(), channel.method.label?.let(::bidiIsolate))
                        .joinToString(separator)
                }
                val icon = when (channel) {
                    WishChannel.OtherApp -> Icons.Filled.Share
                    is WishChannel.Contact -> channel.kind.icon
                }
                FilterChip(
                    selected = channel.key == state.channelKey,
                    onClick = { onChannel(channel.key) },
                    label = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        // Always laid out, so choosing another app doesn't change the sheet's height.
        Text(
            text = if (WishChannels.copiesFirst(state.channel)) stringResource(R.string.wishes_copy_hint) else "",
            minLines = 2,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = state.logAsMessage, role = Role.Checkbox, onValueChange = onLogAsMessage),
        ) {
            Checkbox(checked = state.logAsMessage, onCheckedChange = null)
            Text(stringResource(R.string.wishes_log_as_message), modifier = Modifier.padding(start = 12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { sheet.close {} }) { Text(stringResource(R.string.common_cancel)) }
            Button(
                enabled = message.text.isNotBlank(),
                // Send closes the sheet itself (the ViewModel), once the app has opened.
                onClick = onSend,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.wishes_send_action), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** A language's name in its own script: English / فارسی. */
@Composable
private fun OrbitLanguage.ownName(): String = stringResource(
    when (this) {
        OrbitLanguage.English -> R.string.language_name_english
        OrbitLanguage.Persian -> R.string.language_name_persian
    },
)

