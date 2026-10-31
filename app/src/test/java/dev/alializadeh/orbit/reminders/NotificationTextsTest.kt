package dev.alializadeh.orbit.reminders

import android.app.Notification
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.format.OrbitFormats
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.testing.FakePhotoStore
import dev.alializadeh.orbit.upcoming.DuePerson
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every notification's words (copy deck) in English and Persian, and how they're built. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationTextsTest {

    private val today = LocalDate.of(2026, 10, 28)

    private fun texts(language: OrbitLanguage, latinDigits: Boolean = false) =
        NotificationTexts(RuntimeEnvironment.getApplication().resources, OrbitFormats(language, latinDigits))

    private fun person(
        id: Long = 1,
        name: String = "Sara",
        persian: String = "سارا",
        ideas: List<String> = emptyList(),
        primary: ContactMethod? = null,
        keepInTouch: KeepInTouch = KeepInTouch(Cadence.days(30), addedOn = today.minusDays(40), lastInteraction = null),
    ) = UpcomingPerson(
        id = id,
        names = PersonNames(name, otherScriptName = persian),
        photoFileName = null,
        isArchived = false,
        primaryContactMethod = primary,
        importantDates = emptyList(),
        openGiftIdeas = ideas,
        keepInTouch = keepInTouch,
    )

    private fun reminder(
        daysAway: Int,
        type: ImportantDateType = ImportantDateType.Birthday,
        label: String? = null,
        year: Int? = 1991,
        person: UpcomingPerson = person(),
    ): PlannedReminder {
        val occurrenceDay = today.plusDays(daysAway.toLong())
        val date = ImportantDate(
            id = 7,
            personId = person.id,
            type = type,
            label = label,
            date = RecurringDate(CalendarSystem.Gregorian, occurrenceDay.monthValue, occurrenceDay.dayOfMonth, year),
            position = 0,
        )
        return PlannedReminder(
            person = person,
            date = date,
            occurrence = date.date.nextOccurrence(today),
            leadTime = LeadTime.OneWeek,
            daysAway = daysAway,
            expiresAt = occurrenceDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        )
    }

    private fun digest(vararg people: UpcomingPerson) =
        PlannedDigest(people.map { DuePerson(it, it.keepInTouch.on(today)) }, today)

    private fun four() = arrayOf(
        person(1, "Sara", "سارا"),
        person(2, "Reza", "رضا"),
        person(3, "Mina", "مینا"),
        person(4, "Ali", "علی"),
    )

    /** Without the bidi isolates around user values, to compare with plain text. */
    private fun String.plain() = replace(Regex("[⁦-⁩]"), "")

    private fun NotificationText.plain() = listOf(title.plain()) + lines.map { it.plain() }

    @Test
    @Config(qualifiers = "en")
    fun remindersInEnglish() {
        val texts = texts(OrbitLanguage.English)
        assertEquals(
            listOf("Sara's birthday · in 7 days", "Turns 35", "Gift ideas: Book, Scarf and 1 more"),
            texts.reminder(reminder(7, person = person(ideas = listOf("Book", "Scarf", "Lamp")))).plain(),
        )
        assertEquals(listOf("Sara's birthday · tomorrow", "Turns 35"), texts.reminder(reminder(1)).plain())
        assertEquals(listOf("Today is Sara's birthday", "Turns 35"), texts.reminder(reminder(0)).plain())
        assertEquals(listOf("Today is Sara's birthday"), texts.reminder(reminder(0, year = null)).plain())
        assertEquals(
            listOf("Sara's anniversary · in 14 days", "35th anniversary"),
            texts.reminder(reminder(14, ImportantDateType.Anniversary)).plain(),
        )
        assertEquals(
            listOf("Sara's Graduation · in 3 days", "Gift ideas: Book"),
            texts.reminder(reminder(3, ImportantDateType.Custom, "Graduation", year = null, person = person(ideas = listOf("Book")))).plain(),
        )
        // Names are isolated, so a Persian name can't reorder the sentence.
        assertTrue(texts.reminder(reminder(7)).title.startsWith("⁨Sara⁩"))
    }

    @Test
    @Config(qualifiers = "en")
    fun discreetTextsNeverNameAnyoneOrALabel() {
        val texts = texts(OrbitLanguage.English)
        assertEquals("A birthday is coming up · in 7 days", texts.discreetReminder(reminder(7)).title)
        assertEquals("There's a birthday today", texts.discreetReminder(reminder(0)).title)
        assertEquals("An anniversary is coming up · tomorrow", texts.discreetReminder(reminder(1, ImportantDateType.Anniversary)).title)
        assertEquals(
            "An important date is coming up · in 30 days",
            texts.discreetReminder(reminder(30, ImportantDateType.Custom, "Graduation")).title,
        )
        assertEquals("There's an important date today", texts.discreetReminder(reminder(0, ImportantDateType.Custom, "Graduation")).title)
        assertEquals("Time to catch up with someone", texts.discreetDigest(digest(person())).title)
        assertEquals("Time to catch up with 4 people", texts.discreetDigest(digest(*four())).title)
        assertTrue(texts.discreetDigest(digest(person())).lines.isEmpty())
    }

    @Test
    @Config(qualifiers = "en")
    fun digestsInEnglish() {
        val texts = texts(OrbitLanguage.English)
        val talked = person(keepInTouch = KeepInTouch(Cadence.days(30), today.minusDays(100), lastInteraction = today.minusDays(34)))
        assertEquals(listOf("Time to catch up with Sara", "Last talked 34 days ago"), texts.digest(digest(talked)).plain())
        assertEquals(
            listOf("Time to catch up with Sara", "Added 40 days ago, no interactions yet"),
            texts.digest(digest(person())).plain(),
        )
        val (sara, reza, mina) = four()
        assertEquals(listOf("Time to catch up with Sara and Reza", "2 people are due"), texts.digest(digest(sara, reza)).plain())
        assertEquals("Time to catch up with Sara, Reza and Mina", texts.digest(digest(sara, reza, mina)).title.plain())
        assertEquals(listOf("Time to catch up with Sara, Reza and 2 others", "4 people are due"), texts.digest(digest(*four())).plain())
    }

    @Test
    @Config(qualifiers = "fa")
    fun remindersAndDigestsInPersian() {
        val texts = texts(OrbitLanguage.Persian)
        assertEquals(
            listOf("تولد سارا، ۷ روز دیگر", "۳۵ ساله می‌شود", "ایده‌های هدیه: کتاب، شال و ۱ مورد دیگر"),
            texts.reminder(reminder(7, person = person(ideas = listOf("کتاب", "شال", "چراغ")))).plain(),
        )
        assertEquals("تولد سارا، فردا", texts.reminder(reminder(1)).title.plain())
        assertEquals("امروز تولد سارا است", texts.reminder(reminder(0)).title.plain())
        assertEquals(
            listOf("سالگرد سارا، ۱۴ روز دیگر", "سی‌وپنجمین سالگرد"),
            texts.reminder(reminder(14, ImportantDateType.Anniversary)).plain(),
        )
        assertEquals("فارغ‌التحصیلی سارا، ۳ روز دیگر", texts.reminder(reminder(3, ImportantDateType.Custom, "فارغ‌التحصیلی")).title.plain())
        assertEquals(
            listOf("وقت احوال‌پرسی از سارا است", "۴۰ روز پیش اضافه شد، هنوز ارتباطی ثبت نشده"),
            texts.digest(digest(person())).plain(),
        )
        assertEquals(
            listOf("وقت احوال‌پرسی از سارا، رضا و ۲ نفر دیگر است", "موعد ارتباط با ۴ نفر رسیده است"),
            texts.digest(digest(*four())).plain(),
        )
        assertEquals("تولدی در پیش است، ۷ روز دیگر", texts.discreetReminder(reminder(7)).title)
        assertEquals("امروز تولد یک نفر است", texts.discreetReminder(reminder(0)).title)
        assertEquals("وقت احوال‌پرسی از ۴ نفر است", texts.discreetDigest(digest(*four())).title)
        // "Use Latin digits".
        assertEquals("تولد سارا، 7 روز دیگر", texts(OrbitLanguage.Persian, latinDigits = true).reminder(reminder(7)).title.plain())
    }

    // The built notifications

    private val now = Instant.parse("2026-10-28T09:00:00Z")
    private val phone = ContactMethod(id = 1, personId = 1, type = ContactType.Phone, value = "+44 7700 900123", isPrimary = true, position = 0)

    private fun build(notification: PlannedNotification, rendering: NotificationRendering = NotificationRendering()): Notification =
        NotificationFactory(RuntimeEnvironment.getApplication(), OrbitFormats(OrbitLanguage.English, false), FakePhotoStore())
            .build(notification, rendering, now)

    private val Notification.title: String get() = extras.getCharSequence(Notification.EXTRA_TITLE).toString().plain()

    @Test
    @Config(qualifiers = "en")
    fun theOnTheDayReminderHasTheContactActionMarkAsContactedAndSendWishes() {
        val notification = build(reminder(0, person = person(primary = phone)))
        assertEquals(ReminderChannel.ImportantDates.id, notification.channelId)
        assertEquals("Today is Sara's birthday", notification.title)
        assertEquals(listOf("Call", "Mark as contacted", "Send wishes"), notification.actions.map { it.title.toString() })
        assertFalse(notification.actions.any { it.isAuthenticationRequired })
        // Send wishes opens Orbit on the Person, with the sheet for this date.
        val wishes = org.robolectric.Shadows.shadowOf(notification.actions.last().actionIntent).savedIntent
        val reminder = reminder(0, person = person(primary = phone))
        assertEquals(NotificationEntry.Wishes(reminder.person.id, reminder.date.id), NotificationEntry.of(wishes))
        assertTrue(notification.getLargeIcon() != null)
        // Expires at the end of the date's day.
        assertEquals(15 * 60 * 60 * 1000L, notification.timeoutAfter) // 09:00 until the end of the day
        // Private, with the discreet text on the lock screen.
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals("There's a birthday today", notification.publicVersion.title)
        assertNull(notification.publicVersion.getLargeIcon())
    }

    @Test
    @Config(qualifiers = "en")
    fun aQuietlyRepostedReminderDoesntAlertAgain() {
        val first = build(reminder(7))
        assertEquals(0, first.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertEquals(Notification.GROUP_ALERT_ALL, first.groupAlertBehavior)
        // A new day, an edit or App lock re-posts it: no sound or vibration, like the digest's.
        for (again in listOf(build(reminder(6).copy(silent = true)), build(reminder(0).copy(silent = true), NotificationRendering(NotificationDetail.Discreet)))) {
            assertTrue((again.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
            assertEquals(Notification.GROUP_ALERT_SUMMARY, again.groupAlertBehavior)
            assertNull(again.sound)
        }
        val digest = build(digest(person()).copy(silent = true))
        assertTrue((digest.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
    }

    @Test
    @Config(qualifiers = "en")
    fun earlierRemindersHaveNoActionsAndNoPrimaryMeansNoContactAction() {
        assertNull(build(reminder(7, person = person(primary = phone))).actions)
        assertEquals(listOf("Mark as contacted", "Send wishes"), build(reminder(0)).actions.map { it.title.toString() })
    }

    @Test
    @Config(qualifiers = "en")
    fun aDiscreetNotificationHasNoNamePhotoOrActions() {
        val notification = build(reminder(0, person = person(primary = phone, ideas = listOf("Book"))), NotificationRendering(NotificationDetail.Discreet))
        assertEquals("There's a birthday today", notification.title)
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertNull(notification.getLargeIcon())
        assertNull(notification.actions)
    }

    @Test
    @Config(qualifiers = "en")
    fun theDigestForOneHasThreeActionsAndForSeveralNone() {
        val one = build(digest(person(primary = phone)), NotificationRendering(actionsNeedUnlock = true))
        assertEquals(ReminderChannel.KeepInTouch.id, one.channelId)
        assertEquals(listOf("Call", "Mark as contacted", "Snooze 1 week"), one.actions.map { it.title.toString() })
        // With App lock's "Show details": the background actions ask for the device unlock.
        assertEquals(listOf(false, true, true), one.actions.map { it.isAuthenticationRequired })
        assertEquals("Time to catch up with someone", one.publicVersion.title)

        val several = build(digest(*four()))
        assertEquals("Time to catch up with Sara, Reza and 2 others", several.title)
        assertNull(several.actions)
        assertNull(several.getLargeIcon())
        assertEquals("Time to catch up with 4 people", several.publicVersion.title)
    }

    @Test
    fun entriesAndActionsRoundTripThroughTheirIntents() {
        val context = RuntimeEnvironment.getApplication()
        val intents = NotificationIntents(context)
        for (entry in listOf(NotificationEntry.PersonDetail(42), NotificationEntry.UpcomingDue, NotificationEntry.Wishes(42, 7))) {
            assertEquals(entry, NotificationEntry.of(intents.openIntent(entry)))
            // Review finding 8: another app can start the exported MainActivity, but not with an entry.
            val direct = android.content.Intent(android.content.Intent.ACTION_VIEW, entry.uri)
            assertNull(NotificationEntry.of(direct))
            assertNull(NotificationEntry.of(direct.setClass(context, dev.alializadeh.orbit.MainActivity::class.java)))
        }
        assertNull(NotificationEntry.of(android.content.Intent(context, dev.alializadeh.orbit.MainActivity::class.java)))
        for (key in listOf(NotificationKey.Digest, NotificationKey.DateReminder(7))) {
            for (token in listOf(null, 5_381L, -7_046_029_254_386_353_131L)) {
                val target = NotificationActionTarget(NotificationActionType.SnoozeWeek, key, 42, token)
                val intent = android.content.Intent(target.type.action, target.uri)
                assertEquals(target, NotificationActionTarget.of(intent))
            }
        }
        // Posted before restore tokens: no token, so it only acts while there's been no Restore.
        val legacy = android.net.Uri.parse("orbit://action/orbit.digest/42")
        assertEquals(
            NotificationActionTarget(NotificationActionType.MarkAsContacted, NotificationKey.Digest, 42, null),
            NotificationActionTarget.of(android.content.Intent(NotificationActionType.MarkAsContacted.action, legacy)),
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun backgroundActionsCarryTheRestoreTokenTheyWerePostedFor() {
        val notification = NotificationFactory(RuntimeEnvironment.getApplication(), OrbitFormats(OrbitLanguage.English, false), FakePhotoStore(), restoreToken = 99L)
            .build(reminder(0), NotificationRendering(), now)
        val action = notification.actions.first()
        val intent = org.robolectric.Shadows.shadowOf(action.actionIntent).savedIntent
        assertEquals(99L, NotificationActionTarget.of(intent)!!.restoreToken)
    }

    @Test
    @Config(qualifiers = "en")
    fun anOccasionReminderNamesItAndCountsItsPeople() {
        val people = listOf(person(), person())
        val occasion = dev.alializadeh.orbit.occasion.occasionOf(
            1,
            RecurringDate(CalendarSystem.SolarHijri, 1, 1),
            forEveryone = true,
            builtIn = dev.alializadeh.orbit.occasion.BuiltInOccasion.Nowruz,
        )
        fun occasionReminder(daysAway: Int) = PlannedOccasionReminder(
            occasion = occasion,
            people = people,
            occurrence = RecurringDate.Occurrence(today.plusDays(daysAway.toLong()), 1406, null),
            leadTime = LeadTime.OneWeek,
            daysAway = daysAway,
            expiresAt = now.plusSeconds(86_400L * (daysAway + 1)),
        )
        val soon = build(occasionReminder(7))
        assertEquals(ReminderChannel.Occasions.id, soon.channelId)
        assertEquals("Nowruz · in 7 days", soon.title)
        assertEquals("2 people to send wishes to", soon.extras.getCharSequence(Notification.EXTRA_TEXT).toString().plain())
        assertEquals("An occasion is coming up · in 7 days", soon.publicVersion.title)
        assertNull(soon.actions)
        val onTheDay = build(occasionReminder(0))
        assertEquals("Today is Nowruz", onTheDay.title)
        assertEquals("There's an occasion today", onTheDay.publicVersion.title)
        // Discreet (App lock): no name.
        assertEquals("An occasion is coming up · in 7 days", build(occasionReminder(7), NotificationRendering(NotificationDetail.Discreet)).title)
    }

    private fun followUpTask() = PlannedTaskReminder(
        dev.alializadeh.orbit.upcoming.UpcomingFollowUp(
            person(),
            dev.alializadeh.orbit.person.FollowUp(id = 3, personId = 1, text = "The interview", dueOn = today, createdAt = java.time.Instant.EPOCH),
        ),
    )

    private fun loanTask(direction: dev.alializadeh.orbit.person.LoanDirection) = PlannedTaskReminder(
        dev.alializadeh.orbit.upcoming.UpcomingLoan(
            person(),
            dev.alializadeh.orbit.person.Loan(id = 4, personId = 1, direction = direction, text = "The tent", returnBy = today, createdAt = java.time.Instant.EPOCH),
        ),
    )

    @Test
    fun followUpAndLoanTextsNameThePersonAndTheirDiscreetOnesDont() {
        val english = texts(OrbitLanguage.English)
        val followUp = english.task(followUpTask())
        assertEquals("Follow up with Sara", followUp.title.plain())
        assertEquals(listOf("The interview"), followUp.lines)
        assertEquals("You have a follow-up", english.discreetTask(followUpTask()).title)

        assertEquals("Ask Sara to return this", english.task(loanTask(dev.alializadeh.orbit.person.LoanDirection.Lent)).title.plain())
        assertEquals("Return this to Sara", english.task(loanTask(dev.alializadeh.orbit.person.LoanDirection.Borrowed)).title.plain())
        val discreet = english.discreetTask(loanTask(dev.alializadeh.orbit.person.LoanDirection.Lent))
        assertEquals("It's time for something to be returned", discreet.title)
        assertTrue(discreet.lines.isEmpty())
    }

    @Test
    @Config(qualifiers = "fa")
    fun anOccasionReminderInPersian() {
        val occasion = dev.alializadeh.orbit.occasion.occasionOf(
            1,
            RecurringDate(CalendarSystem.SolarHijri, 1, 1),
            forEveryone = true,
            builtIn = dev.alializadeh.orbit.occasion.BuiltInOccasion.Nowruz,
        )
        val reminder = PlannedOccasionReminder(
            occasion = occasion,
            people = listOf(person()),
            occurrence = RecurringDate.Occurrence(today, 1406, null),
            leadTime = LeadTime.OnTheDay,
            daysAway = 0,
            expiresAt = now.plusSeconds(86_400L),
        )
        val text = texts(OrbitLanguage.Persian).occasion(reminder)
        assertEquals("امروز نوروز است", text.title.plain())
        assertEquals(listOf("به ۱ نفر تبریک بگویید"), text.lines.map { it.plain() })
    }
}
