package dev.alializadeh.orbit.reminders

import android.content.res.Resources
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.OrbitFormats
import dev.alializadeh.orbit.format.RelativeDays
import dev.alializadeh.orbit.format.bidiIsolate
import dev.alializadeh.orbit.format.text
import dev.alializadeh.orbit.occasion.occasionName
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.LoanDirection
import dev.alializadeh.orbit.person.dates.giftIdeasLine
import dev.alializadeh.orbit.person.dates.importantDateName
import dev.alializadeh.orbit.person.dates.todayTitle
import dev.alializadeh.orbit.person.dates.yearsCountText
import dev.alializadeh.orbit.upcoming.DuePerson
import dev.alializadeh.orbit.upcoming.UpcomingFollowUp
import dev.alializadeh.orbit.upcoming.UpcomingLoan
import java.time.LocalDate

/** A notification's words: a title and its body lines (each line on its own when expanded). */
data class NotificationText(val title: String, val lines: List<String> = emptyList())

/**
 * The words of every notification (copy deck: Reminder notifications, Cadence digest, Discreet
 * notifications), in the UI language of [resources] with Orbit's digits and names from [formats].
 * Detailed texts name People; discreet ones (App lock on, and every notification's public version
 * on the lock screen) never do, nor show a custom date's label.
 */
class NotificationTexts(private val resources: Resources, private val formats: OrbitFormats) {

    private val numbers get() = formats.numbers

    /**
     * "Sara's birthday · in 7 days" / "Today is Sara's birthday", then the years count ("Turns
     * 35") and the open Gift ideas ("Gift ideas: Book, Scarf and 1 more") on their own lines.
     */
    fun reminder(reminder: PlannedReminder): NotificationText {
        val name = reminder.person.names.displayName(formats.language)
        val date = reminder.date
        val title = if (reminder.isOnTheDay) {
            todayTitle(resources, name, date.type, date.label)
        } else {
            val whenText = whenText(reminder.daysAway)
            when (date.type) {
                ImportantDateType.Birthday -> resources.getString(R.string.reminder_title_birthday, bidiIsolate(name), whenText)
                ImportantDateType.Anniversary ->
                    resources.getString(R.string.reminder_title_anniversary, bidiIsolate(name), whenText)
                ImportantDateType.Custom -> resources.getString(
                    R.string.reminder_title_custom,
                    bidiIsolate(name),
                    bidiIsolate(importantDateName(resources, date.type, date.label)),
                    whenText,
                )
            }
        }
        return NotificationText(
            title = title,
            lines = listOfNotNull(
                yearsCountText(resources, numbers, date.type, reminder.occurrence.yearsCount),
                giftIdeasLine(resources, numbers, reminder.person.openGiftIdeas),
            ),
        )
    }

    /**
     * "Nowruz · in 7 days" with "12 people to send wishes to", or "Today is Nowruz" with "Send
     * wishes to 12 people".
     */
    fun occasion(reminder: PlannedOccasionReminder): NotificationText {
        val name = bidiIsolate(resources.occasionName(reminder.occasion.occasion))
        val count = reminder.people.size
        return if (reminder.isOnTheDay) {
            NotificationText(
                resources.getString(R.string.reminder_title_occasion_today, name),
                listOf(resources.getQuantityString(R.plurals.reminder_occasion_people_today, count, numbers.format(count))),
            )
        } else {
            NotificationText(
                resources.getString(R.string.reminder_title_occasion, name, whenText(reminder.daysAway)),
                listOf(resources.getQuantityString(R.plurals.reminder_occasion_people, count, numbers.format(count))),
            )
        }
    }

    /** "Follow up with Sara", "Ask Sara to return this" or "Return this to Sara", then what it's about. */
    fun task(reminder: PlannedTaskReminder): NotificationText {
        val task = reminder.task
        val name = bidiIsolate(task.person.names.displayName(formats.language))
        val title = when (task) {
            is UpcomingFollowUp -> resources.getString(R.string.reminder_follow_up_title, name)
            is UpcomingLoan -> resources.getString(
                when (task.loan.direction) {
                    LoanDirection.Lent -> R.string.reminder_loan_lent_title
                    LoanDirection.Borrowed -> R.string.reminder_loan_borrowed_title
                },
                name,
            )
        }
        return NotificationText(title, listOf(task.text))
    }

    /** No name, and nothing of what it's about. */
    fun discreetTask(reminder: PlannedTaskReminder): NotificationText = NotificationText(
        resources.getString(
            when (reminder.task) {
                is UpcomingFollowUp -> R.string.discreet_follow_up
                is UpcomingLoan -> R.string.discreet_loan
            },
        ),
    )

    /** "An occasion is coming up · in 7 days", "There's an occasion today": no name. */
    fun discreetOccasion(reminder: PlannedOccasionReminder): NotificationText = NotificationText(
        if (reminder.isOnTheDay) {
            resources.getString(R.string.discreet_occasion_today)
        } else {
            resources.getString(R.string.discreet_occasion_soon, whenText(reminder.daysAway))
        },
    )

    /** "A birthday is coming up · in 7 days", "There's a birthday today": no name or label. */
    fun discreetReminder(reminder: PlannedReminder): NotificationText {
        val type = reminder.date.type
        val title = if (reminder.isOnTheDay) {
            resources.getString(
                when (type) {
                    ImportantDateType.Birthday -> R.string.discreet_birthday_today
                    ImportantDateType.Anniversary -> R.string.discreet_anniversary_today
                    ImportantDateType.Custom -> R.string.discreet_date_today
                },
            )
        } else {
            resources.getString(
                when (type) {
                    ImportantDateType.Birthday -> R.string.discreet_birthday_soon
                    ImportantDateType.Anniversary -> R.string.discreet_anniversary_soon
                    ImportantDateType.Custom -> R.string.discreet_date_soon
                },
                whenText(reminder.daysAway),
            )
        }
        return NotificationText(title)
    }

    /**
     * "Time to catch up with Sara" with "Last talked 34 days ago" (or "Added 34 days ago, no
     * interactions yet"); with several, "Time to catch up with Sara, Reza and 2 others" and "4
     * people are due".
     */
    fun digest(digest: PlannedDigest): NotificationText {
        val people = digest.people
        val names = people.map { bidiIsolate(it.person.names.displayName(formats.language)) }
        val title = resources.getString(R.string.digest_title, namesList(names))
        val body = if (people.size == 1) {
            singleDigestBody(people.single(), digest.today)
        } else {
            resources.getQuantityString(R.plurals.digest_body_several, people.size, numbers.format(people.size))
        }
        return NotificationText(title, listOf(body))
    }

    /** "Time to catch up with someone" / "Time to catch up with 3 people". */
    fun discreetDigest(digest: PlannedDigest): NotificationText = NotificationText(
        if (digest.people.size == 1) {
            resources.getString(R.string.discreet_digest_one)
        } else {
            resources.getQuantityString(R.plurals.discreet_digest_several, digest.people.size, numbers.format(digest.people.size))
        },
    )

    private fun singleDigestBody(person: DuePerson, today: LocalDate): String {
        val last = person.state.lastInteraction
        return if (last != null) {
            resources.getString(R.string.digest_body_one, RelativeDays.ago(last, today).text(resources, numbers))
        } else {
            val added = person.person.keepInTouch.addedOn
            resources.getString(R.string.digest_body_one_never, RelativeDays.ago(added, today).text(resources, numbers))
        }
    }

    /** "Sara", "Sara and Reza", "Sara, Reza and Mina", "Sara, Reza and 2 others". */
    private fun namesList(names: List<String>): String = when (names.size) {
        1 -> names[0]
        2 -> resources.getString(R.string.names_two, names[0], names[1])
        3 -> resources.getString(R.string.names_three, names[0], names[1], names[2])
        else -> resources.getQuantityString(R.plurals.names_more, names.size - 2, names[0], names[1], numbers.format(names.size - 2))
    }

    /** "tomorrow", "in 7 days" / «فردا», «۷ روز دیگر». */
    private fun whenText(daysAway: Int): String = if (daysAway == 1) {
        resources.getString(R.string.time_tomorrow_inline)
    } else {
        resources.getQuantityString(R.plurals.time_in_days_inline, daysAway, numbers.format(daysAway))
    }
}
