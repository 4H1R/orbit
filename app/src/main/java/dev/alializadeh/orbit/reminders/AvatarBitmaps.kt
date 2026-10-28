package dev.alializadeh.orbit.reminders

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import dev.alializadeh.orbit.person.ui.AvatarColours
import dev.alializadeh.orbit.ui.theme.OrbitFonts
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** A Person's photo, or their initials avatar, as a round bitmap for a notification's large icon. */
class AvatarBitmaps(private val context: Context) {

    private val sizePx: Int = (LARGE_ICON_DP * context.resources.displayMetrics.density).roundToInt()

    fun of(name: String, photo: File?): Bitmap =
        photo?.takeIf(File::exists)?.let(::photo) ?: initials(name)

    private fun photo(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
        val source = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // Centre-crop into a circle.
        val scale = sizePx.toFloat() / minOf(source.width, source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((sizePx - source.width * scale) / 2f, (sizePx - source.height * scale) / 2f)
        }
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        return circle { canvas, radius -> canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }) }
    }

    private fun initials(name: String): Bitmap {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val colours = AvatarColours.of(name, dark = night)
        return circle { canvas, radius ->
            canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colours.container.toArgb() })
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colours.content.toArgb()
                textSize = radius
                textAlign = Paint.Align.CENTER
                typeface = OrbitFonts.get(context).mediumTypeface
            }
            val initial = AvatarColours.initial(name)
            canvas.drawText(initial, radius, radius - (text.descent() + text.ascent()) / 2f, text)
        }
    }

    private inline fun circle(draw: (Canvas, Float) -> Unit): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        draw(Canvas(bitmap), max(1, sizePx) / 2f)
        return bitmap
    }

    private companion object {
        /** Android's large-icon size. */
        const val LARGE_ICON_DP = 64
    }
}
