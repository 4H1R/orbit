package dev.alializadeh.orbit.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.format.OrbitFormats
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.photo.PhotoStore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** [ReminderNotifier] on Android's NotificationManager, with Orbit's two channels. */
@Singleton
class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
    private val photoStore: PhotoStore,
) : ReminderNotifier {

    private val manager = NotificationManagerCompat.from(context)

    override fun showing(): Set<NotificationKey>? = try {
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.id == NotificationKey.ID }
            .mapNotNull { NotificationKey.fromTag(it.tag) }
            .toSet()
    } catch (_: RuntimeException) {
        null
    }

    override fun channelsOn(): Set<ReminderChannel> {
        ensureChannels(context)
        if (!manager.areNotificationsEnabled()) return emptySet()
        return ReminderChannel.entries.filter { channel ->
            manager.getNotificationChannelCompat(channel.id)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
        }.toSet()
    }

    @SuppressLint("MissingPermission") // Checked just before.
    override suspend fun post(notification: PlannedNotification, rendering: NotificationRendering, now: Instant) {
        if (!canPost(context)) return
        val localized = localizedContext(context)
        val backedUp = preferences.preferences.first()
        val formats = OrbitFormats(
            language = OrbitLanguage.of(localized.resources.configuration.locales[0]),
            useLatinDigits = backedUp.useLatinDigits,
            primaryCalendar = backedUp.primaryCalendar,
        )
        val built = NotificationFactory(localized, formats, photoStore).build(notification, rendering, now)
        try {
            manager.notify(notification.key.tag, NotificationKey.ID, built)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post: Reminder health shows it.
        }
    }

    override fun cancel(key: NotificationKey) {
        manager.cancel(key.tag, NotificationKey.ID)
    }

    companion object {
        /**
         * Creates the channels, or renames them after a language change ("creating an existing
         * channel" only updates its name and description; the user owns everything else).
         */
        fun ensureChannels(context: Context) {
            val resources = localizedContext(context).resources
            NotificationManagerCompat.from(context).createNotificationChannelsCompat(
                listOf(
                    NotificationChannelCompat.Builder(ReminderChannel.ImportantDates.id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                        .setName(resources.getString(R.string.channel_important_dates_name))
                        .setDescription(resources.getString(R.string.channel_important_dates_description))
                        .build(),
                    NotificationChannelCompat.Builder(ReminderChannel.KeepInTouch.id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                        .setName(resources.getString(R.string.channel_keep_in_touch_name))
                        .setDescription(resources.getString(R.string.channel_keep_in_touch_description))
                        .build(),
                ),
            )
        }

        /**
         * Orbit's chosen language as BCP 47 tags, empty when it follows the phone: AppCompat's
         * in-memory choice when an activity has set it in this process, else the stored one
         * (`LocaleManagerCompat` reads the framework's on 13+ and AppCompat's file before).
         */
        fun appLanguageTags(context: Context): String =
            AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty {
                LocaleManagerCompat.getApplicationLocales(context).toLanguageTags()
            }

        /** Whether a notification would show at all (permission granted and not turned off). */
        fun canPost(context: Context): Boolean {
            // Before Android 13 there's no runtime permission: notifications are on unless turned off.
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /**
         * [context] in Orbit's own UI language. On Android 13+ the system already applies the
         * per-app language to the whole process. On 12/12L it doesn't, and AppCompat only knows the
         * language once an activity has run, so a notification built by the alarm (or after a
         * reboot) would use the phone's language: there the choice AppCompat stored when it was
         * made (`autoStoreLocales`) is read instead.
         */
        fun localizedContext(context: Context): Context {
            val tags = appLanguageTags(context)
            if (tags.isEmpty()) return context
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tags))
            }
            return context.createConfigurationContext(configuration)
        }
    }
}
