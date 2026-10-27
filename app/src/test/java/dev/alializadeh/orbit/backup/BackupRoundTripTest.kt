package dev.alializadeh.orbit.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.person.Person
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kage.Age
import kage.crypto.scrypt.ScryptIdentity
import kage.crypto.scrypt.ScryptRecipient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Backup format end to end on the JVM: a real Room database (bundled SQLite), `VACUUM INTO`,
 * ZIP, age, then the reader's verification. Most tests use a low scrypt cost to stay fast; one
 * checks the real default.
 */
class BackupRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = Instant.parse("2026-10-28T08:30:00Z")
    private lateinit var database: OrbitDatabase
    private lateinit var workDir: File
    private lateinit var writer: BackupWriter
    private val reader = BackupReader(currentDbSchemaVersion = OrbitDatabase.VERSION)

    @Before
    fun setUp() = runTest {
        database = TestDatabases.onDisk(temp.newFile("orbit.db").apply { delete() })
        val ringId = database.insertTestRing()
        database.personDao().insert(Person(name = "Sara", ringId = ringId, createdAt = now))
        database.personDao().insert(Person(name = "بابک", ringId = ringId, createdAt = now))
        workDir = temp.newFolder("backup-work")
        writer = BackupWriter(
            snapshotter = RoomDatabaseSnapshotter(database),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            appVersion = AppVersion(name = "0.1.0", code = 1),
            appLocales = { "fa" },
            workDir = workDir,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aBackupReadsBackWithItsManifestAndTheDatabase() = runTest {
        val backup = ByteArrayOutputStream()
        val written = writer.write(backup, PASSPHRASE, FAST)
        val staging = temp.newFolder("staging")

        val read = reader.read(backup.toByteArray().inputStream(), PASSPHRASE, extractTo = staging)

        assertEquals(written, read)
        assertEquals(
            BackupManifest(
                backupFormat = BACKUP_FORMAT,
                dbSchemaVersion = OrbitDatabase.VERSION,
                appVersionName = "0.1.0",
                appVersionCode = 1,
                createdAt = now,
                locale = "fa",
                entries = read.entries,
            ),
            read,
        )
        assertEquals(listOf(DATABASE_ENTRY), read.entries.map(BackupEntry::path))
        assertEquals(listOf("Sara", "بابک"), namesIn(File(staging, DATABASE_ENTRY)))
    }

    @Test
    fun theSnapshotCarriesTheSchemaVersionAndThePreferencesRow() = runTest {
        val backup = ByteArrayOutputStream()
        writer.write(backup, PASSPHRASE, FAST)
        val staging = temp.newFolder("staging")
        reader.read(backup.toByteArray().inputStream(), PASSPHRASE, extractTo = staging)

        BundledSQLiteDriver().open(File(staging, DATABASE_ENTRY).path).use { connection ->
            connection.prepare("PRAGMA user_version").use { it.step(); assertEquals(1L, it.getLong(0)) }
            connection.prepare("SELECT COUNT(*) FROM backed_up_preferences").use {
                it.step()
                assertEquals(1L, it.getLong(0))
            }
        }
    }

    @Test
    fun theSnapshotFileIsDeletedAfterwards() = runTest {
        writer.write(ByteArrayOutputStream(), PASSPHRASE, FAST)

        assertTrue(workDir.listFiles()!!.isEmpty())
    }

    @Test
    fun aBackupIsAStandardAgeFileWithOneScryptRecipientAtLogN17ByDefault() = runTest {
        val backup = ByteArrayOutputStream()
        writer.write(backup, PASSPHRASE)

        val header = backup.toByteArray().decodeToString(endIndex = 64).lines()
        assertEquals("age-encryption.org/v1", header[0])
        assertTrue(header[1], header[1].matches(Regex("""-> scrypt [A-Za-z0-9+/]{22} 17""")))
    }

    @Test
    fun theZipHoldsTheManifestFirstThenTheDatabaseAndThePhotosFolder() = runTest {
        val backup = ByteArrayOutputStream()
        writer.write(backup, PASSPHRASE, FAST)
        val zip = ByteArrayOutputStream()
        Age.decryptStream(
            listOf(ScryptIdentity(PASSPHRASE.toByteArray())),
            backup.toByteArray().inputStream(),
            zip,
        )

        val names = ZipInputStream(zip.toByteArray().inputStream()).use { input ->
            generateSequence { input.nextEntry?.name }.toList()
        }
        assertEquals(listOf(MANIFEST_ENTRY, DATABASE_ENTRY, PHOTOS_DIRECTORY), names)
    }

    @Test
    fun aWrongPassphraseIsReportedAsSuch() = runTest {
        val backup = ByteArrayOutputStream()
        writer.write(backup, PASSPHRASE, FAST)

        assertThrows(BackupException.WrongPassphrase::class.java) {
            reader.read(backup.toByteArray().inputStream(), "not the passphrase")
        }
    }

    @Test
    fun aTruncatedBackupIsCorrupt() = runTest {
        val backup = ByteArrayOutputStream()
        writer.write(backup, PASSPHRASE, FAST)
        val bytes = backup.toByteArray()

        assertThrows(BackupException.Corrupt::class.java) {
            reader.read(bytes.copyOf(bytes.size - 10).inputStream(), PASSPHRASE)
        }
    }

    @Test
    fun aDamagedBackupIsCorrupt() = runTest {
        val backup = ByteArrayOutputStream()
        writer.write(backup, PASSPHRASE, FAST)
        val bytes = backup.toByteArray()
        bytes[bytes.size - 100] = (bytes[bytes.size - 100].toInt() xor 1).toByte()

        assertThrows(BackupException.Corrupt::class.java) { reader.read(bytes.inputStream(), PASSPHRASE) }
    }

    @Test
    fun aFileThatIsNotAgeIsNotABackup() {
        assertThrows(BackupException.NotABackup::class.java) {
            reader.read("PK not an age file".byteInputStream(), PASSPHRASE)
        }
    }

    @Test
    fun aBackupWithANewerSchemaIsRefused() = runTest {
        val backup = ByteArrayOutputStream()
        writer.write(backup, PASSPHRASE, FAST)
        val olderOrbit = BackupReader(currentDbSchemaVersion = OrbitDatabase.VERSION - 1)

        assertThrows(BackupException.NewerVersion::class.java) {
            olderOrbit.read(backup.toByteArray().inputStream(), PASSPHRASE)
        }
    }

    @Test
    fun aBackupWithANewerFormatIsRefused() {
        val db = "db".toByteArray()
        val backup = craft(
            manifest(backupFormat = BACKUP_FORMAT + 1, entries = listOf(entry(DATABASE_ENTRY, db))),
            DATABASE_ENTRY to db,
        )

        assertThrows(BackupException.NewerVersion::class.java) { reader.read(backup, PASSPHRASE) }
    }

    @Test
    fun anEntryThatFailsItsHashIsCorrupt() {
        val backup = craft(
            manifest(entries = listOf(entry(DATABASE_ENTRY, "original".toByteArray()))),
            DATABASE_ENTRY to "tampered".toByteArray(),
        )

        assertThrows(BackupException.Corrupt::class.java) { reader.read(backup, PASSPHRASE) }
    }

    @Test
    fun anEntryOutsideTheManifestIsRefusedBeforeAnythingIsWritten() {
        val db = "db".toByteArray()
        val staging = temp.newFolder("staging")
        val backup = craft(
            manifest(entries = listOf(entry(DATABASE_ENTRY, db))),
            "../escape.txt" to "x".toByteArray(),
            DATABASE_ENTRY to db,
        )

        assertThrows(BackupException.Corrupt::class.java) { reader.read(backup, PASSPHRASE, staging) }
        assertTrue(staging.listFiles()!!.isEmpty())
        assertTrue(!File(staging.parentFile, "escape.txt").exists())
    }

    @Test
    fun aManifestListingAPathOutsideTheBackupIsNotABackup() {
        val backup = craft(manifest(entries = listOf(entry("../db/orbit.db", ByteArray(0)))))

        assertThrows(BackupException.NotABackup::class.java) { reader.read(backup, PASSPHRASE) }
    }

    @Test
    fun aMissingEntryIsCorrupt() {
        val backup = craft(manifest(entries = listOf(entry(DATABASE_ENTRY, "db".toByteArray()))))

        assertThrows(BackupException.Corrupt::class.java) { reader.read(backup, PASSPHRASE) }
    }

    @Test
    fun aScryptCostAbove18IsRefused() = runTest {
        val backup = ByteArrayOutputStream()
        writer.write(backup, PASSPHRASE, FAST)
        val claiming19 = backup.toByteArray().toString(Charsets.ISO_8859_1)
            .replaceFirst(Regex("""(-> scrypt \S+) 10\n"""), "$1 19\n")
            .toByteArray(Charsets.ISO_8859_1)

        assertThrows(BackupException::class.java) { reader.read(claiming19.inputStream(), PASSPHRASE) }
    }

    @Test
    fun aFailedWriteNeverLeavesAReadableBackup() = runTest {
        val partial = ByteArrayOutputStream()
        val failingAfter4K = object : OutputStream() {
            override fun write(b: Int) {
                if (partial.size() >= 4096) throw IOException("disk full")
                partial.write(b)
            }
        }
        database.addManyPeople()

        assertThrows(IOException::class.java) {
            runBlocking { writer.write(failingAfter4K, PASSPHRASE, FAST) }
        }
        assertThrows(BackupException::class.java) {
            reader.read(partial.toByteArray().inputStream(), PASSPHRASE)
        }
        assertTrue(workDir.listFiles()!!.isEmpty())
    }

    /** Adds enough rows that the snapshot is far bigger than the 4 KiB the failing stream accepts. */
    private suspend fun OrbitDatabase.addManyPeople() {
        val ringId = insertTestRing("Many", position = 1)
        repeat(500) { personDao().insert(Person(name = "Person $it with a longer name", ringId = ringId, createdAt = now)) }
    }

    private fun namesIn(databaseFile: File): List<String> =
        BundledSQLiteDriver().open(databaseFile.path).use { connection ->
            connection.prepare("SELECT name FROM person ORDER BY id").use { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }

    private fun manifest(backupFormat: Int = BACKUP_FORMAT, entries: List<BackupEntry>) = BackupManifest(
        backupFormat = backupFormat,
        dbSchemaVersion = OrbitDatabase.VERSION,
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

    /** A hand-built Backup, for shapes the writer never produces. */
    private fun craft(
        manifest: BackupManifest,
        vararg entries: Pair<String, ByteArray>,
        workFactor: Int = FAST,
    ): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(Age.encryptStream(listOf(ScryptRecipient(PASSPHRASE.toByteArray(), workFactor)), out)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(BackupJson.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
            }
        }
        return out.toByteArray().inputStream()
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery staple"

        /** A low scrypt cost (log2 N = 10) so these tests stay fast. */
        const val FAST = 10
    }
}
