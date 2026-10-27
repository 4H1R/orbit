package dev.alializadeh.orbit.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.LocalOrbitFormats
import dev.alializadeh.orbit.ui.components.ChoiceRow
import dev.alializadeh.orbit.ui.components.ContentRow
import dev.alializadeh.orbit.ui.components.OrbitConnectedChoice
import dev.alializadeh.orbit.ui.components.OrbitListGroup
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ColourSwatches
import dev.alializadeh.orbit.ui.theme.OrbitViolet
import dev.alializadeh.orbit.ui.theme.ThemeMode
import kotlinx.serialization.Serializable

@Serializable
data object AppearanceSettingsKey : NavKey

@Composable
fun AppearanceRoute(onBack: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceScreen(
        uiState = uiState,
        onBack = onBack,
        onThemeSelect = viewModel::selectTheme,
        onColourSourceSelect = viewModel::selectColourSource,
        onCustomColourSelect = viewModel::selectCustomColour,
    )
}

/**
 * Settings → Appearance: Theme (System/Light/Dark) and Colour (Orbit violet, Match wallpaper, or
 * Your colour: nine swatches plus a free picker). Every change recolours the app at once.
 */
@Composable
fun AppearanceScreen(
    uiState: AppearanceUiState,
    onBack: () -> Unit,
    onThemeSelect: (ThemeMode) -> Unit,
    onColourSourceSelect: (ColourSource) -> Unit,
    onCustomColourSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingColour by rememberSaveable { mutableStateOf(false) }
    OrbitSettingsScaffold(
        title = stringResource(R.string.settings_appearance_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (uiState !is AppearanceUiState.Loaded) return@OrbitSettingsScaffold
        OrbitListGroup(title = stringResource(R.string.appearance_theme)) {
            ContentRow(index = 0, count = 1) {
                OrbitConnectedChoice(
                    options = ThemeMode.entries,
                    selected = uiState.theme,
                    onSelect = onThemeSelect,
                    label = { themeName(it) },
                )
            }
        }
        OrbitListGroup(title = stringResource(R.string.appearance_colour)) {
            val count = 4
            ChoiceRow(
                title = stringResource(R.string.colour_orbit_violet),
                summary = stringResource(R.string.colour_orbit_violet_summary),
                selected = uiState.colourSource == ColourSource.OrbitViolet,
                index = 0,
                count = count,
                onClick = { onColourSourceSelect(ColourSource.OrbitViolet) },
                leadingContent = { ColourDot(OrbitViolet) },
            )
            ChoiceRow(
                title = stringResource(R.string.colour_wallpaper),
                summary = stringResource(R.string.colour_wallpaper_summary),
                selected = uiState.colourSource == ColourSource.Wallpaper,
                index = 1,
                count = count,
                onClick = { onColourSourceSelect(ColourSource.Wallpaper) },
                leadingContent = { ColourDot(dynamicLightColorScheme(LocalContext.current).primary) },
            )
            ChoiceRow(
                title = stringResource(R.string.colour_custom),
                summary = stringResource(R.string.colour_custom_summary),
                selected = uiState.colourSource == ColourSource.Custom,
                index = 2,
                count = count,
                onClick = { onColourSourceSelect(ColourSource.Custom) },
                leadingContent = { ColourDot(uiState.customColour ?: ColourSwatches[6]) },
            )
            ContentRow(index = 3, count = count) {
                Swatches(
                    selected = uiState.customColour.takeIf { uiState.colourSource == ColourSource.Custom },
                    onSwatchSelect = onCustomColourSelect,
                    onPickColour = { pickingColour = true },
                )
            }
        }
        if (pickingColour) {
            ColourPickerDialog(
                initial = uiState.customColour ?: OrbitViolet,
                onDismiss = { pickingColour = false },
                onConfirm = { colour ->
                    pickingColour = false
                    onCustomColourSelect(colour)
                },
            )
        }
    }
}

@Composable
private fun ColourDot(colour: Color) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(colour))
}

/** The nine swatches, then the free-pick slot (showing the picked colour when it isn't a swatch). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(selected: Color?, onSwatchSelect: (Color) -> Unit, onPickColour: () -> Unit) {
    val numbers = LocalOrbitFormats.current.numbers
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ColourSwatches.forEachIndexed { index, swatch ->
            Swatch(
                fill = Brush.linearGradient(listOf(swatch, swatch)),
                checkColour = swatch.contentColour(),
                selected = selected?.toArgb() == swatch.toArgb(),
                description = stringResource(R.string.colour_swatch_description, numbers.format(index + 1)),
                onClick = { onSwatchSelect(swatch) },
            )
        }
        val custom = selected?.takeIf { colour -> ColourSwatches.none { it.toArgb() == colour.toArgb() } }
        Swatch(
            fill = if (custom != null) Brush.linearGradient(listOf(custom, custom)) else RainbowBrush,
            checkColour = custom?.contentColour() ?: Color.White,
            selected = custom != null,
            description = stringResource(R.string.colour_pick),
            onClick = onPickColour,
        )
    }
}

@Composable
private fun Swatch(
    fill: Brush,
    checkColour: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = checkColour)
    }
}

private val RainbowBrush = Brush.sweepGradient(
    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
)

private fun Color.contentColour(): Color = if (luminance() > 0.5f) Color.Black else Color.White

/** A free colour pick: hue, saturation and brightness sliders over a preview. */
@Composable
private fun ColourPickerDialog(initial: Color, onDismiss: () -> Unit, onConfirm: (Color) -> Unit) {
    val hsv = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by rememberSaveable { mutableFloatStateOf(hsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(hsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(hsv[2]) }
    val colour = Color.hsv(hue, saturation, brightness)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.colour_pick)) },
        text = {
            Column {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colour),
                )
                Spacer(Modifier.height(16.dp))
                LabelledSlider(R.string.colour_picker_hue, hue, 0f..360f) { hue = it }
                LabelledSlider(R.string.colour_picker_saturation, saturation, 0f..1f) { saturation = it }
                LabelledSlider(R.string.colour_picker_brightness, brightness, 0f..1f) { brightness = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(colour) }) { Text(stringResource(R.string.colour_picker_use)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun LabelledSlider(
    label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    val text = stringResource(label)
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = text },
        )
    }
}
