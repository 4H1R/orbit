package dev.alializadeh.orbit.backup

import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.testing.FakePassphraseCipher
import dev.alializadeh.orbit.testing.FakePreferencesRepository
import dev.alializadeh.orbit.testing.TestAppLocales
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kage.Age
import kage.Identity
import kage.crypto.scrypt.ScryptRecipient
import kage.format.AgeStanza
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Restore's staging refuses every kind of bad file before the device's data is touched, and leaves
 * no staging folder behind.
 */
class RestoreStagingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = Instant.parse("2026-10-28T08:30:00Z")
    private lateinit var device: OrbitDatabase
    private lateinit var workDir: File
    private lateinit var photosDir: File
    private lateinit var good: ByteArray

    @Before
    fun setUp() = runTest {
        // The Backup: a real one from another database.
        val source = TestDatabases.onDisk(File(temp.newFolder("source"), "orbit.db"))
        source.personDao().insert(Person(name = "Sara", ringId = source.insertTestRing(), createdAt = now))
        val out = ByteArrayOutputStream()
        BackupWriter(
            snapshotter = RoomDatabaseSnapshotter(source),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            appVersion = AppVersion("0.1.0", 1),
            appLocales = TestAppLocales(),
            workDir = temp.newFolder("backup-work"),
            photosDir = temp.newFolder("source-photos"),
        ).write(out, PASSPHRASE, FAST)
        source.close()
        good = out.toByteArray()

        // The device: one Person of its own.
        device = TestDatabases.onDisk(File(temp.newFolder("device"), "orbit.db"))
        device.personDao().insert(Person(name = "Mina", ringId = device.insertTestRing(), createdAt = now))
        workDir = temp.newFolder("restore")
        photosDir = temp.newFolder("photos")
    }

    @After
    fun tearDown() {
        device.close()
    }

    private fun restorer(reader: BackupReader = BackupReader(OrbitDatabase.VERSION)): Restorer {
        val preferences = FakePreferencesRepository()
        return Restorer(
            database = device,
            reader = reader,
            preferences = preferences,
            passphrases = BackupPassphraseStore(preferences, FakePassphraseCipher()),
            appLocales = TestAppLocales(),
            listeners = emptySet(),
            notices = RestoreNotices(),
            stagedDatabaseCheck = RoomStagedDatabaseCheck(),
            workDir = workDir,
            photosDir = photosDir,
        )
    }

    /** Staging [bytes] fails with [expected], and nothing changed. */
    private fun assertRefused(
        expected: KClass<out BackupException>,
        bytes: ByteArray,
        passphrase: String = PASSPHRASE,
        reader: BackupReader = BackupReader(OrbitDatabase.VERSION),
    ) = runBlocking {
        try {
            restorer(reader).stage({ bytes.inputStream() }, passphrase)
            fail("Expected ${expected.simpleName}")
        } catch (e: BackupException) {
            assertTrue("Got $e", expected.isInstance(e))
        }
        assertTrue("Staging left behind: ${workDir.list()!!.toList()}", workDir.listFiles()!!.isEmpty())
        assertEquals(listOf("Mina"), device.personDao().getAll().map(Person::name))
    }

    @Test
    fun aGoodBackupStagesWithItsSummary() = runTest {
        val staged = restorer().stage({ good.inputStream() }, PASSPHRASE)

        assertEquals(1, staged.people)
        assertEquals(0, staged.interactions)
        assertEquals(now, staged.manifest.createdAt)
        assertEquals(listOf("Mina"), device.personDao().getAll().map(Person::name))
    }

    @Test
    fun wrongPassphrase() = assertRefused(BackupException.WrongPassphrase::class, good, passphrase = "not the passphrase")

    @Test
    fun notAnAgeFile() = assertRefused(BackupException.NotABackup::class, "PK\u0003\u0004 a zip, not age".toByteArray())

    @Test
    fun truncated() = assertRefused(BackupException.Corrupt::class, good.copyOf(good.size - 20))

    @Test
    fun tamperedHash() = assertRefused(
        BackupException.Corrupt::class,
        craft(manifest(listOf(entry(DATABASE_ENTRY, "original".toByteArray()))), DATABASE_ENTRY to "tampered".toByteArray()),
    )

    @Test
    fun zipSlipEntry() = assertRefused(
        BackupException.Corrupt::class,
        craft(
            manifest(listOf(entry(DATABASE_ENTRY, DB))),
            "photos/../../escape.jpg" to "x".toByteArray(),
            DATABASE_ENTRY to DB,
        ),
    ).also { assertTrue(temp.root.walk().none { it.name == "escape.jpg" }) }

    @Test
    fun zipSlipInTheManifest() = assertRefused(
        BackupException.NotABackup::class,
        craft(manifest(listOf(entry(DATABASE_ENTRY, DB), entry("photos/../../escape.jpg", DB)))),
    )

    @Test
    fun newerFormat() = assertRefused(
        BackupException.NewerVersion::class,
        craft(manifest(listOf(entry(DATABASE_ENTRY, DB)), backupFormat = BACKUP_FORMAT + 1), DATABASE_ENTRY to DB),
    )

    @Test
    fun newerSchema() = assertRefused(
        BackupException.NewerVersion::class,
        craft(manifest(listOf(entry(DATABASE_ENTRY, DB)), schema = OrbitDatabase.VERSION + 1), DATABASE_ENTRY to DB),
    )

    @Test
    fun aDatabaseThatFailsItsIntegrityCheck() {
        val garbage = ByteArray(4096) { 7 }
        assertRefused(BackupException.Corrupt::class, craft(manifest(listOf(entry(DATABASE_ENTRY, garbage))), DATABASE_ENTRY to garbage))
    }

    @Test
    fun aSqliteDatabaseThatIsNotOrbits() {
        val file = File(temp.root, "other.db")
        BundledSQLiteDriver().open(file.path).use {
            it.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT)")
            it.execSQL("PRAGMA user_version = ${OrbitDatabase.VERSION}")
        }
        val bytes = file.readBytes()
        assertRefused(BackupException.NotABackup::class, craft(manifest(listOf(entry(DATABASE_ENTRY, bytes))), DATABASE_ENTRY to bytes))
    }

    /** logN 18 runs out of heap on a 192 MiB phone: a clear error, not a crash. */
    @Test
    fun runningOutOfMemoryForScryptIsAClearError() {
        val greedy = BackupReader(OrbitDatabase.VERSION) {
            object : Identity {
                override fun unwrap(stanzas: List<AgeStanza>): ByteArray = throw OutOfMemoryError("Failed to allocate 268435456 bytes")
            }
        }
        assertRefused(BackupException.NeedsMoreMemory::class, good, reader = greedy)
    }

    // --- Review fixes ---------------------------------------------------------------------------

    /** A Backup written from [source] after [change]. */
    private suspend fun backupOf(change: suspend (OrbitDatabase) -> Unit): ByteArray {
        val source = TestDatabases.onDisk(File(temp.newFolder(), "orbit.db"))
        try {
            source.personDao().insert(Person(name = "Sara", ringId = source.insertTestRing(), createdAt = now))
            change(source)
            val out = ByteArrayOutputStream()
            BackupWriter(
                snapshotter = RoomDatabaseSnapshotter(source),
                clock = Clock.fixed(now, ZoneOffset.UTC),
                appVersion = AppVersion("0.1.0", 1),
                appLocales = TestAppLocales(),
                workDir = temp.newFolder(),
                photosDir = temp.newFolder(),
            ).write(out, PASSPHRASE, FAST)
            return out.toByteArray()
        } finally {
            source.close()
        }
    }

    private suspend fun OrbitDatabase.execute(sql: String) {
        useWriterConnection { connection -> connection.usePrepared(sql) { it.step() } }
    }

    /** Finding 10: a photo "file name" that's a path could reach outside the photos folder. */
    @Test
    fun aPhotoFileNameThatIsAPathIsRefused() {
        for (name in listOf("../../shared_prefs/x.xml", "photos/a.jpg", "a.jpg", "..")) {
            val bytes = runBlocking { backupOf { it.execute("UPDATE person SET photoFileName = '$name'") } }
            assertRefused(BackupException.Corrupt::class, bytes)
        }
    }

    /** Finding 10: Room validates the schema, not the single rows every screen relies on. */
    @Test
    fun aMissingPreferencesRowIsRefused() {
        val bytes = runBlocking { backupOf { it.execute("DELETE FROM backed_up_preferences") } }
        assertRefused(BackupException.Corrupt::class, bytes)
    }

    /** Finding 10: a missing data_version row used to escape as an IllegalStateException (a crash). */
    @Test
    fun aMissingDataVersionRowIsDamagedNotACrash() {
        val bytes = runBlocking {
            val source = TestDatabases.onDisk(File(temp.newFolder(), "orbit.db"))
            val snapshot = File(temp.newFolder(), "snapshot.db")
            try {
                source.personDao().insert(Person(name = "Sara", ringId = source.insertTestRing(), createdAt = now))
                source.execute("DELETE FROM data_version")
                RoomDatabaseSnapshotter(source).snapshotTo(snapshot)
            } finally {
                source.close()
            }
            snapshot.readBytes()
        }
        assertRefused(BackupException.Corrupt::class, craft(manifest(listOf(entry(DATABASE_ENTRY, bytes))), DATABASE_ENTRY to bytes))
    }

    /** Finding 11: the provider failing to read is "can't read this file", not "damaged". */
    @Test
    fun aReadErrorFromTheProviderIsNotReportedAsDamaged() = runBlocking {
        val failing = object : InputStream() {
            private val bytes = good.inputStream()
            private var read = 0

            override fun read(): Int = if (read++ < 300) bytes.read() else throw IOException("provider went away")

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (read >= 300) throw IOException("provider went away")
                val n = bytes.read(b, off, minOf(len, 300 - read))
                read += n
                return n
            }
        }
        try {
            restorer().stage({ failing }, PASSPHRASE)
            fail("Expected an IOException")
        } catch (e: BackupException) {
            fail("Reported as $e")
        } catch (_: IOException) {
            // Unreadable.
        }
        assertTrue(workDir.listFiles()!!.isEmpty())
    }

    /** Finding 12: a stage cancelled while it works leaves no staging folder, even if it then finishes. */
    @Test
    fun aCancelledStageLeavesNothingBehind() = runBlocking {
        val opened = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val job = launch(Dispatchers.Default) {
            restorer().stage(
                {
                    opened.countDown()
                    proceed.await()
                    good.inputStream()
                },
                PASSPHRASE,
            )
            fail("A cancelled stage returned its result")
        }
        opened.await()
        job.cancel()
        proceed.countDown()
        job.join()

        assertTrue("Staging left behind: ${workDir.list()!!.toList()}", workDir.listFiles()!!.isEmpty())
    }

    private fun manifest(
        entries: List<BackupEntry>,
        backupFormat: Int = BACKUP_FORMAT,
        schema: Int = OrbitDatabase.VERSION,
    ) = BackupManifest(
        backupFormat = backupFormat,
        dbSchemaVersion = schema,
        appVersionName = "0.1.0",
        appVersionCode = 1,
        createdAt = now,
        locale = null,
        entries = entries,
    )

    private fun entry(path: String, bytes: ByteArray) = BackupEntry(
        path = path,
        size = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString(),
    )

    /** A hand-built Backup, for shapes Orbit never writes. */
    private fun craft(manifest: BackupManifest, vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(Age.encryptStream(listOf(ScryptRecipient(PASSPHRASE.toByteArray(), FAST)), out)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(BackupJson.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
            }
        }
        return out.toByteArray()
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery staple"
        const val FAST = 10
        val DB = "db".toByteArray()
    }
}
