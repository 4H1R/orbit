package dev.alializadeh.orbit.reminders

import dev.alializadeh.orbit.upcoming.UpcomingFollowUp
import dev.alializadeh.orbit.upcoming.UpcomingLoan
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import dev.alializadeh.orbit.upcoming.UpcomingTask
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/** A Reminder that the day of a Follow-up or Loan has come. */
data class PlannedTaskReminder(
    val task: UpcomingTask,
    override val silent: Boolean = false,
) : PlannedNotification {
    override val key: NotificationKey get() = task.notificationKey
}

/** The notification slot of this Follow-up or Loan. */
val UpcomingTask.notificationKey: NotificationKey
    get() = when (this) {
        is UpcomingFollowUp -> NotificationKey.FollowUp(followUp.id)
        is UpcomingLoan -> NotificationKey.Loan(loan.id)
    }

/** What the Follow-up and Loan part of a plan does. */
internal data class TaskPlan(
    val post: List<PlannedTaskReminder>,
    val cancel: List<NotificationKey>,
    val wakeAt: List<Instant>,
    val posted: List<PostedTaskReminder>,
    val showing: List<ShowingTaskReminder>,
)

/**
 * Follow-up and Loan Reminders. Each open one with a day, of someone not Archived, is brought up
 * once: at the Reminder time on its day, or as soon as Orbit runs if that day has passed (phone
 * off, channel muted). Unlike an Important date it doesn't expire: it stays until dismissed, and
 * goes when it's done, returned, deleted or moved to another day, or its Person is archived or
 * deleted. Moving it to another day brings it up again on that day. A showing one is quietly
 * re-posted when what it shows changes (the text, the Person, App lock).
 */
internal object TaskReminderPlanner {

    fun plan(
        people: List<UpcomingPerson>,
        today: LocalDate,
        timeHasCome: Boolean,
        canRemind: Boolean,
        state: NotificationState,
        rendering: NotificationRendering,
        slot: (LocalDate) -> Instant,
    ): TaskPlan {
        val tasks = people.flatMap(UpcomingPerson::tasks)
        val byTag = tasks.associateBy { it.notificationKey.tag }
        val keepFrom = today.minusDays(CLOSED_KEPT_DAYS).toEpochDay()
        // Closed ones are remembered a while, so Undo on Done doesn't bring one up again.
        val posted = state.postedTaskReminders.filter { entry ->
            val task = byTag[entry.tag]
            if (task != null) task.date.toEpochDay() == entry.epochDay else entry.epochDay >= keepFrom
        }.toMutableList()
        val dueNow = mutableListOf<UpcomingTask>()
        val wakeAt = mutableListOf<Instant>()
        for (task in tasks) {
            val entry = PostedTaskReminder(task.notificationKey.tag, task.date.toEpochDay())
            val hasCome = task.date.isBefore(today) || (task.date == today && timeHasCome)
            if (canRemind && hasCome && entry !in posted) {
                dueNow += task
                posted += entry
            }
            wakeAt += slot(task.date)
        }

        val post = mutableListOf<PlannedTaskReminder>()
        val cancel = mutableListOf<NotificationKey>()
        val showing = mutableListOf<ShowingTaskReminder>()
        val dueTags = dueNow.mapTo(HashSet()) { it.notificationKey.tag }
        for (shown in state.showingTaskReminders) {
            if (shown.tag in dueTags) continue // replaced below
            val task = byTag[shown.tag]
            if (task == null || task.date.toEpochDay() != shown.epochDay) {
                NotificationKey.fromTag(shown.tag)?.let(cancel::add)
                continue
            }
            val again = PlannedTaskReminder(task, silent = true)
            val content = content(task, rendering)
            if (content != shown.content) post += again
            showing += shown.copy(content = content)
        }
        for (task in dueNow) {
            post += PlannedTaskReminder(task)
            showing += ShowingTaskReminder(task.notificationKey.tag, task.date.toEpochDay(), content(task, rendering))
        }
        return TaskPlan(post, cancel, wakeAt, posted, showing)
    }

    /** What [task]'s notification shows, as a short hash: no text is copied into the device's state. */
    fun content(task: UpcomingTask, rendering: NotificationRendering): String {
        val person = task.person
        val parts = listOf(
            task.text,
            (task as? UpcomingLoan)?.loan?.direction,
            person.names,
            person.photoFileName,
            person.primaryContactMethod?.let { "${it.type}:${it.value}" },
            rendering,
        ).joinToString("\u0000")
        val hash = MessageDigest.getInstance("SHA-256").digest(parts.toByteArray())
        return hash.take(CONTENT_HASH_BYTES).joinToString("") { ((it.toInt() and 0xff) or 0x100).toString(16).substring(1) }
    }

    private const val CONTENT_HASH_BYTES = 8
    private const val CLOSED_KEPT_DAYS = 7L
}
