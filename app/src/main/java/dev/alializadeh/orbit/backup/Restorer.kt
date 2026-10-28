package dev.alializadeh.orbit.backup

import androidx.room3.PooledConnection
import androidx.room3.Room
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.alializadeh.orbit.AppLocales
import dev.alializadeh.orbit.data.DataVersion
import dev.alializadeh.orbit.data.LastBackup
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.data.orbitSetup
import dev.alializadeh.orbit.data.queryLong
import dev.alializadeh.orbit.data.queryStrings
import dev.alializadeh.orbit.language.OrbitLanguage
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** A Backup decrypted and validated into staging, ready to replace the device's data. */
class StagedBackup internal constructor(
    internal val directory: File,
    val manifest: BackupManifest,
    /** Counted from the staged database itself. */
    val people: Int,
    val interactions: Int,
    val photos: Int,
    internal val passphrase: String,
)

/**
 * Runs after every Restore, once the restored data is in place (also when a Restore interrupted by
 * a crash is finished on the next start). Slice 6 binds its reminder `reconcile()` here with
 * `@IntoSet`: alarms aren't part of a Backup, so they must be planned again from the restored data.
 */
fun interface RestoreListener {
    suspend fun onRestored()
}

/** "Restored 84 people": waits here until the app shell shows it, even across an activity recreation. */
@Singleton
class RestoreNotices @Inject constructor() {
    private val restored = MutableStateFlow<Int?>(null)

    /** How many People the last Restore brought, until [consume]d. */
    val restoredPeople: StateFlow<Int?> = restored.asStateFlow()

    fun announce(people: Int) {
        restored.value = people
    }

    fun consume() {
        restored.value = null
    }
}

/** Opens a staged database file the way the app would: migrating it and validating its schema. */
fun interface StagedDatabaseCheck {
    /** @throws Exception when Room can't open it as an Orbit database of this version. */
    suspend fun openAndMigrate(file: File)
}

class RoomStagedDatabaseCheck @Inject constructor() : StagedDatabaseCheck {
    override suspend fun openAndMigrate(file: File) {
        val database = Room.databaseBuilder<OrbitDatabase>(file.absolutePath).orbitSetup(seed = false).build()
        try {
            // The first connection runs the migrations and Room's schema validation.
            database.useWriterConnection { it.usePrepared("SELECT COUNT(*) FROM person") { statement -> statement.step() } }
        } finally {
            database.close()
        }
    }
}

/**
 * Restore: replaces everything on the device with a Backup, atomically.
 *
 * 1. [stage]: decrypt and verify the whole Backup into an empty staging folder (manifest versions,
 *    every hash, the ZIP's entry names against the manifest, so nothing escapes the folder), then
 *    `PRAGMA integrity_check` and `foreign_key_check` on the staged database, then open it with Room
 *    (migrating an older schema, validating it). The device's data is untouched so far.
 * 2. [apply]: copy the staged photos in beside the current ones (new names only), then replace every
 *    table's rows in **one SQLite transaction** on Room's own writer connection, with the staged
 *    database attached. That transaction is the commit point: before it the old data stands, after it
 *    the new, and SQLite's journal makes a crash in between roll back. Room's invalidation tracker
 *    then refreshes every screen. Finally the photos the data no longer references are deleted.
 * 3. A journal file brackets step 2, so [recoverInterrupted] can finish (or undo the photo copy of)
 *    a Restore that a crash interrupted.
 *
 * Afterwards: "last Backup" is the Backup's own date and the data counts as unchanged (a Restore
 * counts as a Backup), the typed passphrase becomes the remembered one, the [RestoreListener]s run,
 * the Backup's language is applied, and [RestoreNotices] announces it.
 */
class Restorer(
    private val database: OrbitDatabase,
    private val reader: BackupReader,
    private val preferences: PreferencesRepository,
    private val passphrases: BackupPassphraseStore,
    private val appLocales: AppLocales,
    private val listeners: Set<RestoreListener>,
    private val notices: RestoreNotices,
    private val stagedDatabaseCheck: StagedDatabaseCheck,
    /** App-private and excluded from Android backups: staging folders and the journal. */
    private val workDir: File,
    private val photosDir: File,
) {

    private val dao = database.backupStatusDao()
    private val journalFile = File(workDir, JOURNAL)

    /**
     * Decrypts, verifies and validates the Backup from [open] into staging.
     *
     * @throws BackupException when the passphrase is wrong, the file isn't an Orbit Backup, it's
     *   damaged, it's from a newer Orbit, or it needs more memory than this phone has.
     */
    suspend fun stage(open: () -> InputStream, passphrase: String): StagedBackup = withContext(Dispatchers.IO) {
        val normalised = PassphraseRules.normalise(passphrase)
        val directory = File(workDir, STAGING_PREFIX + UUID.randomUUID())
        check(directory.mkdirs()) { "Can't create $directory" }
        try {
            val manifest = open().use { reader.read(it, normalised, extractTo = directory) }
            val databaseFile = File(directory, DATABASE_ENTRY)
            checkStagedDatabase(databaseFile, manifest)
            val contents = DatabaseFileContents.read(databaseFile)
            StagedBackup(
                directory = directory,
                manifest = manifest,
                people = contents.people,
                interactions = contents.interactions,
                photos = stagedPhotos(directory).size,
                passphrase = normalised,
            )
        } catch (t: Throwable) {
            directory.deleteRecursively()
            throw t
        }
    }

    /** Throws away a staged Backup that won't be restored. */
    fun discard(staged: StagedBackup) {
        staged.directory.deleteRecursively()
    }

    /** Replaces the device's data with [staged]; returns how many People it brought. */
    suspend fun apply(staged: StagedBackup): Int = withContext(Dispatchers.IO + NonCancellable) {
        val restoredPhotos = stagedPhotos(staged.directory)
        photosDir.mkdirs()
        for (photo in restoredPhotos) copyIn(photo)
        val journal = RestoreJournal(
            phase = RestoreJournal.Phase.Swapping,
            stagingDirectory = staged.directory.path,
            previousPhotos = dao.photoFileNames(),
            restoredPhotos = restoredPhotos.map(File::getName),
            createdAtEpochMillis = staged.manifest.createdAt.toEpochMilli(),
            previousDataVersion = dao.dataVersion(),
            dataVersion = DatabaseFileContents.read(File(staged.directory, DATABASE_ENTRY)).dataVersion,
            locale = staged.manifest.locale,
            people = staged.people,
        )
        writeJournal(journal)
        try {
            swapDatabase(File(staged.directory, DATABASE_ENTRY))
        } catch (t: Throwable) {
            // The transaction rolled back: the old data stands. Take the copied photos back out.
            rollBack(journal)
            throw t
        }
        val committed = journal.copy(phase = RestoreJournal.Phase.Committed)
        writeJournal(committed)
        finish(committed, passphrase = staged.passphrase)
        staged.people
    }

    /**
     * At app start: finishes a Restore that a crash interrupted after its commit, or takes back the
     * photos copied for one interrupted before it, then removes stale staging folders.
     */
    suspend fun recoverInterrupted(startedAtMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        readJournal()?.let { journal ->
            val live = dao.dataVersion()
            val committed = journal.phase == RestoreJournal.Phase.Committed ||
                (live == journal.dataVersion && live != journal.previousDataVersion)
            if (committed) finish(journal, passphrase = null) else rollBack(journal)
        }
        workDir.listFiles { file -> file.name.startsWith(STAGING_PREFIX) && file.lastModified() < startedAtMillis }
            ?.forEach { it.deleteRecursively() }
    }

    private suspend fun finish(journal: RestoreJournal, passphrase: String?) {
        val dataVersion = dao.dataVersion()
        preferences.updateDeviceState {
            BackupRules.passphraseTyped(
                it.copy(
                    lastBackup = LastBackup(journal.createdAtEpochMillis, dataVersion),
                    backupNudgeHiddenUntilEpochDay = null,
                ),
            )
        }
        if (passphrase != null) passphrases.remember(passphrase)
        val referenced = dao.photoFileNames().toSet()
        (journal.previousPhotos + journal.restoredPhotos)
            .filterNot { it in referenced }
            .forEach { File(photosDir, it).delete() }
        for (listener in listeners) listener.onRestored()
        File(journal.stagingDirectory).deleteRecursively()
        journalFile.delete()
        notices.announce(journal.people)
        // Last: it recreates the activity.
        appLocales.apply(languageTags(journal.locale))
    }

    private suspend fun rollBack(journal: RestoreJournal) {
        val referenced = dao.photoFileNames().toSet()
        journal.restoredPhotos
            .filterNot { it in referenced || it in journal.previousPhotos }
            .forEach { File(photosDir, it).delete() }
        File(journal.stagingDirectory).deleteRecursively()
        journalFile.delete()
    }

    /** Integrity, foreign keys, the version the manifest claims, then Room's own migration and validation. */
    private suspend fun checkStagedDatabase(file: File, manifest: BackupManifest) {
        try {
            BundledSQLiteDriver().open(file.path).use { connection ->
                val integrity = connection.queryStrings("PRAGMA integrity_check")
                if (integrity != listOf("ok")) throw BackupException.Corrupt("integrity_check: $integrity")
                val violations = connection.queryStrings("SELECT `table` FROM pragma_foreign_key_check")
                if (violations.isNotEmpty()) throw BackupException.Corrupt("Foreign key violations in $violations")
                val version = connection.queryLong("PRAGMA user_version").toInt()
                if (version != manifest.dbSchemaVersion) {
                    throw BackupException.Corrupt("Schema $version, but the manifest says ${manifest.dbSchemaVersion}")
                }
            }
        } catch (e: SQLiteException) {
            throw BackupException.Corrupt("The backup's database can't be read", e)
        }
        try {
            stagedDatabaseCheck.openAndMigrate(file)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException.NotABackup("Not an Orbit database: ${e.message}", e)
        }
    }

    /**
     * Replaces every table's rows with the staged database's, in one transaction on Room's writer
     * connection. Foreign keys are checked at commit; the data version is copied last, so the bump
     * triggers fired by the copy don't count.
     */
    private suspend fun swapDatabase(staged: File) {
        database.useWriterConnection { connection ->
            connection.usePrepared("ATTACH DATABASE ? AS staged") {
                it.bindText(1, staged.absolutePath)
                it.step()
            }
            try {
                connection.immediateTransaction {
                    usePrepared("PRAGMA defer_foreign_keys = ON") { it.step() }
                    val tables = insertOrder(this, strings("SELECT name FROM main.sqlite_master WHERE type = 'table'").filter(::isDataTable))
                    val stagedTables = strings("SELECT name FROM staged.sqlite_master WHERE type = 'table'").toSet()
                    val missing = tables - stagedTables
                    if (missing.isNotEmpty()) throw BackupException.Corrupt("The backup lacks tables $missing")
                    // Children first, so no RESTRICT foreign key stops a delete.
                    for (table in tables.asReversed()) usePrepared("DELETE FROM main.`$table`") { it.step() }
                    usePrepared("DELETE FROM main.`${DataVersion.TABLE}`") { it.step() }
                    for (table in tables + DataVersion.TABLE) {
                        // Named columns: a migrated table may order them differently from a new one.
                        val columns = strings("SELECT name FROM pragma_table_info('$table', 'main')")
                            .joinToString(", ") { "`$it`" }
                        usePrepared("INSERT INTO main.`$table` ($columns) SELECT $columns FROM staged.`$table`") { it.step() }
                    }
                }
            } finally {
                connection.usePrepared("DETACH DATABASE staged") { it.step() }
            }
        }
    }

    /** Parents before children, following the foreign keys. */
    private suspend fun insertOrder(connection: PooledConnection, tables: List<String>): List<String> {
        val parents = tables.associateWith { table ->
            connection.strings("SELECT `table` FROM pragma_foreign_key_list('$table', 'main')").toSet() - table
        }
        val ordered = mutableListOf<String>()
        fun visit(table: String, path: Set<String>) {
            if (table in ordered || table in path || table !in parents) return
            parents.getValue(table).forEach { visit(it, path + table) }
            ordered += table
        }
        tables.sorted().forEach { visit(it, emptySet()) }
        return ordered
    }

    private fun isDataTable(name: String) =
        name != DataVersion.TABLE && !name.startsWith("sqlite_") && !name.startsWith("room_") && !name.startsWith("android_")

    private suspend fun PooledConnection.strings(sql: String): List<String> = usePrepared(sql) { statement ->
        buildList { while (statement.step()) add(statement.getText(0)) }
    }

    private fun stagedPhotos(directory: File): List<File> =
        File(directory, PHOTOS_DIRECTORY).listFiles()?.filter(File::isFile)?.sortedBy(File::getName).orEmpty()

    /** Copies a staged photo in under its own name, unless it's already there (names are unique). */
    private fun copyIn(photo: File) {
        val target = File(photosDir, photo.name)
        if (target.exists()) return
        val partial = File(photosDir, photo.name + ".restoring")
        photo.copyTo(partial, overwrite = true)
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun writeJournal(journal: RestoreJournal) {
        workDir.mkdirs()
        val partial = File(workDir, "$JOURNAL.partial")
        partial.writeText(BackupJson.encodeToString(RestoreJournal.serializer(), journal))
        Files.move(partial.toPath(), journalFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun readJournal(): RestoreJournal? {
        if (!journalFile.isFile) return null
        return try {
            BackupJson.decodeFromString(RestoreJournal.serializer(), journalFile.readText())
        } catch (_: SerializationException) {
            journalFile.delete()
            null
        } catch (_: IllegalArgumentException) {
            journalFile.delete()
            null
        }
    }

    /** Only Orbit's own languages are applied; anything else follows the system. */
    private fun languageTags(locale: String?): String? =
        locale?.substringBefore(',')?.takeIf(String::isNotBlank)?.let { OrbitLanguage.of(Locale.forLanguageTag(it)).tag }

    private companion object {
        const val JOURNAL = "restore-journal.json"
        const val STAGING_PREFIX = "staging-"
    }
}

/** Brackets the swap, so a crash in the middle can be finished or undone on the next start. */
@Serializable
internal data class RestoreJournal(
    val phase: Phase,
    val stagingDirectory: String,
    /** The photos the data referenced before. */
    val previousPhotos: List<String>,
    /** The photos copied in from the Backup. */
    val restoredPhotos: List<String>,
    val createdAtEpochMillis: Long,
    val previousDataVersion: Long,
    /** The Backup's data version: the live one once the swap has committed. */
    val dataVersion: Long,
    val locale: String?,
    val people: Int,
) {
    enum class Phase { Swapping, Committed }
}
