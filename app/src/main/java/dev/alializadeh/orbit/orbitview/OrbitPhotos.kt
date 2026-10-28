package dev.alializadeh.orbit.orbitview

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File
import kotlin.math.max

/**
 * The Orbit view's photos as round bitmaps of [sizePx], loaded through Coil once each and drawn
 * straight onto the canvas (no composable per Person, so a few hundred stay smooth). A photo shows
 * once it's loaded; until then the Person is drawn as their initials or colour.
 */
@Composable
internal fun rememberOrbitPhotos(files: Set<File>, sizePx: Int): Map<File, ImageBitmap> {
    val context = LocalContext.current
    val photos: SnapshotStateMap<File, ImageBitmap> = remember(sizePx) { mutableStateMapOf() }
    LaunchedEffect(files, sizePx) {
        val loader = SingletonImageLoader.get(context)
        photos.keys.retainAll(files)
        for (file in files) {
            if (file in photos) continue
            val request = ImageRequest.Builder(context).data(file).size(sizePx).allowHardware(false).build()
            val bitmap = (loader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: continue
            photos[file] = round(bitmap, sizePx).asImageBitmap()
        }
    }
    return photos
}

/** [source] centre-cropped into a circle of [size] px. */
private fun round(source: Bitmap, size: Int): Bitmap {
    val output = createBitmap(size, size)
    val scale = size.toFloat() / minOf(source.width, source.height)
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate((size - source.width * scale) / 2, (size - source.height * scale) / 2)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
    }
    val radius = max(1f, size / 2f)
    Canvas(output).drawCircle(radius, radius, radius, paint)
    return output
}
