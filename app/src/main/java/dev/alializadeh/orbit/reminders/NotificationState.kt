package dev.alializadeh.orbit.reminders

import kotlinx.serialization.Serializable

/**
 * What Orbit has notified on this phone (device-bound, ADR 0006 and the PRD's "notification
 * state"): never in a Backup. Kept in `DeviceState` and rewritten by every `reconcile()`.
 *
 * "Last notified as Due" is not here: it's Person data (`Person.lastNotifiedDueOn`) and travels
 * in a Backup.
 */
@Serializable
data class NotificationState(
    /**
     * Every Reminder posted (or settled), kept until a week after its occurrence, so a Reminder is
     * never posted twice even after its notification was dismissed or cancelled.
     */
    val postedReminders: List<PostedReminder> = emptyList(),
    /** The Reminder each Important date's notification shows now, at most one per date. */
    val showingReminders: List<ShowingReminder> = emptyList(),
    /**
     * The local day (epoch day) the Cadence digest last ran on, whether or not it named anyone:
     * at most one runs a day. Null before the first.
     */
    val lastDigestEpochDay: Long? = null,
    /** The People the digest that's showing names, in its order; empty when none shows. */
    val digestPersonIds: List<Long> = emptyList(),
    /**
     * How the showing digest was rendered; null (before App lock) is the default. Each Reminder
     * keeps its own ([ShowingReminder.content]).
     */
    val renderedAs: NotificationRendering? = null,
    /**
     * The database's restore token (`DataVersion.RESTORE_TOKEN_ROW_ID`) this state was written
     * against. When the live one differs, a Restore has replaced the data: every id above may now
     * name someone else, so `reconcile()` starts again from nothing.
     */
    val restoreToken: Long? = null,
    /** Like [postedReminders], for Occasions (v1.1). */
    val postedOccasionReminders: List<PostedOccasionReminder> = emptyList(),
    /** Like [showingReminders], for Occasions: at most one per Occasion. */
    val showingOccasionReminders: List<ShowingOccasionReminder> = emptyList(),
) {
    /**
     * Only what [showing] says is still on screen: notifications the user dismissed are
     * forgotten (Reminders stay in [postedReminders], so they aren't posted again).
     */
    fun retainShowing(showing: Set<NotificationKey>): NotificationState = copy(
        showingReminders = showingReminders.filter { NotificationKey.DateReminder(it.dateId) in showing },
        digestPersonIds = if (NotificationKey.Digest in showing) digestPersonIds else emptyList(),
        showingOccasionReminders = showingOccasionReminders.filter { NotificationKey.OccasionReminder(it.occasionId) in showing },
    )

    /** The notifications this state says are showing. */
    fun showingKeys(): Set<NotificationKey> = buildSet {
        showingReminders.forEach { add(NotificationKey.DateReminder(it.dateId)) }
        if (digestPersonIds.isNotEmpty()) add(NotificationKey.Digest)
        showingOccasionReminders.forEach { add(NotificationKey.OccasionReminder(it.occasionId)) }
    }
}

/** One Occasion Reminder: an Occasion's occurrence and one of its lead times. */
@Serializable
data class PostedOccasionReminder(
    val occasionId: Long,
    val occurrenceEpochDay: Long,
    val leadTime: LeadTime,
)

/** An Occasion Reminder on screen, like [ShowingReminder]. */
@Serializable
data class ShowingOccasionReminder(
    val occasionId: Long,
    val occurrenceEpochDay: Long,
    val fingerprint: String,
    val content: String = "",
)

/** One Reminder: an Important date's occurrence and one of its lead times. */
@Serializable
data class PostedReminder(
    val dateId: Long,
    val occurrenceEpochDay: Long,
    val leadTime: LeadTime,
)

/**
 * A Reminder notification on screen: which date and occurrence it's for, the date as it was then
 * ([fingerprint]), so an edit to the date cancels it, and what it shows ([content]), so a change
 * to that re-posts it quietly.
 */
@Serializable
data class ShowingReminder(
    val dateId: Long,
    val personId: Long,
    val occurrenceEpochDay: Long,
    val fingerprint: String,
    /** `ReminderPlanner.content` of the Reminder as posted; empty (state from before it) re-posts once. */
    val content: String = "",
)

/** Where one of Orbit's notifications sits: its tag (with the one notification id, [ID]). */
sealed interface NotificationKey {
    val tag: String

    /** Each Important date has one slot: a later Reminder for it replaces the earlier one. */
    data class DateReminder(val dateId: Long) : NotificationKey {
        override val tag: String get() = DATE_PREFIX + dateId
    }

    /** The Cadence digest: one at a time. */
    data object Digest : NotificationKey {
        override val tag: String = "orbit.digest"
    }

    /** Each Occasion has one slot, like an Important date. */
    data class OccasionReminder(val occasionId: Long) : NotificationKey {
        override val tag: String get() = OCCASION_PREFIX + occasionId
    }

    companion object {
        /** Orbit tells its notifications apart by tag; the id is always this. */
        const val ID = 1

        private const val DATE_PREFIX = "orbit.date."
        private const val OCCASION_PREFIX = "orbit.occasion."

        fun fromTag(tag: String?): NotificationKey? = when {
            tag == null -> null
            tag == Digest.tag -> Digest
            tag.startsWith(DATE_PREFIX) -> tag.removePrefix(DATE_PREFIX).toLongOrNull()?.let(::DateReminder)
            tag.startsWith(OCCASION_PREFIX) -> tag.removePrefix(OCCASION_PREFIX).toLongOrNull()?.let(::OccasionReminder)
            else -> null
        }
    }
}

/** Orbit's notification channels (ticket 07, v1.1). Muting one doesn't change Upcoming. */
enum class ReminderChannel(val id: String) {
    /** Reminders. */
    ImportantDates("important_dates"),

    /** The Cadence digest. */
    KeepInTouch("keep_in_touch"),

    /** Occasion Reminders (v1.1). */
    Occasions("occasions"),
}
