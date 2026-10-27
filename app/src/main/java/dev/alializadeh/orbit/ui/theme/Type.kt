package dev.alializadeh.orbit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The Material 3 type scale set in Orbit's fonts. Each style keeps its token weight, mapped onto a
 * bundled weight: Regular (400) and Medium (500) as in the tokens, and SemiBold (600) for the
 * emphasised styles, whose Bold token Orbit draws at 600 as in the visual direction.
 */
internal fun orbitTypography(fonts: OrbitFonts): Typography {
    fun TextStyle.inOrbitFont(): TextStyle {
        val weight = fontWeight ?: FontWeight.Normal
        return when {
            weight < FontWeight.Medium -> copy(fontFamily = fonts.regular, fontWeight = FontWeight.Normal)
            weight < FontWeight.SemiBold -> copy(fontFamily = fonts.medium, fontWeight = FontWeight.Medium)
            else -> copy(fontFamily = fonts.semiBold, fontWeight = FontWeight.SemiBold)
        }
    }

    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.inOrbitFont(),
        displayMedium = base.displayMedium.inOrbitFont(),
        displaySmall = base.displaySmall.inOrbitFont(),
        headlineLarge = base.headlineLarge.inOrbitFont(),
        headlineMedium = base.headlineMedium.inOrbitFont(),
        headlineSmall = base.headlineSmall.inOrbitFont(),
        titleLarge = base.titleLarge.inOrbitFont(),
        titleMedium = base.titleMedium.inOrbitFont(),
        titleSmall = base.titleSmall.inOrbitFont(),
        bodyLarge = base.bodyLarge.inOrbitFont(),
        bodyMedium = base.bodyMedium.inOrbitFont(),
        bodySmall = base.bodySmall.inOrbitFont(),
        labelLarge = base.labelLarge.inOrbitFont(),
        labelMedium = base.labelMedium.inOrbitFont(),
        labelSmall = base.labelSmall.inOrbitFont(),
        displayLargeEmphasized = base.displayLargeEmphasized.inOrbitFont(),
        displayMediumEmphasized = base.displayMediumEmphasized.inOrbitFont(),
        displaySmallEmphasized = base.displaySmallEmphasized.inOrbitFont(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.inOrbitFont(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.inOrbitFont(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.inOrbitFont(),
        titleLargeEmphasized = base.titleLargeEmphasized.inOrbitFont(),
        titleMediumEmphasized = base.titleMediumEmphasized.inOrbitFont(),
        titleSmallEmphasized = base.titleSmallEmphasized.inOrbitFont(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.inOrbitFont(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.inOrbitFont(),
        bodySmallEmphasized = base.bodySmallEmphasized.inOrbitFont(),
        labelLargeEmphasized = base.labelLargeEmphasized.inOrbitFont(),
        labelMediumEmphasized = base.labelMediumEmphasized.inOrbitFont(),
        labelSmallEmphasized = base.labelSmallEmphasized.inOrbitFont(),
    )
}
