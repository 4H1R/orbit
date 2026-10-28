package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.TestDatabases
import dev.alializadeh.orbit.data.insertTestRing
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.testing.TestAppLocales
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Backups interoperate with the real `age` CLI in both directions (ADR 0004: anyone can open a
 * Backup with `age -d`, even without Orbit).
 *
 * `age` reads passphrases from a terminal only, so the test drives it through the official
 * `age-plugin-batchpass` plugin (age ≥ 1.3), which takes the passphrase from `AGE_PASSPHRASE` and
 * reads and writes ordinary `scrypt` stanzas.
 *
 * Skipped unless the CLI is found: set `ORBIT_AGE_CLI` to the `age` binary, or put it on PATH, with
 * `age-plugin-batchpass` in the same folder.
 */
class AgeCliInteropTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var age: File
    private lateinit var ageDir: File
    private lateinit var database: OrbitDatabase

    @Before
    fun setUp() {
        val cli = findAgeCli()
        assumeTrue("age CLI not found: set ORBIT_AGE_CLI or put age on PATH", cli != null)
        age = cli!!.absoluteFile
        ageDir = requireNotNull(age.parentFile)
        assumeTrue(
            "age-plugin-batchpass not found next to $age",
            ageDir.listFiles().orEmpty().any { it.name.startsWith("age-plugin-batchpass") },
        )
        database = TestDatabases.onDisk(File(temp.root, "orbit.db"))
        runBlocking {
            database.personDao().insert(
                Person(name = "Sara", ringId = database.insertTestRing(), createdAt = Instant.EPOCH, photoFileName = PHOTO),
            )
        }
    }

    @Test
    fun theAgeCliDecryptsAnOrbitBackup() {
        val backup = writeBackup()

        val zip = File(temp.root, "backup.zip")
        val result = age("-d", "-j", "batchpass", "-o", zip.path, backup.path)

        assertEquals(result.output, 0, result.exitCode)
        ZipFile(zip).use { file ->
            val manifest = BackupJson.decodeFromString(
                BackupManifest.serializer(),
                file.getInputStream(file.getEntry(MANIFEST_ENTRY)).readBytes().decodeToString(),
            )
            val database = file.getInputStream(file.getEntry(DATABASE_ENTRY)).readBytes()
            assertEquals(manifest.entries.first { it.path == DATABASE_ENTRY }.sha256, database.sha256Hex())
            // Photos are STORED entries; a standard ZIP reader gets them back byte for byte.
            val photo = file.getInputStream(file.getEntry(PHOTOS_DIRECTORY + PHOTO)).readBytes()
            assertEquals(PHOTO_BYTES.toList(), photo.toList())
        }
    }

    @Test
    fun orbitReadsABackupEncryptedByTheAgeCli() {
        val zip = File(temp.root, "backup.zip")
        assertEquals(0, age("-d", "-j", "batchpass", "-o", zip.path, writeBackup().path).exitCode)

        val reencrypted = File(temp.root, "from-cli.age")
        val result = age("-e", "-j", "batchpass", "-o", reencrypted.path, zip.path)
        assertEquals(result.output, 0, result.exitCode)

        val manifest = reencrypted.inputStream().use {
            BackupReader(OrbitDatabase.VERSION).read(it, PASSPHRASE)
        }
        assertEquals(listOf(DATABASE_ENTRY, PHOTOS_DIRECTORY + PHOTO), manifest.entries.map(BackupEntry::path))
    }

    @Test
    fun theAgeCliRejectsTheWrongPassphrase() {
        val result = age("-d", "-j", "batchpass", "-o", File(temp.root, "x.zip").path, writeBackup().path, passphrase = "wrong")

        assertNotEquals(0, result.exitCode)
    }

    private fun writeBackup(): File {
        val file = File(temp.root, "orbit-backup.age")
        val writer = BackupWriter(
            snapshotter = RoomDatabaseSnapshotter(database),
            clock = Clock.fixed(Instant.parse("2026-10-28T08:30:00Z"), ZoneOffset.UTC),
            appVersion = AppVersion("0.1.0", 1),
            appLocales = TestAppLocales(),
            workDir = temp.newFolder(),
            photosDir = File(temp.root, "photos").apply {
                mkdirs()
                File(this, PHOTO).writeBytes(PHOTO_BYTES)
            },
        )
        runBlocking { writer.write(file.outputStream(), PASSPHRASE) } // The real cost, logN 17.
        return file
    }

    private class Result(val exitCode: Int, val output: String)

    private fun age(vararg args: String, passphrase: String = PASSPHRASE): Result {
        val process = ProcessBuilder(listOf(age.path) + args)
            .redirectErrorStream(true)
            .apply {
                val env = environment()
                env["AGE_PASSPHRASE"] = passphrase
                env["AGE_PASSPHRASE_WORK_FACTOR"] = BACKUP_WRITE_WORK_FACTOR.toString()
                // age finds plugins on PATH.
                val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
                env[pathKey] = ageDir.path + File.pathSeparator + env[pathKey].orEmpty()
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "age timed out" }
        return Result(process.exitValue(), output)
    }

    private fun ByteArray.sha256Hex(): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(this).toHexString()

    private companion object {
        const val PASSPHRASE = "orbit interop passphrase"
        const val PHOTO = "0b6f3c1e-photo.jpg"
        val PHOTO_BYTES = ByteArray(5000) { (it * 31 % 251).toByte() }

        fun findAgeCli(): File? {
            System.getenv("ORBIT_AGE_CLI")?.takeIf(String::isNotBlank)?.let { path ->
                return File(path).takeIf(File::canExecute)
            }
            val names = listOf("age", "age.exe")
            return System.getenv("PATH").orEmpty().split(File.pathSeparator)
                .flatMap { dir -> names.map { File(dir, it) } }
                .firstOrNull { it.isFile && it.canExecute() }
        }
    }
}
