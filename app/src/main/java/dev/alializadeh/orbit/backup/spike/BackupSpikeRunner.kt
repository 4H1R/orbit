package dev.alializadeh.orbit.backup.spike

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alializadeh.orbit.backup.BackupReader
import dev.alializadeh.orbit.backup.BackupWriter
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * TEMPORARY: the slice 0 Backup format spike, reached from Settings. It proves the whole path on a
 * real device: `VACUUM INTO` → ZIP → age → the Save picker's document → re-read and verify.
 *
 * Slice 5 replaces it with the real Back up now flow (Keystore-wrapped user passphrase, progress
 * sheet, "last Backup" bookkeeping). Delete this package then.
 */
class BackupSpikeRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: BackupWriter,
    private val reader: BackupReader,
) {

    /**
     * Writes a Backup to [uri] (truncating it), re-opens it and verifies it to the end. On any
     * failure the partial document is deleted.
     */
    suspend fun backUpAndVerify(uri: Uri) {
        val resolver = context.contentResolver
        try {
            withContext(Dispatchers.IO) {
                // "wt", not "w": some providers don't truncate on "w".
                val output = resolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Can't write to $uri")
                writer.write(output, PASSPHRASE)
                val input = resolver.openInputStream(uri)
                    ?: throw IOException("Can't read back $uri")
                input.use { reader.read(it, PASSPHRASE) }
            }
        } catch (t: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
            throw t
        }
    }

    companion object {
        /** Hard-coded for the spike only, so a spike Backup can be opened with `age -d`. */
        const val PASSPHRASE = "orbit-spike-passphrase"
    }
}
