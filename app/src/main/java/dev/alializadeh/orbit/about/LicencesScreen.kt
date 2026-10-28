package dev.alializadeh.orbit.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.settings.OrbitSettingsScaffold
import dev.alializadeh.orbit.ui.components.ContentRow
import dev.alializadeh.orbit.ui.components.NavigationRow
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object LicencesKey : NavKey

/** One licence's text, for [item] (its name is the title). */
@Serializable
data class LicenceTextKey(val item: String, val licence: Licence) : NavKey

/** Settings → About → Open-source licences: the bundled fonts, then every library, each with its licence. */
@Composable
fun LicencesScreen(onBack: () -> Unit, onOpen: (LicenceTextKey) -> Unit, modifier: Modifier = Modifier) {
    OrbitSettingsScaffold(title = stringResource(R.string.about_licences), onBack = onBack, modifier = modifier) {
        LicenceGroup(stringResource(R.string.about_licences_fonts), OpenSourceLicences.fonts, onOpen)
        LicenceGroup(stringResource(R.string.about_licences_libraries), OpenSourceLicences.libraries, onOpen)
    }
}

@Composable
private fun LicenceGroup(title: String, items: List<OpenSourceItem>, onOpen: (LicenceTextKey) -> Unit) {
    // One row per item and licence (MaterialKolor has two).
    val rows = items.flatMap { item -> item.licences.map { item to it } }
    OrbitListGroup(title = title) {
        rows.forEachIndexed { index, (item, licence) ->
            NavigationRow(
                title = item.name,
                summary = licence.title,
                index = index,
                count = rows.size,
                onClick = { onOpen(LicenceTextKey(item.name, licence)) },
            )
        }
    }
}

/**
 * A licence's text, as it's written: English and left to right in both UI languages. The bundled
 * files are hard-wrapped for a terminal, so each paragraph is joined back into one and rewrapped to
 * the screen (and the font size).
 */
@Composable
fun LicenceTextRoute(key: LicenceTextKey, onBack: () -> Unit) {
    val context = LocalContext.current
    var paragraphs by remember(key) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(key) {
        paragraphs = withContext(Dispatchers.IO) {
            context.assets.open(key.licence.asset).bufferedReader().use { reflowed(it.readText()) }
        }
    }
    LicenceTextScreen(key = key, paragraphs = paragraphs, onBack = onBack)
}

@Composable
fun LicenceTextScreen(key: LicenceTextKey, paragraphs: List<String>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    OrbitSettingsScaffold(title = key.item, onBack = onBack, modifier = modifier) {
        OrbitListGroup(title = key.licence.title) {
            ContentRow(index = 0, count = 1) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        paragraphs.forEach { paragraph ->
                            Text(
                                text = paragraph,
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** [text]'s paragraphs (separated by blank lines or rules), each joined into one line. */
internal fun reflowed(text: String): List<String> =
    text.lines()
        // A rule of dashes is a paragraph break, not words.
        .map { line -> if (line.isNotBlank() && line.all { it == '-' || it == '=' || it.isWhitespace() }) "" else line }
        .joinToString("\n")
        .split(Regex("\n\\s*\n"))
        .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
        .filter(String::isNotEmpty)
