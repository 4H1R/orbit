package dev.alializadeh.orbit.reminders

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import dev.alializadeh.orbit.R
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On Android 12/12L the system doesn't apply the per-app language, and AppCompat only knows it once
 * an activity has run. A notification built in a fresh process (the alarm, a reboot) must still use
 * Orbit's language, read from what AppCompat stored when the user chose it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "en")
class NotificationLanguageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** AppCompat's own record of the per-app language (`autoStoreLocales`, API ≤ 32). */
    private val stored = File(context.filesDir, "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file")

    @Before
    fun noActivityHasSetALanguage() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        stored.delete()
    }

    @After
    fun forgetTheStoredLanguage() {
        stored.delete()
    }

    @Test
    fun aFreshProcessUsesTheLanguageAppCompatStored() {
        stored.writeText("""<?xml version='1.0' encoding='utf-8' standalone='yes' ?><locales application_locales="fa" />""")

        val localized = AndroidReminderNotifier.localizedContext(context)

        assertEquals("fa", AndroidReminderNotifier.appLanguageTags(context))
        assertEquals("تاریخ‌های مهم", localized.getString(R.string.channel_important_dates_name))
        // The phone itself stays in English.
        assertEquals("Important dates", context.getString(R.string.channel_important_dates_name))
    }

    @Test
    fun withNoLanguageChosenNotificationsFollowThePhone() {
        val localized = AndroidReminderNotifier.localizedContext(context)

        assertEquals("", AndroidReminderNotifier.appLanguageTags(context))
        assertEquals("Important dates", localized.getString(R.string.channel_important_dates_name))
    }

    @Test
    fun aLanguageSetInThisProcessWinsOverTheStoredOne() {
        stored.writeText("""<?xml version='1.0' encoding='utf-8' standalone='yes' ?><locales application_locales="en" />""")
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))

        assertEquals("تاریخ‌های مهم", AndroidReminderNotifier.localizedContext(context).getString(R.string.channel_important_dates_name))
    }
}
