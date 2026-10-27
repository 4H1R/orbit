package dev.alializadeh.orbit.ui.theme

import androidx.compose.ui.graphics.Color

/** Settings → Appearance → Theme. Stored by name: never rename a constant. */
enum class ThemeMode {
    System,
    Light,
    Dark,
}

/** Settings → Appearance → Colour. Stored by name: never rename a constant. */
enum class ColourSource {
    /** The default: Orbit violet through MaterialKolor. */
    OrbitViolet,

    /** Android dynamic colour from the wallpaper. */
    Wallpaper,

    /** "Your colour": a swatch or a free pick, through MaterialKolor like Orbit violet. */
    Custom,
}

/** The nine "Your colour" swatches, in the order they're shown (from the visual direction). */
val ColourSwatches: List<Color> = listOf(
    Color(0xFFD6409F),
    Color(0xFFE5484D),
    Color(0xFFF76B15),
    Color(0xFFC99A00),
    Color(0xFF30A46C),
    Color(0xFF12A594),
    Color(0xFF0090FF),
    Color(0xFF8E4EC6),
    Color(0xFF7D6B5D),
)
