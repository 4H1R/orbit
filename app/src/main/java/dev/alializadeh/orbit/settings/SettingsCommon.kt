package dev.alializadeh.orbit.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode

/**
 * A settings page: a top bar and a scrolling column of grouped lists on `surfaceContainer`, so the
 * `surface` segmented rows stand out (the Expressive grouped-list look).
 */
@Composable
fun OrbitSettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = { OrbitTopAppBar(title = title, onBack = onBack, containerColor = background) },
    ) { innerPadding ->
        OrbitSettingsColumn(innerPadding, content)
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
