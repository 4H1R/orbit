package dev.alializadeh.orbit.reminders

import android.app.Notification
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.OrbitFormats
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.ui.theme.OrbitViolet
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import java.time.Duration
import java.time.Instant

/**
 * Builds Orbit's notifications from the planner's [PlannedNotification]s.
 *
 * Every notification is `VISIBILITY_PRIVATE` with the discreet text as its public version, so the
 * phone's lock-screen setting decides what shows there. [NotificationDetail.Discreet] (App lock,
 * slice 7) uses the discreet text everywhere and drops the photo, Gift ideas and actions; actions
 * ask for the device unlock when [NotificationRendering.actionsNeedUnlock].
 *
 * [context] must carry Orbit's UI language; [formats] its digits and names. Background actions
 * carry [restoreToken], the data they were posted for, so one tapped after a Restore does nothing.
 */
class NotificationFactory(
    private val context: Context,
    formats: OrbitFormats,
    private val photoStore: PhotoStore,
    private val restoreToken: Long? = null,
) {
    private val texts = NotificationTexts(context.resources, formats)
    private val intents = NotificationIntents(context)
    private val avatars = AvatarBitmaps(context)
    private val language = formats.language

    fun build(notification: PlannedNotification, rendering: NotificationRendering, now: Instant): Notification =
        when (notification) {
            is PlannedReminder -> reminder(notification, rendering, now)
            is PlannedDigest -> digest(notification, rendering)
        }

    private fun reminder(reminder: PlannedReminder, rendering: NotificationRendering, now: Instant): Notification {
        val discreet = texts.discreetReminder(reminder)
        val builder = base(ReminderChannel.ImportantDates, discreet, silent = false)
            .setContentIntent(intents.open(NotificationEntry.PersonDetail(reminder.person.id)))
            // Expires at the end of the date's day, even if Orbit doesn't run then.
            .setTimeoutAfter(Duration.between(now, reminder.expiresAt).toMillis().coerceAtLeast(1))
        if (rendering.detail == NotificationDetail.Detailed) {
            builder.setText(texts.reminder(reminder))
            builder.setLargeIcon(avatar(reminder.person))
            if (reminder.isOnTheDay) {
                contactAction(reminder.person)?.let(builder::addAction)
                builder.addAction(
                    backgroundAction(
                        NotificationActionTarget(NotificationActionType.MarkAsContacted, reminder.key, reminder.person.id, restoreToken),
                        R.string.action_mark_as_contacted,
                        rendering,
                    ),
                )
            }
        }
        return builder.build()
    }

    private fun digest(digest: PlannedDigest, rendering: NotificationRendering): Notification {
        val discreet = texts.discreetDigest(digest)
        val single = digest.people.singleOrNull()?.person
        val entry = single?.let { NotificationEntry.PersonDetail(it.id) } ?: NotificationEntry.UpcomingDue
        val builder = base(ReminderChannel.KeepInTouch, discreet, silent = digest.silent)
            .setContentIntent(intents.open(entry))
        if (rendering.detail == NotificationDetail.Detailed) {
            builder.setText(texts.digest(digest))
            if (single != null) {
                builder.setLargeIcon(avatar(single))
                contactAction(single)?.let(builder::addAction)
                builder.addAction(
                    backgroundAction(
                        NotificationActionTarget(NotificationActionType.MarkAsContacted, digest.key, single.id, restoreToken),
                        R.string.action_mark_as_contacted,
                        rendering,
                    ),
                )
                builder.addAction(
                    backgroundAction(
                        NotificationActionTarget(NotificationActionType.SnoozeWeek, digest.key, single.id, restoreToken),
                        R.string.action_snooze_week,
                        rendering,
                    ),
                )
            }
        }
        return builder.build()
    }

    /** Everything every notification has, showing [discreet] until detail is added. */
    private fun base(channel: ReminderChannel, discreet: NotificationText, silent: Boolean): NotificationCompat.Builder {
        val public = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(discreet.title)
            .build()
        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(discreet.title)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
    }

    private fun NotificationCompat.Builder.setText(text: NotificationText): NotificationCompat.Builder {
        setContentTitle(text.title)
        text.lines.firstOrNull()?.let(::setContentText)
        if (text.lines.size > 1) setStyle(NotificationCompat.BigTextStyle().bigText(text.lines.joinToString("\n")))
        return this
    }

    private fun avatar(person: UpcomingPerson) =
        avatars.of(person.names.displayName(language), person.photoFileName?.let(photoStore::file))

    /** The primary Contact method's first action ("Call", "WhatsApp"); none without a primary. */
    private fun contactAction(person: UpcomingPerson): NotificationCompat.Action? {
        val action = person.primaryContactMethod?.let { ContactActions.of(it).firstOrNull() } ?: return null
        return NotificationCompat.Action.Builder(0, context.getString(action.kind.labelRes), intents.contact(action)).build()
    }

    private fun backgroundAction(target: NotificationActionTarget, label: Int, rendering: NotificationRendering) =
        NotificationCompat.Action.Builder(0, context.getString(label), intents.background(target))
            .setAuthenticationRequired(rendering.actionsNeedUnlock)
            .build()

    private companion object {
        val ACCENT = OrbitViolet.toArgb()
    }
}
