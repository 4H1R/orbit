package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.AppLocales
import dev.alializadeh.orbit.AppVersion
import java.io.File
import java.io.OutputStream
import java.time.Clock
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kage.Age
import kage.crypto.scrypt.ScryptRecipient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes Backups: a `VACUUM INTO` snapshot, zipped with its manifest and streamed through age
 * (one scrypt recipient) straight into the destination. The only plaintext on disk is the snapshot,
 * in app-private [workDir], deleted as soon as the Backup is written.
 */
class BackupWriter(
    private val snapshotter: DatabaseSnapshotter,
    private val clock: Clock,
    private val appVersion: AppVersion,
    private val appLocales: AppLocales,
    private val workDir: File,
) {

    /**
     * Writes a Backup encrypted with [passphrase] to [output], and closes [output].
     *
     * If anything fails, [output] is closed without age's final chunk, so a partial file can never
     * be read as a complete Backup.
     */
    suspend fun write(
        output: OutputStream,
        passphrase: String,
        workFactor: Int = BACKUP_WRITE_WORK_FACTOR,
    ): BackupManifest = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val snapshot = File(workDir, "snapshot-${UUID.randomUUID()}.db")
        try {
            val schemaVersion = snapshotter.snapshotTo(snapshot)
            val manifest = BackupManifest(
                backupFormat = BACKUP_FORMAT,
                dbSchemaVersion = schemaVersion,
                appVersionName = appVersion.name,
                appVersionCode = appVersion.code,
                createdAt = clock.instant(),
                locale = appLocales.languageTags(),
                entries = listOf(BackupEntry(DATABASE_ENTRY, snapshot.length(), snapshot.sha256Hex())),
            )
            val recipient = ScryptRecipient(passphrase.toByteArray(Charsets.UTF_8), workFactor)
            val zip = ZipOutputStream(Age.encryptStream(listOf(recipient), output).buffered(BUFFER_SIZE))
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(BackupJson.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
            snapshot.inputStream().use { it.copyTo(zip, BUFFER_SIZE) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(PHOTOS_DIRECTORY))
            zip.closeEntry()
            // Finishes the ZIP, then age's final chunk, then closes output.
            zip.close()
            manifest
        } catch (t: Throwable) {
            runCatching { output.close() }
            throw t
        } finally {
            snapshot.delete()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
