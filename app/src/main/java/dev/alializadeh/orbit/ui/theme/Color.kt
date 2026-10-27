package dev.alializadeh.orbit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The default seed colour. Every colour scheme is generated from a seed by MaterialKolor. */
val OrbitViolet = Color(0xFF5B4BD6)

/** Brand colour reserved for Important dates, used as the scheme's tertiary key colour. */
val Apricot = Color(0xFFFF8A5B)

/** Whether a scheme is dark, whatever chose it (Theme setting, wallpaper, screenshot qualifiers). */
fun androidx.compose.material3.ColorScheme.isDarkScheme(): Boolean = surface.luminance() < 0.5f
