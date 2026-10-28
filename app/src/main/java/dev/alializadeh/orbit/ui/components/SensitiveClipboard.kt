package dev.alializadeh.orbit.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard

/**
 * Plain text for the clipboard, marked sensitive and with no label: the keyboard's clipboard
 * strip and Android 13+'s copy preview hide the text, and the label doesn't repeat it.
 */
fun sensitiveClipData(text: CharSequence): ClipData = ClipData.newPlainText(null, text).markedSensitive()

/** Marks this clip sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`; the same key is honoured below 13). */
fun ClipData.markedSensitive(): ClipData = apply {
    val extras = description.extras ?: PersistableBundle()
    extras.putBoolean(IS_SENSITIVE_KEY, true)
    description.extras = extras
}

private val IS_SENSITIVE_KEY =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"

/** Everything copied inside [content] (a `SelectionContainer`'s Copy too) is marked sensitive. */
@Composable
fun SensitiveClipboard(content: @Composable () -> Unit) {
    val clipboard = LocalClipboard.current
    val sensitive = remember(clipboard) { SensitiveClipboardWrapper(clipboard) }
    CompositionLocalProvider(LocalClipboard provides sensitive, content = content)
}

private class SensitiveClipboardWrapper(private val clipboard: Clipboard) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = clipboard.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        clipboard.setClipEntry(clipEntry?.let { ClipEntry(it.clipData.markedSensitive()) })
    }
}
