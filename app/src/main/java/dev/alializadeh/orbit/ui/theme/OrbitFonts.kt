package dev.alializadeh.orbit.ui.theme

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.FontWeight
import dev.alializadeh.orbit.R
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import androidx.compose.ui.text.font.Typeface as composeTypeface

/**
 * Outfit (Latin) with Vazirmatn Non-Latin (Persian) as a per-script fallback, one typeface per
 * weight in use.
 *
 * Each weight is a [Typeface.CustomFallbackBuilder] typeface wrapped as its own
 * `FontFamily(typeface)`. Compose ignores `fontWeight` for such a family, so a weight is chosen by
 * picking a typography style, never by setting `fontWeight` on a `Text`.
 */
internal class OrbitFonts private constructor(resources: Resources) {

    val regular: ComposeFontFamily =
        family(resources, R.font.outfit_regular, R.font.vazirmatn_nl_regular, FontWeight.Normal)
    val medium: ComposeFontFamily =
        family(resources, R.font.outfit_medium, R.font.vazirmatn_nl_medium, FontWeight.Medium)
    val semiBold: ComposeFontFamily =
        family(resources, R.font.outfit_semibold, R.font.vazirmatn_nl_semibold, FontWeight.SemiBold)

    companion object {
        @Volatile private var instance: OrbitFonts? = null

        /** The font files never change, so they're loaded once per process. */
        fun get(context: Context): OrbitFonts =
            instance ?: synchronized(this) {
                instance ?: OrbitFonts(context.applicationContext.resources).also { instance = it }
            }

        private fun family(
            resources: Resources,
            @FontRes latin: Int,
            @FontRes persian: Int,
            weight: FontWeight,
        ): ComposeFontFamily {
            val typeface = Typeface.CustomFallbackBuilder(singleFontFamily(resources, latin))
                .addCustomFallback(singleFontFamily(resources, persian))
                .setStyle(FontStyle(weight.weight, FontStyle.FONT_SLANT_UPRIGHT))
                .setSystemFallback("sans-serif")
                .build()
            return ComposeFontFamily(composeTypeface(typeface))
        }

        private fun singleFontFamily(resources: Resources, @FontRes font: Int): FontFamily =
            FontFamily.Builder(Font.Builder(resources, font).build()).build()
    }
}
