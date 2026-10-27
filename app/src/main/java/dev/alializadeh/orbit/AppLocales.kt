package dev.alializadeh.orbit

import androidx.appcompat.app.AppCompatDelegate

/**
 * The app's own language choice. AppCompat per-app locales store it (ADR 0006), so a Backup
 * manifest records it and a Restore applies it.
 */
fun interface AppLocales {
    /** BCP 47 language tags of the per-app locale, or null when Orbit follows the system. */
    fun languageTags(): String?
}

internal object AppCompatAppLocales : AppLocales {
    override fun languageTags(): String? =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { null }
}
