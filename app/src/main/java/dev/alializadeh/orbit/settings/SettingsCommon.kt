package dev.alializadeh.orbit.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.ui.components.OrbitListGroupScope
import dev.alializadeh.orbit.ui.components.OrbitSnackbarHost
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode

/**
 * A settings page: a top bar and a scrolling column of grouped lists on `surfaceContainer`, so the
 * `surface` segmented rows stand out (the Expressive grouped-list look).
 *
 * The page family's rules: a page starts with its first group (or a [SettingsIntro] when the page
 * needs one sentence of context); groups are separated by an [OrbitSectionTitle][dev.alializadeh.orbit.ui.components.OrbitSectionTitle]
 * or a [SettingsGroupGap]; what a group means goes under it as a [SettingsFooter]; adding is an
 * [AddRow] at the end of its group; a destructive action is a [DestructiveRow], last on the page.
 * A page with nothing to show passes [emptyState] (an `OrbitEmptyState`), drawn full-size instead
 * of [content].
 */
@Composable
fun OrbitSettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    emptyState: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = { OrbitTopAppBar(title = title, onBack = onBack, containerColor = background) },
        snackbarHost = { OrbitSnackbarHost() },
    ) { innerPadding ->
        if (emptyState != null) {
            Box(Modifier.fillMaxSize().padding(innerPadding)) { emptyState() }
        } else {
            OrbitSettingsColumn(innerPadding, content)
        }
    }
}

/** One sentence of context at the top of a settings page, before its first group. */
@Composable
fun SettingsIntro(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    )
}

/** What the group above means: supporting text right under it. */
@Composable
fun SettingsFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** The space between two groups when the second has no section title. */
@Composable
fun SettingsGroupGap() {
    Spacer(Modifier.height(16.dp))
}

/** The last row of a group that adds one more of what the group lists ("Add ring"). */
@Composable
fun OrbitListGroupScope.AddRow(
    title: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.38f)) },
        supportingContent = summary?.let { { Text(it) } },
    ) {
        Text(title, color = if (enabled) colors.primary else Color.Unspecified)
    }
}

/**
 * A destructive action on a settings page ("Clear crash log"): error-coloured, in a group of its
 * own at the end of the page.
 */
@Composable
fun OrbitListGroupScope.DestructiveRow(
    title: String,
    icon: ImageVector,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = MaterialTheme.colorScheme.error
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        leadingContent = { Icon(icon, contentDescription = null, tint = error) },
    ) {
        Text(title, color = error)
    }
}

/** Content is capped at about 640dp and centred on wide windows. */
val MaxContentWidth = 640.dp

/** A scrolling settings page under its top bar, capped at [MaxContentWidth] and centred. */
@Composable
fun OrbitSettingsColumn(innerPadding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            content = content,
        )
    }
}

@Composable
fun themeName(theme: ThemeMode): String = stringResource(
    when (theme) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    },
)

@Composable
fun colourSourceName(source: ColourSource): String = stringResource(
    when (source) {
        ColourSource.OrbitViolet -> R.string.colour_orbit_violet
        ColourSource.Wallpaper -> R.string.colour_wallpaper
        ColourSource.Custom -> R.string.colour_custom
    },
)

/** Each language in its own script, whatever the UI language: "English", «فارسی». */
@Composable
fun languageName(language: OrbitLanguage): String = stringResource(
    when (language) {
        OrbitLanguage.English -> R.string.language_name_english
        OrbitLanguage.Persian -> R.string.language_name_persian
    },
)
