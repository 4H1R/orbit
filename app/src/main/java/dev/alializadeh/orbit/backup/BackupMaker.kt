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
 * Orbit never deletes or rotates finished Backup files.
 */
class BackupMaker @Inject constructor(
    private val writer: BackupWriter,
    private val reader: BackupReader,
    private val documents: BackupDocuments,
    private val preferences: PreferencesRepository,
) {

    enum class Stage { Writing, Verifying }

    suspend fun backUp(
        uri: String,
        passphrase: String,
        onStage: (Stage) -> Unit = {},
        workFactor: Int = BACKUP_WRITE_WORK_FACTOR,
    ): BackupOutcome {
        val job = currentCoroutineContext().job
        try {
            onStage(Stage.Writing)
            val written = withContext(Dispatchers.IO) {
                writer.write(CancellableOutputStream(documents.openOutput(uri), job), passphrase, workFactor)
            }
            onStage(Stage.Verifying)
            val read = withContext(Dispatchers.IO) {
                CancellableInputStream(documents.openInput(uri), job).use { reader.read(it, passphrase) }
            }
            if (read != written.manifest) throw BackupException.Corrupt("The saved backup reads back differently")
            preferences.updateDeviceState {
                it.copy(
                    lastBackup = LastBackup(written.manifest.createdAt.toEpochMilli(), written.dataVersion),
                    backupCount = it.backupCount + 1,
                )
            }
            return BackupOutcome(people = written.manifest.people, photos = written.manifest.photos)
        } catch (t: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { documents.delete(uri) }
            throw t
        }
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
