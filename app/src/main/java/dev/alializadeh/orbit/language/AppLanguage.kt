package dev.alializadeh.orbit.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import javax.inject.Inject

/**
 * The UI language, kept by AppCompat per-app locales (ADR 0006: not a backed-up preference; the
 * Backup manifest records it instead). Setting it recreates the activity, which flips the layout
 * direction and reloads every string.
 */
interface AppLanguage {
    /** The per-app language if one is set, otherwise the language the system locale implies. */
    fun current(): OrbitLanguage

    fun set(language: OrbitLanguage)
}

class AppCompatAppLanguage @Inject constructor() : AppLanguage {

    override fun current(): OrbitLanguage {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locales = if (appLocales.isEmpty) LocaleListCompat.getAdjustedDefault() else appLocales
        return OrbitLanguage.of(locales[0])
    }

    override fun set(language: OrbitLanguage) {
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == language.tag) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}
