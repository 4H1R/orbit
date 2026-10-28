package dev.alializadeh.orbit.backup

import androidx.room3.useWriterConnection
import dev.alializadeh.orbit.data.DataVersion
import dev.alializadeh.orbit.data.DeviceState
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.testing.FakePassphraseCipher
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.TestAppLocales
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A Restore that a crash interrupted is sorted out on the next start: finished when its
 * transaction had committed, otherwise the photos it copied in are taken back out. Either way the
 * data is whole, old or new, never a mix.
 */
class RestoreRecoveryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val createdAt = Instant.parse("2026-10-12T09:00:00Z")
    private lateinit var database: OrbitDatabase
    private lateinit var workDir: File
    private lateinit var photos: File
    private lateinit var staging: File
    private val preferences = FakePreferencesRepository()
    private val locales = TestAppLocales()
    private val notices = RestoreNotices()
    private var listenerRuns = 0
    private var ringId = 0L
    private val logged = mutableListOf<String>()

    @Before
    fun setUp() = runTest {
        database = TestDatabases.onDisk(File(temp.newFolder("db"), "orbit.db"))
        workDir = temp.newFolder("restore")
        photos = temp.newFolder("photos")
        staging = File(workDir, "staging-crashed").apply { mkdirs() }
        // The live data: Sara, whose photo is LIVE.
        File(photos, LIVE).writeText("live")
        ringId = database.insertTestRing()
        database.personDao().insert(Person(name = "Sara", ringId = ringId, createdAt = createdAt, photoFileName = LIVE))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun restorer(preferences: PreferencesRepository = this.preferences, listener: RestoreListener = RestoreListener { listenerRuns++ }) =
        Restorer(
            database = database,
            reader = BackupReader(OrbitDatabase.VERSION),
            preferences = preferences,
            passphrases = BackupPassphraseStore(preferences, FakePassphraseCipher()),
            appLocales = locales,
            listeners = setOf(listener),
            notices = notices,
            stagedDatabaseCheck = RoomStagedDatabaseCheck(),
            workDir = workDir,
            photosDir = photos,
            log = { logged += it },
        )

    private suspend fun liveVersion() = database.backupStatusDao().dataVersion()

    /** What the swap's transaction leaves: the restore token. */
    private suspend fun commitToken(token: Long) {
        database.useWriterConnection { connection ->
            connection.usePrepared("INSERT OR REPLACE INTO ${DataVersion.TABLE} (id, version) VALUES (${DataVersion.RESTORE_TOKEN_ROW_ID}, ?)") {
                it.bindLong(1, token)
                it.step()
            }
        }
    }

    private fun writeJournal(journal: RestoreJournal) {
        File(workDir, "restore-journal.json").writeText(BackupJson.encodeToString(RestoreJournal.serializer(), journal))
    }

    private fun journal(
        phase: RestoreJournal.Phase,
        dataVersion: Long,
        previousDataVersion: Long,
        previousPhotos: List<String>,
        restoreToken: Long? = TOKEN,
    ) = RestoreJournal(
        phase = phase,
        stagingDirectory = staging.path,
        previousPhotos = previousPhotos,
        restoredPhotos = listOf(RESTORED),
        createdAtEpochMillis = createdAt.toEpochMilli(),
        previousDataVersion = previousDataVersion,
        dataVersion = dataVersion,
        locale = "fa",
        people = 1,
        restoreToken = restoreToken,
    )

    /** The swap committed: Sara's photo is the restored one now. */
    private suspend fun swapCommitted() {
        val sara = database.personDao().getAll().single()
        database.personDao().update(sara.copy(photoFileName = RESTORED))
        File(photos, RESTORED).writeText("from the backup")
        commitToken(TOKEN)
    }

    @Test
    fun beforeTheCommitThePhotosItCopiedAreTakenBack() = runTest {
        // Crashed after copying photos in, before the transaction: the old data stands.
        File(photos, RESTORED).writeText("from the backup")
        writeJournal(journal(RestoreJournal.Phase.Swapping, dataVersion = 9_999, previousDataVersion = liveVersion(), listOf(LIVE)))

        restorer().recoverInterrupted()

        assertEquals(listOf(LIVE), photos.list()!!.toList())
        assertFalse(staging.exists())
        assertFalse(File(workDir, "restore-journal.json").exists())
        assertNull(preferences.device.value.lastBackup)
        assertEquals(0, listenerRuns)
        assertTrue(locales.applied.isEmpty())
    }

    @Test
    fun afterTheCommitTheRestoreIsFinished() = runTest {
        // Crashed after the transaction committed: the live data is the Backup's, but the
        // bookkeeping never ran.
        swapCommitted()
        writeJournal(journal(RestoreJournal.Phase.Committed, dataVersion = liveVersion(), previousDataVersion = 1, listOf(LIVE)))

        restorer().recoverInterrupted()

        assertEquals(listOf(RESTORED), photos.list()!!.toList())
        assertEquals(createdAt.toEpochMilli(), preferences.device.value.lastBackup!!.createdAtEpochMillis)
        assertEquals(liveVersion(), preferences.device.value.lastBackup!!.dataVersion)
        assertEquals(1, listenerRuns)
        assertEquals(listOf<String?>("fa"), locales.applied)
        assertEquals(1, notices.restoredPeople.value)
        assertFalse(staging.exists())
    }

    @Test
    fun aCommitTheJournalNeverRecordedIsSeenInTheRestoreToken() = runTest {
        // Crashed between the commit and the journal's "committed": the token is in the database.
        swapCommitted()
        writeJournal(journal(RestoreJournal.Phase.Swapping, dataVersion = liveVersion(), previousDataVersion = 1, listOf(LIVE)))

        restorer().recoverInterrupted()

        assertEquals(createdAt.toEpochMilli(), preferences.device.value.lastBackup!!.createdAtEpochMillis)
        assertEquals(1, listenerRuns)
        assertEquals(listOf(RESTORED), photos.list()!!.toList())
    }

    /** Review finding 8: the Backup's data version can equal the device's previous one. */
    @Test
    fun aCommitIsRecognisedEvenWhenTheBackupsDataVersionIsTheDevicesPreviousOne() = runTest {
        swapCommitted()
        val version = liveVersion()
        writeJournal(journal(RestoreJournal.Phase.Swapping, dataVersion = version, previousDataVersion = version, listOf(LIVE)))

        restorer().recoverInterrupted()

        assertEquals(1, listenerRuns)
        // The replaced data's photo went; the restored one stays.
        assertEquals(listOf(RESTORED), photos.list()!!.toList())
    }

    @Test
    fun withoutItsTokenASwapDidntCommitWhateverTheVersionsLookLike() = runTest {
        File(photos, RESTORED).writeText("from the backup")
        commitToken(TOKEN + 1) // An earlier Restore's token.
        writeJournal(journal(RestoreJournal.Phase.Swapping, dataVersion = liveVersion(), previousDataVersion = 1, listOf(LIVE)))

        restorer().recoverInterrupted()

        assertEquals(0, listenerRuns)
        assertEquals(listOf(LIVE), photos.list()!!.toList())
        assertNull(preferences.device.value.lastBackup)
    }

    /** Review finding 2: an edit made after the commit must still count as a change. */
    @Test
    fun recoveryRecordsTheBackupsDataVersionNotTheLiveOne() = runTest {
        swapCommitted()
        val restoredVersion = liveVersion()
        writeJournal(journal(RestoreJournal.Phase.Committed, dataVersion = restoredVersion, previousDataVersion = 1, listOf(LIVE)))
        // Edited after the commit, before the crash.
        database.personDao().insert(Person(name = "Reza", ringId = ringId, createdAt = createdAt))
        assertTrue(liveVersion() != restoredVersion)

        restorer().recoverInterrupted()

        assertEquals(restoredVersion, preferences.device.value.lastBackup!!.dataVersion)
    }

    /** Review finding 2: whatever fails after the commit, the app starts and the rest still runs. */
    @Test
    fun recoveryNeverThrowsAndRunsEveryStepItCan() = runTest {
        swapCommitted()
        writeJournal(journal(RestoreJournal.Phase.Committed, dataVersion = liveVersion(), previousDataVersion = 1, listOf(LIVE)))
        val brokenStore = object : PreferencesRepository by preferences {
            override suspend fun updateDeviceState(transform: (DeviceState) -> DeviceState) = throw IOException("disk full")
        }

        restorer(brokenStore, RestoreListener { error("listener broke") }).recoverInterrupted()

        assertEquals(listOf(RESTORED), photos.list()!!.toList())
        assertFalse(File(workDir, "restore-journal.json").exists())
        assertEquals(1, notices.restoredPeople.value)
        assertEquals(listOf<String?>("fa"), locales.applied)
        // Logged by class, never by message (it could quote user data).
        assertTrue(logged.toString(), logged.any { "IOException" in it } && logged.any { "IllegalStateException" in it })
        assertTrue(logged.none { "disk full" in it || "listener broke" in it })
    }

    @Test
    fun recoveryIsIdempotent() = runTest {
        swapCommitted()
        val journal = journal(RestoreJournal.Phase.Committed, dataVersion = liveVersion(), previousDataVersion = 1, listOf(LIVE))
        writeJournal(journal)
        restorer().recoverInterrupted()
        val after = preferences.device.value

        // The process died before the journal went: the same journal again.
        staging.mkdirs()
        writeJournal(journal)
        restorer().recoverInterrupted()

        assertEquals(after.lastBackup, preferences.device.value.lastBackup)
        assertEquals(listOf(RESTORED), photos.list()!!.toList())
    }

    @Test
    fun staleStagingFoldersAndPartialCopiesAreRemoved() = runTest {
        staging.setLastModified(0)
        val partialPhoto = File(photos, "$RESTORED.restoring").apply { writeText("half") }
        partialPhoto.setLastModified(0)
        val partialJournal = File(workDir, "restore-journal.json.restoring").apply { writeText("{") }

        restorer().recoverInterrupted(startedAtMillis = System.currentTimeMillis())

        assertFalse(staging.exists())
        assertFalse(partialPhoto.exists())
        assertFalse(partialJournal.exists())
        assertEquals(listOf(LIVE), photos.list()!!.toList())
    }

    private companion object {
        const val LIVE = "0f8fad5b-d9cb-469f-a165-70867728950e.jpg"
        const val RESTORED = "7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg"
        const val TOKEN = 4_242_424_242L
    }
}
