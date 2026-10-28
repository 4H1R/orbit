package dev.alializadeh.orbit.reminders

import androidx.room3.useWriterConnection
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.interaction.RoomInteractionRepository
import dev.alializadeh.orbit.person.ImportantDateDraft
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonDraft
import dev.alializadeh.orbit.person.RoomPersonRepository
import dev.alializadeh.orbit.testing.FakePhotoStore
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.MutableClock
import dev.alializadeh.orbit.upcoming.RoomUpcomingRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `reconcile()` over the real Room data, with the notifier and the alarm faked. */
class ReminderReconcilerTest {

    private val today = LocalDate.of(2026, 10, 28)
    private val nine: Instant = Instant.parse("2026-10-28T09:00:00Z")

    /** Starts 40 days back, so People added then are Due (the test Ring is every 30 days). */
    private val clock = MutableClock(nine.minus(Duration.ofDays(40)))
    private lateinit var database: OrbitDatabase
    private lateinit var people: RoomPersonRepository
    private lateinit var interactions: RoomInteractionRepository
    private val preferences = FakePreferencesRepository()
    private val notifier = FakeNotifier()
    private val alarm = FakeAlarm()
    private var rendering = NotificationRendering()
    private lateinit var reconciler: ReminderReconciler
    private var ringId = 0L

    @Before
    fun setUp() = runTest {
        database = TestDatabases.inMemory()
        people = RoomPersonRepository(database, database.personDao(), database.tagDao(), database.ringDao(), FakePhotoStore(), clock)
        interactions = RoomInteractionRepository(database, database.interactionDao(), clock)
        val upcoming = RoomUpcomingRepository(database.personDao(), database.ringDao(), database.interactionDao(), clock)
        reconciler = ReminderReconciler(upcoming, preferences, database.personDao(), notifier, alarm, { rendering }, clock, database.backupStatusDao())
        ringId = database.insertTestRing()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun person(name: String, vararg dates: ImportantDateDraft): Long =
        people.save(PersonDraft(name = name, ringId = ringId, importantDates = dates.toList()))

    private fun birthday(month: Int, day: Int) =
        ImportantDateDraft(type = ImportantDateType.Birthday, date = RecurringDate(CalendarSystem.Gregorian, month, day, 1991))

    /** Sara (birthday today) and Reza, both Due; Mina added today. */
    private suspend fun seed(): Triple<Long, Long, Long> {
        val sara = person("Sara", birthday(10, 28))
        val reza = person("Reza")
        clock.instant = nine
        val mina = person("Mina")
        return Triple(sara, reza, mina)
    }

    @Test
    fun postsTodaysReminderAndDigestMarksPeopleAndSetsTheNextAlarm() = runTest {
        val (sara, reza, mina) = seed()
        val versionBefore = database.backupStatusDao().dataVersion()

        reconciler.reconcile()

        val reminder = notifier.posted.map { it.first }.filterIsInstance<PlannedReminder>().single()
        assertEquals(sara, reminder.person.id)
        assertTrue(reminder.isOnTheDay)
        val digest = notifier.posted.map { it.first }.filterIsInstance<PlannedDigest>().single()
        // Sara has an on-the-day Reminder: left out, and counts as notified.
        assertEquals(listOf(reza), digest.people.map { it.person.id })
        assertEquals(today, database.personDao().get(sara)!!.lastNotifiedDueOn)
        assertEquals(today, database.personDao().get(reza)!!.lastNotifiedDueOn)
        assertNull(database.personDao().get(mina)!!.lastNotifiedDueOn)
        // The marker isn't a data change (no Backup nudge, no reconcile loop).
        assertEquals(versionBefore, database.backupStatusDao().dataVersion())
        val state = preferences.device.value.notifications
        assertEquals(today.toEpochDay(), state.lastDigestEpochDay)
        assertEquals(listOf(reza), state.digestPersonIds)
        assertEquals(1, state.showingReminders.size)
        // Next: the day Mina becomes Due (added today, every 30 days).
        assertEquals(Instant.parse("2026-11-27T09:00:00Z"), alarm.at)
    }

    @Test
    fun aSecondRunChangesNothing() = runTest {
        seed()
        reconciler.reconcile()
        val posted = notifier.posted.size
        val state = preferences.device.value.notifications
        clock.instant = nine.plus(Duration.ofMinutes(5))
        reconciler.reconcile()
        assertEquals(posted, notifier.posted.size)
        assertTrue(notifier.cancelled.isEmpty())
        assertEquals(state, preferences.device.value.notifications)
        assertEquals(Instant.parse("2026-11-27T09:00:00Z"), alarm.at)
    }

    @Test
    fun anInteractionCancelsTheDigestButNotTheReminder() = runTest {
        val (sara, reza, _) = seed()
        reconciler.reconcile()
        interactions.markAsContacted(reza)
        interactions.markAsContacted(sara)
        reconciler.reconcile()
        assertEquals(listOf(NotificationKey.Digest), notifier.cancelled)
        assertEquals(setOf<NotificationKey>(NotificationKey.DateReminder(dateIdOf(sara))), notifier.showingKeys)
    }

    @Test
    fun snoozingTheOnlyDigestPersonCancelsIt() = runTest {
        val (_, reza, _) = seed()
        reconciler.reconcile()
        people.setSnoozedUntil(reza, today.plusWeeks(1))
        reconciler.reconcile()
        assertEquals(listOf(NotificationKey.Digest), notifier.cancelled)
    }

    @Test
    fun aDismissedReminderIsNotPostedAgain() = runTest {
        val (sara, _, _) = seed()
        reconciler.reconcile()
        notifier.showingKeys.remove(NotificationKey.DateReminder(dateIdOf(sara)))
        val posted = notifier.posted.size
        clock.instant = nine.plus(Duration.ofHours(3))
        reconciler.reconcile()
        assertEquals(posted, notifier.posted.size)
        assertTrue(preferences.device.value.notifications.showingReminders.isEmpty())
    }

    @Test
    fun editingADateCancelsItsReminder() = runTest {
        val (sara, _, _) = seed()
        reconciler.reconcile()
        val date = dateIdOf(sara)
        people.saveImportantDate(sara, birthday(10, 28).copy(id = date, leadTimes = setOf(LeadTime.OneWeek)))
        reconciler.reconcile()
        assertEquals(listOf<NotificationKey>(NotificationKey.DateReminder(date)), notifier.cancelled)
    }

    @Test
    fun archivingCancelsEverythingOfThatPerson() = runTest {
        val (sara, _, _) = seed()
        reconciler.reconcile()
        people.setArchived(sara, archived = true)
        reconciler.reconcile()
        assertEquals(listOf<NotificationKey>(NotificationKey.DateReminder(dateIdOf(sara))), notifier.cancelled)
    }

    @Test
    fun mutedChannelsPostNothingAndMarkNobody() = runTest {
        val (sara, reza, _) = seed()
        notifier.channels = emptySet()
        reconciler.reconcile()
        assertTrue(notifier.posted.isEmpty())
        assertNull(database.personDao().get(sara)!!.lastNotifiedDueOn)
        assertNull(database.personDao().get(reza)!!.lastNotifiedDueOn)
        // Turned back on later that day: everything catches up.
        notifier.channels = ReminderChannel.entries.toSet()
        clock.instant = nine.plus(Duration.ofHours(6))
        reconciler.reconcile()
        assertEquals(2, notifier.posted.size)
    }

    @Test
    fun theRenderingIsChosenAtPostTime() = runTest {
        seed()
        rendering = NotificationRendering(NotificationDetail.Discreet, actionsNeedUnlock = true)
        reconciler.reconcile()
        assertTrue(notifier.posted.isNotEmpty())
        assertTrue(notifier.posted.all { it.second == rendering })
    }

    @Test
    fun aRenderingChangeQuietlyReRendersWhatsShowing() = runTest {
        seed()
        reconciler.reconcile()
        assertEquals(2, notifier.posted.size)

        rendering = NotificationRendering(NotificationDetail.Discreet)
        reconciler.reconcile()

        val again = notifier.posted.drop(2)
        assertEquals(2, again.size)
        assertTrue(again.all { (notification, how) -> notification.silent && how == rendering })
        reconciler.reconcile()
        assertEquals(4, notifier.posted.size)
    }

    @Test
    fun concurrentRunsPostOnce() = runTest {
        seed()
        (1..4).map { async { reconciler.reconcile() } }.awaitAll()
        assertEquals(2, notifier.posted.size)
    }

    @Test
    fun withNothingToRemindTheAlarmIsCancelled() = runTest {
        clock.instant = nine
        person("Sara", birthday(10, 28).copy(leadTimes = emptySet()))
        database.ringDao().update(ringId, "Friends", cadenceCount = null, cadenceUnit = null)
        alarm.at = Instant.EPOCH
        reconciler.reconcile()
        assertTrue(notifier.posted.isEmpty())
        assertNull(alarm.at)
        assertEquals(1, alarm.calls)
    }

    // --- Restore (review finding 6) ---------------------------------------------------------------

    /** What a Restore's swap writes: a new restore token. */
    private suspend fun writeRestoreToken(token: Long) {
        database.useWriterConnection { connection ->
            connection.usePrepared("INSERT OR REPLACE INTO data_version (id, version) VALUES (2, $token)") { it.step() }
        }
    }

    @Test
    fun aRestoreTakesDownWhatShowsAndPlansAgainFromNothing() = runTest {
        seed()
        reconciler.reconcile()
        assertEquals(2, notifier.showingKeys.size)

        reconciler.whileDataIsReplaced { writeRestoreToken(77) }

        // Down as soon as the data is replaced: they name the old data's People.
        assertTrue(notifier.showingKeys.isEmpty())
        val posted = notifier.posted.size
        reconciler.reconcile()
        val state = preferences.device.value.notifications
        assertEquals(77L, state.restoreToken)
        // The old state (posted Reminders, digest ids) is forgotten: today's Reminder is the
        // restored data's and is posted again.
        assertTrue(notifier.posted.drop(posted).any { it.first is PlannedReminder })
    }

    @Test
    fun aStateFromBeforeARestoreIsNeverUsedEvenAfterACrash() = runTest {
        val (sara, _, _) = seed()
        reconciler.reconcile()
        // The process died right after the swap: nothing was taken down, and what shows can't be read.
        writeRestoreToken(77)
        notifier.unreadable = true

        reconciler.reconcile()

        assertTrue(notifier.cancelled.containsAll(listOf(NotificationKey.Digest, NotificationKey.DateReminder(dateIdOf(sara)))))
        assertEquals(77L, preferences.device.value.notifications.restoreToken)
    }

    @Test
    fun theSwapWaitsForAReconcileInProgress() = runTest {
        seed()
        val gate = CompletableDeferred<Unit>()
        notifier.gate = gate
        val events = Collections.synchronizedList(mutableListOf<String>())
        alarm.onSet = { events += "reconciled" }
        val reconciling = launch(Dispatchers.Default) { reconciler.reconcile() }
        notifier.posting.await()

        val swapping = launch(Dispatchers.Default) { reconciler.whileDataIsReplaced { events += "swapped" } }
        withContext(Dispatchers.Default) { delay(300) }
        assertTrue(events.toString(), events.isEmpty())
        gate.complete(Unit)
        reconciling.join()
        swapping.join()

        assertEquals(listOf("reconciled", "swapped"), events.toList())
    }

    @Test
    fun aNotificationActionFromBeforeARestoreDoesNothing() = runTest {
        val (sara, _, _) = seed()
        val posted = NotificationActionTarget(NotificationActionType.MarkAsContacted, NotificationKey.Digest, sara, restoreToken = null)
        var acted = 0

        assertTrue(reconciler.runAction(posted) { acted++ })
        writeRestoreToken(77)
        // The same id may be someone else now.
        assertFalse(reconciler.runAction(posted) { acted++ })
        assertTrue(reconciler.runAction(posted.copy(restoreToken = 77)) { acted++ })

        assertEquals(2, acted)
    }

    private suspend fun dateIdOf(personId: Long): Long = database.personDao().getImportantDates(personId).single().id
}

/** Remembers what was posted and cancelled, and what's "on screen". */
class FakeNotifier : ReminderNotifier {
    val posted = mutableListOf<Pair<PlannedNotification, NotificationRendering>>()
    val cancelled = mutableListOf<NotificationKey>()
    val showingKeys = mutableSetOf<NotificationKey>()
    var channels: Set<ReminderChannel> = ReminderChannel.entries.toSet()

    /** What shows can't be read (null from [showing]). */
    var unreadable = false

    /** Completed when a post starts; the post then waits for [gate], if set. */
    val posting = CompletableDeferred<Unit>()
    var gate: CompletableDeferred<Unit>? = null

    override fun showing(): Set<NotificationKey>? = if (unreadable) null else showingKeys.toSet()

    override fun channelsOn(): Set<ReminderChannel> = channels

    override suspend fun post(notification: PlannedNotification, rendering: NotificationRendering, now: Instant) {
        posting.complete(Unit)
        gate?.await()
        posted += notification to rendering
        showingKeys += notification.key
    }

    override fun cancel(key: NotificationKey) {
        cancelled += key
        showingKeys -= key
    }
}

class FakeAlarm : ReminderAlarm {
    var at: Instant? = null
    var calls = 0
    var onSet: () -> Unit = {}

    override fun set(at: Instant?) {
        this.at = at
        calls++
        onSet()
    }
}
