package dev.alializadeh.orbit.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import dev.alializadeh.orbit.person.contact.ContactAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where tapping a notification takes the user, carried in the activity intent's data. Only an
 * intent to the non-exported [NotificationIntents.OPEN_FROM_NOTIFICATION] alias counts: other apps
 * can start the exported `MainActivity`, but never with an entry.
 */
sealed interface NotificationEntry {
    val uri: Uri

    /** Person detail, over Upcoming. */
    data class PersonDetail(val personId: Long) : NotificationEntry {
        override val uri: Uri get() = "$SCHEME://$PERSON/$personId".toUri()
    }

    /** Upcoming at its top (the widget's header). */
    data object Upcoming : NotificationEntry {
        override val uri: Uri get() = "$SCHEME://$UPCOMING/$TOP".toUri()
    }

    /** Upcoming, scrolled to Due (the digest naming several People). */
    data object UpcomingDue : NotificationEntry {
        override val uri: Uri get() = "$SCHEME://$UPCOMING/$DUE".toUri()
    }

    /** Person detail with the Wishes sheet open for their Important date [dateId] (an on-the-day Reminder's Send wishes). */
    data class Wishes(val personId: Long, val dateId: Long) : NotificationEntry {
        override val uri: Uri get() = "$SCHEME://$WISHES/$personId/$dateId".toUri()
    }

    /** An Occasion with its People, over Upcoming (an Occasion Reminder). */
    data class Occasion(val occasionId: Long) : NotificationEntry {
        override val uri: Uri get() = "$SCHEME://$OCCASION/$occasionId".toUri()
    }

    companion object {
        private const val SCHEME = "orbit"
        private const val OCCASION = "occasion"
        private const val PERSON = "person"
        private const val UPCOMING = "upcoming"
        private const val DUE = "due"
        private const val TOP = "top"
        private const val WISHES = "wishes"

        /** The entry [intent] asks for, or null when it's a plain launch or not Orbit's own. */
        fun of(intent: Intent?): NotificationEntry? {
            if (intent?.component?.className != NotificationIntents.OPEN_FROM_NOTIFICATION) return null
            val data = intent.data ?: return null
            if (data.scheme != SCHEME) return null
            return when (data.host) {
                PERSON -> data.lastPathSegment?.toLongOrNull()?.let(::PersonDetail)
                UPCOMING -> when (data.lastPathSegment) {
                    DUE -> UpcomingDue
                    TOP -> Upcoming
                    else -> null
                }
                OCCASION -> data.lastPathSegment?.toLongOrNull()?.let(::Occasion)
                WISHES -> {
                    val segments = data.pathSegments
                    val personId = segments.getOrNull(0)?.toLongOrNull()
                    val dateId = segments.getOrNull(1)?.toLongOrNull()
                    if (segments.size == 2 && personId != null && dateId != null) Wishes(personId, dateId) else null
                }
                else -> null
            }
        }
    }
}

/**
 * An entry waiting for the app shell to take it. `MainActivity` offers one from a notification
 * tap; the shell consumes it once it's drawn. Slice 7: the shell isn't drawn while App lock's
 * gate is up, so the entry waits behind the gate and continues once it's passed.
 */
@Singleton
class NotificationEntries @Inject constructor() {
    private val pendingEntry = MutableStateFlow<NotificationEntry?>(null)

    val pending: StateFlow<NotificationEntry?> = pendingEntry.asStateFlow()

    fun offer(entry: NotificationEntry) {
        pendingEntry.value = entry
    }

    fun consume(entry: NotificationEntry) {
        pendingEntry.compareAndSet(entry, null)
    }
}

/** What a notification's background action does. */
enum class NotificationActionType(val action: String) {
    MarkAsContacted("dev.alializadeh.orbit.action.MARK_AS_CONTACTED"),
    SnoozeWeek("dev.alializadeh.orbit.action.SNOOZE_WEEK"),
    ;

    companion object {
        fun of(action: String?): NotificationActionType? = entries.firstOrNull { it.action == action }
    }
}

/**
 * A background action on one notification ([key]) for one Person, posted for the data whose restore
 * token is [restoreToken] (`DataVersion.RESTORE_TOKEN_ROW_ID`; null before any Restore). After a
 * Restore the same [personId] may be someone else, so an action whose token isn't the live one
 * does nothing.
 */
data class NotificationActionTarget(
    val type: NotificationActionType,
    val key: NotificationKey,
    val personId: Long,
    val restoreToken: Long? = null,
) {

    /** The intent's data tells actions apart, so each notification's PendingIntent is its own. */
    val uri: Uri get() = Uri.Builder().scheme("orbit").authority("action")
        .appendPath(key.tag)
        .appendPath(personId.toString())
        .appendPath(restoreToken?.toString() ?: NO_TOKEN)
        .build()

    companion object {
        private const val NO_TOKEN = "none"

        fun of(intent: Intent?): NotificationActionTarget? {
            val type = NotificationActionType.of(intent?.action) ?: return null
            val segments = intent?.data?.pathSegments ?: return null
            // Two segments: posted before restore tokens, so before any Restore since.
            if (segments.size != 2 && segments.size != 3) return null
            val key = NotificationKey.fromTag(segments[0]) ?: return null
            val personId = segments[1].toLongOrNull() ?: return null
            val token = segments.getOrNull(2)?.takeIf { it != NO_TOKEN }?.let { it.toLongOrNull() ?: return null }
            return NotificationActionTarget(type, key, personId, token)
        }
    }
}

/**
 * The PendingIntents notifications carry. All immutable. Opening Orbit or a contact app is an
 * activity PendingIntent (Android 12 forbids starting activities from a receiver); Mark as
 * contacted and Snooze are broadcasts to the non-exported [NotificationActionReceiver].
 */
class NotificationIntents(private val context: Context) {

    fun open(entry: NotificationEntry): PendingIntent = PendingIntent.getActivity(context, 0, openIntent(entry), FLAGS)

    /** `MainActivity` through its non-exported alias, which only Orbit itself can start. */
    fun openIntent(entry: NotificationEntry): Intent = Intent()
        .setClassName(context, OPEN_FROM_NOTIFICATION)
        .setAction(Intent.ACTION_VIEW)
        .setData(entry.uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The dialer or messenger, straight from the notification; logs nothing. */
    fun contact(action: ContactAction): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(action.intentAction, action.uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        FLAGS,
    )

    fun background(target: NotificationActionTarget): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, NotificationActionReceiver::class.java).setAction(target.type.action).setData(target.uri),
        FLAGS,
    )

    companion object {
        /** The manifest's non-exported `activity-alias` for `MainActivity`. */
        const val OPEN_FROM_NOTIFICATION = "dev.alializadeh.orbit.OpenFromNotification"

        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
