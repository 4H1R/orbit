package dev.alializadeh.orbit.data

import androidx.datastore.core.DataStore
import dev.alializadeh.orbit.backup.RoomBackupStatusRepository
import dev.alializadeh.orbit.interaction.InteractionDraft
import dev.alializadeh.orbit.interaction.InteractionType
import dev.alializadeh.orbit.interaction.RoomInteractionRepository
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The "data changed since the last Backup" signal: every write to Orbit's data bumps the data
 * version in the same transaction, whatever path it takes; Orbit's own "last notified" marker
 * doesn't; and it survives closing and reopening the database.
 */
class DataVersionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = Instant.parse("2026-10-28T08:30:00Z")
    private lateinit var file: File
    private lateinit var database: OrbitDatabase

    @Before
    fun setUp() {
        file = File(temp.root, "orbit.db")
        database = TestDatabases.onDisk(file)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun version(): Long = database.backupStatusDao().dataVersion()

    /** Asserts [write] moves the data version on. */
    private suspend fun assertBumps(what: String, write: suspend () -> Unit) {
        val before = version()
        write()
        assertTrue("$what should change the data version", version() > before)
    }

    @Test
    fun everyKindOfWriteBumpsIt() = runTest {
        val ringId = database.insertTestRing()
        val dao = database.personDao()
        var id = 0L
        assertBumps("adding a Person") { id = dao.insert(Person(name = "Sara", ringId = ringId, createdAt = now)) }
        assertBumps("editing a Person") { dao.update(dao.get(id)!!.copy(notes = "Jazz")) }
        assertBumps("snoozing") { dao.setSnoozedUntil(id, LocalDate.of(2026, 11, 1)) }
        assertBumps("archiving") { dao.setArchivedAt(id, now.toEpochMilli()) }
        assertBumps("a Tag") { database.tagDao().insert(Tag(name = "Gym")) }
        assertBumps("an Interaction") {
            RoomInteractionRepository(database, database.interactionDao(), Clock.fixed(now, ZoneOffset.UTC))
                .save(InteractionDraft(type = InteractionType.Call, date = LocalDate.of(2026, 10, 27), personIds = listOf(id)))
        }
        assertBumps("a backed-up preference") {
            RoomPreferencesRepository(database, InMemoryDeviceStateStore()).setTheme(ThemeMode.Dark)
        }
        assertBumps("deleting a Person") { dao.delete(id) }
    }

    @Test
    fun theDigestsOwnMarkerDoesNotCountAsAChange() = runTest {
        val id = database.personDao().insert(Person(name = "Sara", ringId = database.insertTestRing(), createdAt = now))
        val before = version()

        database.personDao().setLastNotifiedDueOn(id, LocalDate.of(2026, 10, 28))

        assertEquals(before, version())
    }

    @Test
    fun theTriggersAreThereAfterReopening() = runTest {
        val ringId = database.insertTestRing()
        val before = version()
        database.close()
        database = TestDatabases.onDisk(file)

        database.personDao().insert(Person(name = "Sara", ringId = ringId, createdAt = now))

        assertTrue(version() > before)
    }

    @Test
    fun theStatusComparesTheLiveVersionWithTheLastBackups() = runTest {
        val preferences = FakePreferencesRepository()
        val status = RoomBackupStatusRepository(database, preferences)
        val ringId = database.insertTestRing()
        assertTrue("Never backed up counts as changed", status.status.first().dataChanged)

        val backedUp = version()
        preferences.updateDeviceState { it.copy(lastBackup = LastBackup(now.toEpochMilli(), backedUp)) }
        assertFalse(status.status.first().dataChanged)

        database.personDao().insert(Person(name = "Sara", ringId = ringId, createdAt = now))
        assertTrue(status.status.first().dataChanged)
    }
}

private class InMemoryDeviceStateStore : DataStore<DeviceState> {
    private val state = MutableStateFlow(DeviceState())
    override val data: Flow<DeviceState> = state

    override suspend fun updateData(transform: suspend (DeviceState) -> DeviceState): DeviceState =
        transform(state.value).also { state.value = it }
}
