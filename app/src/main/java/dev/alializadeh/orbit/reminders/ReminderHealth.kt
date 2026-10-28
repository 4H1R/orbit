package dev.alializadeh.orbit.reminders

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Where reminders stand on this phone (ticket 02's Reminder health checklist). Device-bound and
 * read live each time, never stored.
 */
data class ReminderHealth(
    /** Notifications allowed for the app (the permission on 13+, the app switch everywhere). */
    val notificationsAllowed: Boolean,
    /** Channels the user turned off (only meaningful while [notificationsAllowed]). */
    val channelsOff: Set<ReminderChannel> = emptySet(),
    /** Exact alarms allowed; null where it can't be taken away (Android 13+ has `USE_EXACT_ALARM`). */
    val exactAlarms: Boolean? = null,
    /** Orbit is exempt from battery optimisation. */
    val batteryUnrestricted: Boolean = true,
    /** "Pause app activity if unused" is on; null where the phone has no such setting. */
    val pausedIfUnused: Boolean? = null,
    /** `Build.MANUFACTURER`, for the phone maker's steps. */
    val phoneMaker: String = "",
    /** Notifications need the runtime permission (Android 13+). */
    val notificationsNeedPermission: Boolean = true,
) {
    val notificationsOk: Boolean get() = notificationsAllowed && channelsOff.isEmpty()

    /** How many checklist items need the user (the phone maker's steps are advice, not counted). */
    val problems: Int
        get() = listOf(!notificationsOk, exactAlarms == false, !batteryUnrestricted, pausedIfUnused == true).count { it }

    /**
     * The problem worth a card on Upcoming, if any (ticket 07: notifications off, or exact alarms
     * unavailable on 12/12L). Before Orbit has ever asked for notifications ([askedForNotifications]),
     * notifications being off on 13+ is just the fresh-install default, not a problem yet.
     */
    fun critical(askedForNotifications: Boolean): CriticalReminderProblem? = when {
        !notificationsAllowed && (askedForNotifications || !notificationsNeedPermission) -> CriticalReminderProblem.NotificationsOff
        exactAlarms == false -> CriticalReminderProblem.ExactAlarmsOff
        else -> null
    }
}

enum class CriticalReminderProblem { NotificationsOff, ExactAlarmsOff }

/** Reads [ReminderHealth] from the system. */
interface ReminderHealthChecker {
    suspend fun check(): ReminderHealth
}

class AndroidReminderHealthChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderHealthChecker {

    override suspend fun check(): ReminderHealth {
        AndroidReminderNotifier.ensureChannels(context)
        val notifications = NotificationManagerCompat.from(context)
        val channelsOff = ReminderChannel.entries.filter {
            notifications.getNotificationChannelCompat(it.id)?.importance == NotificationManagerCompat.IMPORTANCE_NONE
        }.toSet()
        val exactAlarms = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            null
        }
        val power = context.getSystemService(PowerManager::class.java)
        return ReminderHealth(
            notificationsAllowed = AndroidReminderNotifier.canPost(context),
            channelsOff = channelsOff,
            exactAlarms = exactAlarms,
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
            pausedIfUnused = pausedIfUnused(),
            phoneMaker = Build.MANUFACTURER.orEmpty(),
            notificationsNeedPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
    }

    private suspend fun pausedIfUnused(): Boolean? {
        val status = try {
            val future = PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
            suspendCancellableCoroutine<Int?> { continuation ->
                future.addListener(
                    { continuation.resume(runCatching { future.get() }.getOrNull()) },
                    ContextCompat.getMainExecutor(context),
                )
            }
        } catch (_: RuntimeException) {
            null
        }
        return when (status) {
            UnusedAppRestrictionsConstants.DISABLED -> false
            UnusedAppRestrictionsConstants.API_30_BACKPORT,
            UnusedAppRestrictionsConstants.API_30,
            UnusedAppRestrictionsConstants.API_31,
            -> true
            else -> null
        }
    }
}

/** dontkillmyapp.com: each phone maker's page of steps that keep apps working in the background. */
object DontKillMyApp {

    private const val SITE = "https://dontkillmyapp.com/"

    /** Makers whose `Build.MANUFACTURER` differs from the site's page name. */
    private val ALIASES = mapOf(
        "redmi" to "xiaomi",
        "poco" to "xiaomi",
        "hmd global" to "nokia",
        "motorola mobility" to "motorola",
    )

    private val PAGES = setOf(
        "samsung", "xiaomi", "huawei", "honor", "oneplus", "oppo", "vivo", "realme", "meizu", "asus",
        "nokia", "sony", "lenovo", "motorola", "google", "wiko", "htc", "blackview", "unihertz", "tecno",
    )

    /** The page for [manufacturer], or the site's front page for makers it doesn't list. */
    fun url(manufacturer: String): String {
        val maker = manufacturer.trim().lowercase(Locale.ROOT)
        val page = ALIASES[maker] ?: maker
        return if (page in PAGES) SITE + page else SITE
    }

    /** "Samsung", "Google": the maker as people write it. */
    fun displayName(manufacturer: String): String =
        manufacturer.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}
