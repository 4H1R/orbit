package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.testing.FakePassphraseCipher
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.TestAppLocales
import java.io.File
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
    private lateinit var restorer: Restorer

    @Before
    fun setUp() = runTest {
        database = TestDatabases.onDisk(File(temp.newFolder("db"), "orbit.db"))
        workDir = temp.newFolder("restore")
        photos = temp.newFolder("photos")
        staging = File(workDir, "staging-crashed").apply { mkdirs() }
        restorer = Restorer(
            database = database,
            reader = BackupReader(OrbitDatabase.VERSION),
            preferences = preferences,
            passphrases = BackupPassphraseStore(preferences, FakePassphraseCipher()),
            appLocales = locales,
            listeners = setOf(RestoreListener { listenerRuns++ }),
            notices = notices,
            stagedDatabaseCheck = RoomStagedDatabaseCheck(),
            workDir = workDir,
            photosDir = photos,
        )
        // The live data: Sara, whose photo is "live.jpg".
        File(photos, "live.jpg").writeText("live")
        database.personDao().insert(Person(name = "Sara", ringId = database.insertTestRing(), createdAt = createdAt, photoFileName = "live.jpg"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun liveVersion() = database.backupStatusDao().dataVersion()

    private fun writeJournal(journal: RestoreJournal) {
        File(workDir, "restore-journal.json").writeText(BackupJson.encodeToString(RestoreJournal.serializer(), journal))
    }

    private fun journal(phase: RestoreJournal.Phase, dataVersion: Long, previousDataVersion: Long, previousPhotos: List<String>) =
        RestoreJournal(
            phase = phase,
            stagingDirectory = staging.path,
            previousPhotos = previousPhotos,
            restoredPhotos = listOf("restored.jpg"),
            createdAtEpochMillis = createdAt.toEpochMilli(),
            previousDataVersion = previousDataVersion,
            dataVersion = dataVersion,
            locale = "fa",
            people = 1,
        )

    @Test
    fun beforeTheCommitThePhotosItCopiedAreTakenBack() = runTest {
        // Crashed after copying photos in, before the transaction: the old data stands.
        File(photos, "restored.jpg").writeText("from the backup")
        writeJournal(journal(RestoreJournal.Phase.Swapping, dataVersion = 9_999, previousDataVersion = liveVersion(), listOf("live.jpg")))

        restorer.recoverInterrupted()

        assertEquals(listOf("live.jpg"), photos.list()!!.toList())
        assertFalse(staging.exists())
        assertFalse(File(workDir, "restore-journal.json").exists())
        assertNull(preferences.device.value.lastBackup)
        assertEquals(0, listenerRuns)
        assertTrue(locales.applied.isEmpty())
    }

    @Test
    fun afterTheCommitTheRestoreIsFinished() = runTest {
        // Crashed after the transaction committed: the live data is the Backup's ("restored.jpg"
        // is Sara's photo now), but the bookkeeping never ran.
        val sara = database.personDao().getAll().single()
        database.personDao().update(sara.copy(photoFileName = "restored.jpg"))
        File(photos, "restored.jpg").writeText("from the backup")
        writeJournal(journal(RestoreJournal.Phase.Committed, dataVersion = liveVersion(), previousDataVersion = 1, listOf("live.jpg")))

        restorer.recoverInterrupted()

        assertEquals(listOf("restored.jpg"), photos.list()!!.toList())
        assertEquals(createdAt.toEpochMilli(), preferences.device.value.lastBackup!!.createdAtEpochMillis)
        assertEquals(liveVersion(), preferences.device.value.lastBackup!!.dataVersion)
        assertEquals(1, listenerRuns)
        assertEquals(listOf<String?>("fa"), locales.applied)
        assertEquals(1, notices.restoredPeople.value)
        assertFalse(staging.exists())
    }

    @Test
    fun aCommitTheJournalNeverRecordedIsSeenInTheDataVersion() = runTest {
        // Crashed between the commit and the journal's "committed": the live version is the Backup's.
        writeJournal(journal(RestoreJournal.Phase.Swapping, dataVersion = liveVersion(), previousDataVersion = 1, listOf("live.jpg")))

        restorer.recoverInterrupted()

        assertEquals(createdAt.toEpochMilli(), preferences.device.value.lastBackup!!.createdAtEpochMillis)
        assertEquals(1, listenerRuns)
    }

    @Test
    fun staleStagingFoldersAreRemoved() = runTest {
        staging.setLastModified(0)

        restorer.recoverInterrupted(startedAtMillis = System.currentTimeMillis())

        assertFalse(staging.exists())
    }
}
