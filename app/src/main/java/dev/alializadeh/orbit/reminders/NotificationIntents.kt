package dev.alializadeh.orbit.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import dev.alializadeh.orbit.MainActivity
import dev.alializadeh.orbit.person.contact.ContactAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where tapping a notification takes the user, carried in the activity intent's data. */
sealed interface NotificationEntry {
    val uri: Uri

    /** Person detail, over Upcoming. */
    data class PersonDetail(val personId: Long) : NotificationEntry {
        override val uri: Uri get() = "$SCHEME://$PERSON/$personId".toUri()
    }

    /** Upcoming, scrolled to Due (the digest naming several People). */
    data object UpcomingDue : NotificationEntry {
        override val uri: Uri get() = "$SCHEME://$UPCOMING/$DUE".toUri()
    }

    companion object {
        private const val SCHEME = "orbit"
        private const val PERSON = "person"
        private const val UPCOMING = "upcoming"
        private const val DUE = "due"

        /** The entry [intent] asks for, or null when it's a plain launch. */
        fun of(intent: Intent?): NotificationEntry? {
            val data = intent?.data ?: return null
            if (data.scheme != SCHEME) return null
            return when (data.host) {
                PERSON -> data.lastPathSegment?.toLongOrNull()?.let(::PersonDetail)
                UPCOMING -> UpcomingDue.takeIf { data.lastPathSegment == DUE }
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

/** A background action on one notification ([key]) for one Person. */
data class NotificationActionTarget(val type: NotificationActionType, val key: NotificationKey, val personId: Long) {

    /** The intent's data tells actions apart, so each notification's PendingIntent is its own. */
    val uri: Uri get() = Uri.Builder().scheme("orbit").authority("action").appendPath(key.tag).appendPath(personId.toString()).build()

    companion object {
        fun of(intent: Intent?): NotificationActionTarget? {
            val type = NotificationActionType.of(intent?.action) ?: return null
            val segments = intent?.data?.pathSegments ?: return null
            if (segments.size != 2) return null
            val key = NotificationKey.fromTag(segments[0]) ?: return null
            val personId = segments[1].toLongOrNull() ?: return null
            return NotificationActionTarget(type, key, personId)
        }
    }
}

/**
 * The PendingIntents notifications carry. All immutable. Opening Orbit or a contact app is an
 * activity PendingIntent (Android 12 forbids starting activities from a receiver); Mark as
 * contacted and Snooze are broadcasts to the non-exported [NotificationActionReceiver].
 */
class NotificationIntents(private val context: Context) {

    fun open(entry: NotificationEntry): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(entry.uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        FLAGS,
    )

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

    private companion object {
        const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
