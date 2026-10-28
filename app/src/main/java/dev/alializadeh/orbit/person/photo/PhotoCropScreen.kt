package dev.alializadeh.orbit.person.photo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.person.edit.CropUiState
import dev.alializadeh.orbit.ui.expressive.OrbitLoadingIndicator
import dev.alializadeh.orbit.ui.expressive.OrbitTopAppBar
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Orbit's crop screen: the picked photo under a circular mask, moved with a drag and scaled with a
 * pinch (the maths is [CropTransform]). Done saves the square around the circle.
 */
@Composable
fun PhotoCropScreen(
    state: CropUiState,
    onCancel: () -> Unit,
    onDone: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCancel)
    // Saveable, so the pan and zoom are still there when App lock lets the user back in.
    var transform by rememberSaveable(state, stateSaver = CropTransformSaver) { mutableStateOf<CropTransform?>(null) }
    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            OrbitTopAppBar(
                title = stringResource(R.string.photo_crop_title),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { transform?.let { onDone(it.cropRect()) } },
                        enabled = state is CropUiState.Ready && transform != null,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.38f),
                        ),
                    ) {
                        Text(stringResource(R.string.common_done))
                    }
                },
                containerColor = Color.Black,
                contentColor = Color.White,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                CropUiState.Loading, CropUiState.Saving -> OrbitLoadingIndicator()
                CropUiState.Failed -> Text(
                    text = stringResource(R.string.photo_crop_failed),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
                is CropUiState.Ready -> CropArea(
                    image = state.image,
                    transform = transform,
                    onTransform = { transform = it },
                )
            }
        }
    }
}

@Composable
private fun CropArea(
    image: CropImage,
    transform: CropTransform?,
    onTransform: (CropTransform) -> Unit,
) {
    val bitmap = remember(image) { image.imageBitmap() }
    val margin = with(LocalDensity.current) { 24.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val viewport = min(width, height) - 2 * margin
        if (viewport <= 0f) return@BoxWithConstraints
        val current = transform?.takeIf { it.viewport == viewport }
            ?: transform?.withViewport(viewport)
            ?: CropTransform(image.width, image.height, viewport)
        LaunchedEffect(current) { if (current != transform) onTransform(current) }
        var latest by remember { mutableStateOf(current) }
        latest = current
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(image) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val next = latest.transformed(
                            panX = pan.x,
                            panY = pan.y,
                            zoomChange = zoom,
                            centroidX = centroid.x - centre.x,
                            centroidY = centroid.y - centre.y,
                        )
                        latest = next
                        onTransform(next)
                    }
                },
        ) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val shownWidth = image.width * current.scale
            val shownHeight = image.height * current.scale
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(
                    (centre.x + current.offsetX - shownWidth / 2).roundToInt(),
                    (centre.y + current.offsetY - shownHeight / 2).roundToInt(),
                ),
                dstSize = IntSize(shownWidth.roundToInt(), shownHeight.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            val circle = Rect(centre, viewport / 2)
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, Size(size.width, size.height)))
                addOval(circle)
            }
            drawPath(scrim, Color.Black.copy(alpha = 0.6f))
            drawCircle(Color.White, radius = viewport / 2, center = centre, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private val CropTransformSaver = Saver<CropTransform?, FloatArray>(
    save = { transform ->
        transform?.run { floatArrayOf(imageWidth.toFloat(), imageHeight.toFloat(), viewport, zoom, offsetX, offsetY) }
    },
    restore = { CropTransform(it[0].toInt(), it[1].toInt(), it[2], it[3], it[4], it[5]) },
)
