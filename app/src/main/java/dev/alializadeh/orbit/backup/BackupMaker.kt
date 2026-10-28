package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.data.LastBackup
import dev.alializadeh.orbit.data.PreferencesRepository
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** What Back up now did, for the result sheet. */
data class BackupOutcome(val people: Int, val photos: Int)

/**
 * Back up now, from the moment the Save picker returns a document: write the Backup into it, then
 * re-open it and decrypt it to the end checking every hash. Only then is it a Backup: "last
 * Backup" and the Backup count move on.
 *
 * On any failure, or a cancel, the partial document is deleted and "last Backup" stays as it was.
 * Recording the Backup is the last step and can't be cancelled; once it's done the file is never
 * deleted, so the file and "last Backup" always agree. The document is also noted in device-bound
 * state while it's written: if the process dies first, [cleanUpInterrupted] deletes it at the next
 * start. Orbit never deletes or rotates finished Backup files.
 */
class BackupMaker @Inject constructor(
    private val writer: BackupWriter,
    private val reader: BackupReader,
    private val documents: BackupDocuments,
    private val preferences: PreferencesRepository,
) {

    enum class Stage { Writing, Verifying }

    /**
     * @throws BackupException.NeedsMoreMemory when scrypt or the encryption ran out of memory.
     */
    suspend fun backUp(
        uri: String,
        passphrase: String,
        onStage: (Stage) -> Unit = {},
        workFactor: Int = BACKUP_WRITE_WORK_FACTOR,
    ): BackupOutcome {
        val job = currentCoroutineContext().job
        var recorded = false
        try {
            withContext(NonCancellable + Dispatchers.IO) {
                documents.holdAccess(uri)
                preferences.updateDeviceState { it.copy(backupInProgressUri = uri) }
            }
            onStage(Stage.Writing)
            val written = withContext(Dispatchers.IO) {
                writer.write(CancellableOutputStream(documents.openOutput(uri), job), passphrase, workFactor)
            }
            onStage(Stage.Verifying)
            val read = withContext(Dispatchers.IO) {
                CancellableInputStream(documents.openInput(uri), job).use { reader.read(it, passphrase) }
            }
            if (read != written.manifest) throw BackupException.Corrupt("The saved backup reads back differently")
            // The commit point: from here on the file is a Backup and is never deleted.
            withContext(NonCancellable) {
                preferences.updateDeviceState {
                    it.copy(
                        lastBackup = LastBackup(written.manifest.createdAt.toEpochMilli(), written.dataVersion),
                        backupCount = it.backupCount + 1,
                        backupInProgressUri = null,
                    )
                }
                recorded = true
                withContext(Dispatchers.IO) { documents.releaseAccess(uri) }
            }
            return BackupOutcome(people = written.manifest.people, photos = written.manifest.photos)
        } catch (t: Throwable) {
            if (recorded) throw t
            withContext(NonCancellable + Dispatchers.IO) {
                documents.delete(uri)
                documents.releaseAccess(uri)
                runCatching { preferences.updateDeviceState { it.copy(backupInProgressUri = null) } }
            }
            // scrypt's memory is Java heap: a phone that can't give it fails here, not in a crash.
            if (generateSequence(t) { it.cause }.any { it is OutOfMemoryError }) throw BackupException.NeedsMoreMemory(t)
            throw t
        }
    }

    /**
     * At app start: deletes the document of a Backup whose process died before it was recorded, and
     * any plaintext snapshot left behind (made before [startedAtMillis]).
     */
    suspend fun cleanUpInterrupted(startedAtMillis: Long) = withContext(Dispatchers.IO) {
        writer.deleteStaleSnapshots(startedAtMillis)
        val uri = preferences.deviceState.first().backupInProgressUri ?: return@withContext
        documents.delete(uri)
        documents.releaseAccess(uri)
        preferences.updateDeviceState { if (it.backupInProgressUri == uri) it.copy(backupInProgressUri = null) else it }
    }

    /** Stops a blocking write as soon as the Backup is cancelled. */
    private class CancellableOutputStream(out: OutputStream, private val job: Job) : FilterOutputStream(out) {
        override fun write(b: Int) {
            ensureActive()
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            ensureActive()
            out.write(b, off, len)
        }

        private fun ensureActive() {
            if (!job.isActive) throw IOException("Backup cancelled", CancellationException())
        }
    }

    private class CancellableInputStream(input: InputStream, private val job: Job) : FilterInputStream(input) {
        override fun read(): Int {
            ensureActive()
            return super.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            ensureActive()
            return super.read(b, off, len)
        }

        private fun ensureActive() {
            if (!job.isActive) throw IOException("Backup cancelled", CancellationException())
        }
    }
}
