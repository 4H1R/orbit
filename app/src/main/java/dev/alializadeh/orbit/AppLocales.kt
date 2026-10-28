package dev.alializadeh.orbit

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The app's own language choice. AppCompat per-app locales store it (ADR 0006), so a Backup
 * manifest records it and a Restore applies it.
 */
interface AppLocales {
    /** BCP 47 language tags of the per-app locale, or null when Orbit follows the system. */
    fun languageTags(): String?

    /**
     * Sets the per-app locale ([tags] null: follow the system), from any thread. Changing it
     * recreates the activity.
     */
    fun apply(tags: String?)
}

internal object AppCompatAppLocales : AppLocales {
    override fun languageTags(): String? =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { null }

    override fun apply(tags: String?) {
        // AppCompat recreates the activities, so it must run on the main thread.
        Handler(Looper.getMainLooper()).post {
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != tags.orEmpty()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags.orEmpty()))
            }
        }
    }
}
