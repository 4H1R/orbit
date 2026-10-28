package dev.alializadeh.orbit.backup

import androidx.room3.PooledConnection
import androidx.room3.Room
import androidx.room3.Transactor
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.alializadeh.orbit.AppLocales
import dev.alializadeh.orbit.data.BackedUpPreferences
import dev.alializadeh.orbit.data.DataVersion
import dev.alializadeh.orbit.data.LastBackup
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.data.orbitSetup
import dev.alializadeh.orbit.data.queryLong
import dev.alializadeh.orbit.data.queryStrings
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.photo.FilesPhotoStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
    /**
     * Wraps the swap transaction (it must call [swap] once), to hold off work that mustn't run
     * while the data is replaced. Reminders wait for any `reconcile()` in progress here.
     */
    suspend fun aroundSwap(swap: suspend () -> Unit) {
        swap()
    }

    /** After the commit. A failure here is logged; the Restore still counts as done. */
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
 *    (migrating an older schema, validating it), then check the rows Orbit relies on (the single
 *    rows, photo file names). The device's data is untouched so far.
 * 2. [apply]: copy the staged photos in beside the current ones (new names only), then replace every
 *    table's rows in **one SQLite transaction** on Room's own writer connection, with the staged
 *    database attached. That transaction is the commit point: before it the old data stands, after it
 *    the new, and SQLite's journal makes a crash in between roll back. Room's invalidation tracker
 *    then refreshes every screen. Finally the photos the data no longer references are deleted.
 * 3. A journal file, written (and synced) before any photo is copied, brackets step 2, so
 *    [recoverInterrupted] can finish (or undo the photo copy of) a Restore that a crash or a power
 *    loss interrupted. The swap writes a new random restore token (`DataVersion`'s second row) in
 *    its transaction, which runs with `synchronous = FULL`: the token in the live database says,
 *    unambiguously and durably, whether the swap committed. Nothing old is deleted before that.
 *
 * Afterwards: "last Backup" is the Backup's own date and the data counts as unchanged (a Restore
 * counts as a Backup), the typed passphrase becomes the remembered one, the [RestoreListener]s run,
 * the Backup's language is applied, and [RestoreNotices] announces it. Each of these stands alone:
 * one failing is logged and the rest still run; the Restore has happened either way.
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
    /** Where failures that don't stop a Restore go (class names only: messages can hold user data). */
    private val log: (String) -> Unit = {},
) {

    private val dao = database.backupStatusDao()
    private val journalFile = File(workDir, JOURNAL)

    /**
     * Decrypts, verifies and validates the Backup from [open] into staging. Either returns the
     * staged Backup (the caller then [apply]s or [discard]s it) or leaves nothing behind, also when
     * the caller is cancelled while it runs.
     *
     * @throws BackupException when the passphrase is wrong, the file isn't an Orbit Backup, it's
     *   damaged, it's from a newer Orbit, or it needs more memory than this phone has.
     * @throws IOException when the file can't be read.
     */
    suspend fun stage(open: () -> InputStream, passphrase: String): StagedBackup {
        val normalised = PassphraseRules.normalise(passphrase)
        val directory = File(workDir, STAGING_PREFIX + UUID.randomUUID())
        try {
            return withContext(Dispatchers.IO) {
                if (!directory.mkdirs()) throw IOException("Can't create a staging folder")
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IllegalStateException) {
                    // A missing row, an unexpected shape: not something Orbit wrote.
                    throw BackupException.Corrupt("The backup's contents are inconsistent", e)
                } catch (e: IllegalArgumentException) {
                    throw BackupException.Corrupt("The backup's contents are inconsistent", e)
                }
            }
        } catch (t: Throwable) {
            // Also when a cancel lands after the work is done (withContext then drops the result).
            withContext(NonCancellable + Dispatchers.IO) { directory.deleteRecursively() }
            throw t
        }
    }

    /** Throws away a staged Backup that won't be restored. Blocking file I/O: not on the main thread. */
    fun discard(staged: StagedBackup) {
        staged.directory.deleteRecursively()
    }

    /**
     * Replaces the device's data with [staged]; returns how many People it brought.
     *
     * @throws Exception only when nothing changed (the swap didn't commit). Once it has, the Restore
     *   is done, whatever the steps after it do.
     */
    suspend fun apply(staged: StagedBackup): Int = withContext(Dispatchers.IO + NonCancellable) {
        val restoredPhotos = stagedPhotos(staged.directory)
        val databaseFile = File(staged.directory, DATABASE_ENTRY)
        val token = newRestoreToken()
        val journal = RestoreJournal(
            phase = RestoreJournal.Phase.Swapping,
            stagingDirectory = staged.directory.path,
            previousPhotos = dao.photoFileNames(),
            restoredPhotos = restoredPhotos.map(File::getName),
            createdAtEpochMillis = staged.manifest.createdAt.toEpochMilli(),
            previousDataVersion = dao.dataVersion(),
            dataVersion = DatabaseFileContents.read(databaseFile).dataVersion,
            locale = staged.manifest.locale,
            people = staged.people,
            restoreToken = token,
        )
        // First: no photo is copied in without a journal that can take it back out.
        writeJournal(journal)
        try {
            photosDir.mkdirs()
            for (photo in restoredPhotos) copyIn(photo)
            syncDirectory(photosDir)
            swapAroundListeners(databaseFile, token)
        } catch (t: Throwable) {
            val committed = try {
                hasCommitted(journal)
            } catch (_: Exception) {
                false
            }
            if (!committed) {
                // The transaction rolled back (or never ran): the old data stands.
                step("roll back") { rollBack(journal) }
                throw t
            }
            log("Restore: failure after the commit: ${t.javaClass.name}")
        }
        finish(journal.copy(phase = RestoreJournal.Phase.Committed), passphrase = staged.passphrase)
        staged.people
    }

    /**
     * At app start: finishes a Restore that a crash interrupted after its commit, or takes back the
     * photos copied for one interrupted before it, then removes stale staging folders and partial
     * copies. Never throws: whatever fails is logged, and the app starts.
     */
    suspend fun recoverInterrupted(startedAtMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        step("recovery") {
            readJournal()?.let { journal ->
                if (journal.phase == RestoreJournal.Phase.Committed || hasCommitted(journal)) {
                    finish(journal, passphrase = null)
                } else {
                    rollBack(journal)
                }
            }
        }
        step("stale staging") {
            workDir.listFiles { file -> file.name.startsWith(STAGING_PREFIX) && file.lastModified() < startedAtMillis }
                ?.forEach { it.deleteRecursively() }
            File(workDir, JOURNAL + PARTIAL_SUFFIX).delete()
        }
        step("partial photos") {
            photosDir.listFiles { file -> file.name.endsWith(PARTIAL_SUFFIX) && file.lastModified() < startedAtMillis }
                ?.forEach { it.delete() }
        }
    }

    /** Whether the swap of [journal] committed: the live restore token is the one it wrote. */
    private suspend fun hasCommitted(journal: RestoreJournal): Boolean {
        val token = journal.restoreToken
        if (token != null) return dao.restoreToken() == token
        // A journal written before restore tokens existed.
        val live = dao.dataVersion()
        return live == journal.dataVersion && live != journal.previousDataVersion
    }

    /**
     * Everything after the commit; safe to repeat (recovery runs it again after a crash). Each step
     * stands alone, so a failing one (DataStore, the Keystore, a listener) can't undo "restored".
     */
    private suspend fun finish(journal: RestoreJournal, passphrase: String?) {
        step("journal") { writeJournal(journal) }
        step("last backup") {
            preferences.updateDeviceState {
                BackupRules.passphraseTyped(
                    it.copy(
                        // The Backup's own data version: an edit made since still counts as a change.
                        lastBackup = LastBackup(journal.createdAtEpochMillis, journal.dataVersion),
                        backupNudgeHiddenUntilEpochDay = null,
                        restoredDuringFirstRun = it.restoredDuringFirstRun || !it.firstRunFinished,
                    ),
                )
            }
        }
        if (passphrase != null) {
            step("passphrase") {
                if (!passphrases.remember(passphrase)) log("Restore: the passphrase couldn't be remembered")
            }
        }
        step("photos") {
            val referenced = dao.photoFileNames().toSet()
            (journal.previousPhotos + journal.restoredPhotos)
                .filter { it !in referenced && FilesPhotoStore.isValidFileName(it) }
                .forEach { File(photosDir, it).delete() }
        }
        for (listener in listeners) step("a listener") { listener.onRestored() }
        step("clean up") {
            File(journal.stagingDirectory).deleteRecursively()
            journalFile.delete()
        }
        notices.announce(journal.people)
        // Last: it recreates the activity.
        step("language") { appLocales.apply(languageTags(journal.locale)) }
    }

    private suspend fun rollBack(journal: RestoreJournal) {
        val referenced = dao.photoFileNames().toSet()
        journal.restoredPhotos
            .filterNot { it in referenced || it in journal.previousPhotos }
            .filter { FilesPhotoStore.isValidFileName(it) }
            .forEach {
                File(photosDir, it).delete()
                File(photosDir, it + PARTIAL_SUFFIX).delete()
            }
        File(journal.stagingDirectory).deleteRecursively()
        journalFile.delete()
    }

    /** Runs [block]; a failure is logged (its class only) and the caller goes on. */
    private inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Restore: $name failed: ${e.javaClass.name}")
        }
    }

    /**
     * Integrity, foreign keys, the version the manifest claims, then Room's own migration and
     * validation, then the rows Orbit takes for granted.
     */
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BackupException.NotABackup("Not an Orbit database: ${e.javaClass.name}", e)
        }
        try {
            BundledSQLiteDriver().open(file.path).use { connection ->
                val singleRows = listOf(
                    "SELECT COUNT(*) FROM ${DataVersion.TABLE} WHERE id = ${DataVersion.SINGLE_ROW_ID}",
                    "SELECT COUNT(*) FROM backed_up_preferences WHERE id = ${BackedUpPreferences.SINGLE_ROW_ID}",
                )
                if (singleRows.any { connection.queryLong(it) != 1L }) throw BackupException.Corrupt("A single row is missing")
                // Room keeps only a photo's file name; anything else (a path, "..") could reach
                // outside the photos folder when it's loaded or deleted.
                val photoNames = connection.queryStrings("SELECT photoFileName FROM person WHERE photoFileName IS NOT NULL")
                if (!photoNames.all { FilesPhotoStore.isValidFileName(it) }) throw BackupException.Corrupt("An unexpected photo file name")
            }
        } catch (e: SQLiteException) {
            throw BackupException.Corrupt("The backup's database can't be read", e)
        }
    }

    /** The swap, inside every listener's [RestoreListener.aroundSwap]. */
    private suspend fun swapAroundListeners(staged: File, token: Long) {
        val swap = listeners.fold<RestoreListener, suspend () -> Unit>({ swapDatabase(staged, token) }) { inner, listener ->
            { listener.aroundSwap(inner) }
        }
        swap()
    }

    /**
     * Replaces every table's rows with the staged database's, in one transaction on Room's writer
     * connection. Foreign keys are checked at commit; the data version is copied last, so the bump
     * triggers fired by the copy don't count; then the new restore [token]. The commit is synced
     * (`synchronous = FULL`), so once this returns it survives a power loss.
     */
    private suspend fun swapDatabase(staged: File, token: Long) {
        database.useWriterConnection { connection ->
            val synchronous = connection.usePrepared("PRAGMA synchronous") { it.step(); it.getLong(0) }
            connection.usePrepared("PRAGMA synchronous = FULL") { it.step() }
            try {
                connection.usePrepared("ATTACH DATABASE ? AS staged") {
                    it.bindText(1, staged.absolutePath)
                    it.step()
                }
                try {
                    replaceRows(connection, token)
                } finally {
                    connection.usePrepared("DETACH DATABASE staged") { it.step() }
                }
            } finally {
                connection.usePrepared("PRAGMA synchronous = $synchronous") { it.step() }
            }
        }
    }

    private suspend fun replaceRows(connection: Transactor, token: Long) {
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
            usePrepared("INSERT OR REPLACE INTO main.`${DataVersion.TABLE}` (id, version) VALUES (?, ?)") {
                it.bindLong(1, DataVersion.RESTORE_TOKEN_ROW_ID.toLong())
                it.bindLong(2, token)
                it.step()
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

    /**
     * Copies a staged photo in under its own name, unless it's already there (names are unique),
     * synced before it's moved into place.
     */
    private fun copyIn(photo: File) {
        val target = File(photosDir, photo.name)
        if (target.exists()) return
        val partial = File(photosDir, photo.name + PARTIAL_SUFFIX)
        FileOutputStream(partial).use { out ->
            photo.inputStream().use { it.copyTo(out) }
            out.fd.sync()
        }
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    /** Writes the journal durably: synced, moved into place, and its folder synced. */
    private fun writeJournal(journal: RestoreJournal) {
        workDir.mkdirs()
        val partial = File(workDir, JOURNAL + PARTIAL_SUFFIX)
        FileOutputStream(partial).use { out ->
            out.write(BackupJson.encodeToString(RestoreJournal.serializer(), journal).toByteArray())
            out.fd.sync()
        }
        Files.move(partial.toPath(), journalFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory(workDir)
    }

    /** Makes renames in [directory] durable where the platform allows it (Linux, so Android, does). */
    private fun syncDirectory(directory: File) {
        try {
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
            // Not possible here (Windows can't open a folder): the files themselves were synced.
        }
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
        const val PARTIAL_SUFFIX = ".restoring"

        private val random = SecureRandom()

        /** A new random restore token, different from none-at-all (0 is never used). */
        fun newRestoreToken(): Long = generateSequence { random.nextLong() }.first { it != 0L }
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
    /** Written into the database by the swap's transaction: there once it has committed. */
    val restoreToken: Long? = null,
) {
    enum class Phase { Swapping, Committed }
}
