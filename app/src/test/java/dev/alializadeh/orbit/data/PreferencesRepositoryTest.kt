package dev.alializadeh.orbit.data

import androidx.datastore.core.DataStoreFactory
import androidx.room3.useWriterConnection
import dev.alializadeh.orbit.backup.BackupNudgeInterval
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.lock.AppLockSettings
import dev.alializadeh.orbit.interaction.InteractionType
import dev.alializadeh.orbit.person.PeopleSort
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.reminders.NotificationState
import dev.alializadeh.orbit.reminders.PostedReminder
import dev.alializadeh.orbit.reminders.ShowingReminder
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The backed-up preferences row (Room on the JVM) and the device-bound DataStore. */
class PreferencesRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: OrbitDatabase
    private lateinit var repository: RoomPreferencesRepository

    @Before
    fun setUp() {
        database = TestDatabases.inMemory()
        val store = DataStoreFactory.create(
            serializer = DeviceStateSerializer,
            produceFile = { folder.newFile("device_state.json").also { it.delete() } },
        )
        repository = RoomPreferencesRepository(database, store)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theSeededRowHoldsTheDefaults() = runTest {
        assertEquals(BackedUpPreferences(), repository.preferences.first())
        val defaults = repository.preferences.first()
        assertEquals(LocalTime.of(9, 0), defaults.reminderTime)
        assertEquals(setOf(LeadTime.OneWeek, LeadTime.OnTheDay), defaults.defaultLeadTimes)
        assertEquals(BackupNudgeInterval.Days30, defaults.backupNudgeInterval)
        assertEquals(PeopleSort.Name, defaults.peopleSort)
        assertEquals(ThemeMode.System, defaults.theme)
        assertEquals(ColourSource.OrbitViolet, defaults.colourSource)
    }

    @Test
    fun theSqlColumnDefaultsMatchTheKotlinDefaults() = runTest {
        // A row inserted with only its id (as SeedCallback does) reads back as the Kotlin defaults.
        database.useWriterConnection { transactor ->
            transactor.usePrepared("DELETE FROM backed_up_preferences") { it.step() }
            transactor.usePrepared("INSERT INTO backed_up_preferences (id) VALUES (1)") { it.step() }
        }
        assertEquals(BackedUpPreferences(), database.backedUpPreferencesDao().get())
    }

    @Test
    fun everyColumnRoundTrips() = runTest {
        val changed = BackedUpPreferences(
            theme = ThemeMode.Dark,
            colourSource = ColourSource.Custom,
            customSeedArgb = 0xFF12A594.toInt(),
            primaryCalendar = CalendarSystem.SolarHijri,
            useLatinDigits = true,
            reminderTime = LocalTime.of(20, 45),
            defaultLeadTimes = setOf(LeadTime.OneMonth, LeadTime.ThreeDays, LeadTime.OnTheDay),
            backupNudgeInterval = BackupNudgeInterval.Off,
            peopleSort = PeopleSort.RecentlyAdded,
        )
        repository.update { changed }
        assertEquals(changed, repository.preferences.first())

        repository.update { it.copy(defaultLeadTimes = emptySet()) }
        assertEquals(emptySet<LeadTime>(), repository.preferences.first().defaultLeadTimes)
    }

    @Test
    fun appearanceSetters() = runTest {
        repository.setTheme(ThemeMode.Light)
        repository.setColourSource(ColourSource.Wallpaper)
        assertEquals(ThemeMode.Light, repository.preferences.first().theme)
        assertEquals(ColourSource.Wallpaper, repository.preferences.first().colourSource)
        assertEquals(null, repository.preferences.first().customSeedArgb)

        // "Your colour" without a seed starts on the blue swatch...
        repository.setColourSource(ColourSource.Custom)
        assertEquals(0xFF0090FF.toInt(), repository.preferences.first().customSeedArgb)

        // ...and a picked seed survives switching away and back.
        repository.setCustomColour(0xFFD6409F.toInt())
        repository.setColourSource(ColourSource.OrbitViolet)
        repository.setColourSource(ColourSource.Custom)
        val preferences = repository.preferences.first()
        assertEquals(ColourSource.Custom, preferences.colourSource)
        assertEquals(0xFFD6409F.toInt(), preferences.customSeedArgb)
    }

    @Test
    fun languageAndDateSetters() = runTest {
        repository.setPrimaryCalendar(CalendarSystem.SolarHijri)
        repository.setUseLatinDigits(true)
        val preferences = repository.preferences.first()
        assertEquals(CalendarSystem.SolarHijri, preferences.primaryCalendar)
        assertTrue(preferences.useLatinDigits)
    }

    @Test
    fun deviceStateStartsUnfinishedAndPersists() = runTest {
        assertFalse(repository.deviceState.first().firstRunFinished)
        repository.updateDeviceState { it.copy(firstRunFinished = true) }
        assertTrue(repository.deviceState.first().firstRunFinished)
    }

    @Test
    fun deviceStateJsonToleratesUnknownAndMissingFields() = runBlocking {
        val read = DeviceStateSerializer.readFrom(ByteArrayInputStream("""{"firstRunFinished":true,"later":1}""".toByteArray()))
        assertEquals(DeviceState(firstRunFinished = true), read)
        assertEquals(DeviceState(), DeviceStateSerializer.readFrom(ByteArrayInputStream("{}".toByteArray())))

        val out = ByteArrayOutputStream()
        DeviceStateSerializer.writeTo(DeviceState(firstRunFinished = true), out)
        assertEquals(
            """{"firstRunFinished":true,"lastInteractionType":"Call","wrappedBackupPassphrase":null,"lastBackup":null,""" +
                """"backupCount":0,"passphraseTypedAtBackupCount":0,"backupNudgeHiddenUntilEpochDay":null,""" +
                """"notifications":{"postedReminders":[],"showingReminders":[],"lastDigestEpochDay":null,"digestPersonIds":[],""" +
                """"renderedAs":null},"notificationPermissionAsked":false,"batteryExemptionAsked":false,""" +
                """"appLock":{"enabled":false,"relockAfter":"OneMinute","showDetailsInNotifications":false}}""",
            out.toString(Charsets.UTF_8),
        )
    }

    /** A re-lock timing this version doesn't know falls back to the default, App lock stays on. */
    @Test
    fun anUnknownRelockTimingFallsBack() = runBlocking {
        val json = """{"firstRunFinished":true,"appLock":{"enabled":true,"relockAfter":"AfterAnHour"}}"""
        val read = DeviceStateSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))
        assertTrue(read.firstRunFinished)
        assertEquals(AppLockSettings(enabled = true), read.appLock)
    }

    /** Slice 6's notification state survives a write and a read. */
    @Test
    fun notificationStateRoundTrips() = runBlocking {
        val state = DeviceState(
            notifications = NotificationState(
                postedReminders = listOf(PostedReminder(dateId = 3, occurrenceEpochDay = 20_000, leadTime = LeadTime.OneWeek)),
                showingReminders = listOf(ShowingReminder(dateId = 3, personId = 1, occurrenceEpochDay = 20_000, fingerprint = "Birthday|")),
                lastDigestEpochDay = 19_990,
                digestPersonIds = listOf(1, 2),
            ),
            notificationPermissionAsked = true,
        )
        val out = ByteArrayOutputStream()
        DeviceStateSerializer.writeTo(state, out)
        assertEquals(state, DeviceStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun theLastInteractionTypeRoundTripsAndAnUnknownOneFallsBackToTheDefault() = runBlocking {
        val out = ByteArrayOutputStream()
        DeviceStateSerializer.writeTo(DeviceState(lastInteractionType = InteractionType.InPerson), out)
        assertEquals(
            InteractionType.InPerson,
            DeviceStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray())).lastInteractionType,
        )
        // A type this version doesn't know must not throw away the whole device state.
        val read = DeviceStateSerializer.readFrom(
            ByteArrayInputStream("""{"firstRunFinished":true,"lastInteractionType":"Video"}""".toByteArray()),
        )
        assertEquals(DeviceState(firstRunFinished = true), read)
    }

    @Test(expected = androidx.datastore.core.CorruptionException::class)
    fun unreadableDeviceStateIsReportedAsCorruption() = runBlocking<Unit> {
        DeviceStateSerializer.readFrom(ByteArrayInputStream("not json".toByteArray()))
    }
}
