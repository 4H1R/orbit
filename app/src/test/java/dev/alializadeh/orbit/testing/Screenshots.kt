package dev.alializadeh.orbit.testing

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.ui.theme.OrbitAppearance
import dev.alializadeh.orbit.ui.theme.OrbitTheme
import org.robolectric.RuntimeEnvironment

/** The four screenshot variants: en/LTR and fa/RTL, light and dark (theme System). */
enum class ScreenshotVariant(val id: String, val qualifiers: String, val persian: Boolean) {
    EnLight("en_light", "+en-ldltr-notnight", persian = false),
    EnDark("en_dark", "+en-ldltr-night", persian = false),
    FaLight("fa_light", "+fa-ldrtl-notnight", persian = true),
    FaDark("fa_dark", "+fa-ldrtl-night", persian = true),
}

/**
 * Captures [content] in [variant] under Orbit's theme to `src/test/screenshots/<screen>_<variant>.png`.
 * Record with `./gradlew recordRoborazziDebug`; verify with `verifyRoborazziDebug`.
 */
fun captureScreen(
    screen: String,
    variant: ScreenshotVariant,
    appearance: OrbitAppearance = OrbitAppearance(),
    useLatinDigits: Boolean = false,
    /** Null: the language's own (Gregorian in English, Solar Hijri in Persian). */
    primaryCalendar: CalendarSystem? = null,
    /** A taller screen (1600dp), to see a whole scrolling page at once. */
    tall: Boolean = false,
    content: @Composable () -> Unit,
) {
    val qualifiers = if (tall) variant.qualifiers.replace(NIGHT_QUALIFIER, "-h1600dp$1") else variant.qualifiers
    RuntimeEnvironment.setQualifiers(qualifiers)
    captureRoboImage("src/test/screenshots/${screen}_${variant.id}.png") {
        OrbitTheme(appearance = appearance, useLatinDigits = useLatinDigits, primaryCalendar = primaryCalendar) { content() }
    }
}

/** The night-mode qualifier at the end; screen height has to come before it. */
private val NIGHT_QUALIFIER = Regex("(-(?:not)?night)$")
