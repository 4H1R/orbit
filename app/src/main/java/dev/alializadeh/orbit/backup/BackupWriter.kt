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

/** A Backup that was written: its manifest, and the data version of the snapshot it holds. */
data class WrittenBackup(val manifest: BackupManifest, val dataVersion: Long)

/**
 * Writes Backups: a `VACUUM INTO` snapshot plus every photo it references, zipped with the
 * manifest and streamed through age (one scrypt recipient) straight into the destination. The only
 * plaintext on disk is the snapshot, in app-private [workDir], deleted as soon as the Backup is
 * written.
 *
 * Photos are never rewritten in place (a new photo gets a new file name), so the files the snapshot
 * references can't change underneath it. One that has vanished since is left out.
 */
class BackupWriter(
    private val snapshotter: DatabaseSnapshotter,
    private val clock: Clock,
    private val appVersion: AppVersion,
    private val appLocales: AppLocales,
    private val workDir: File,
    private val photosDir: File,
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
    ): WrittenBackup = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val snapshot = File(workDir, "$SNAPSHOT_PREFIX${UUID.randomUUID()}.db")
        try {
            snapshotter.snapshotTo(snapshot)
            val contents = DatabaseFileContents.read(snapshot)
            val photos = contents.photoFileNames
                .map { name -> PHOTOS_DIRECTORY + name to File(photosDir, name) }
                .filter { (path, file) -> isAllowedEntryPath(path) && file.isFile }
                .map { (path, file) -> PhotoToWrite(path, file, file.hashes()) }
            val databaseHashes = snapshot.hashes()
            val manifest = BackupManifest(
                backupFormat = BACKUP_FORMAT,
                dbSchemaVersion = contents.schemaVersion,
                appVersionName = appVersion.name,
                appVersionCode = appVersion.code,
                createdAt = clock.instant(),
                locale = appLocales.languageTags(),
                people = contents.people,
                interactions = contents.interactions,
                photos = photos.size,
                entries = listOf(BackupEntry(DATABASE_ENTRY, databaseHashes.size, databaseHashes.sha256)) +
                    photos.map { BackupEntry(it.path, it.hashes.size, it.hashes.sha256) },
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
            for (photo in photos) {
                // JPEGs don't compress: store them as they are.
                zip.putNextEntry(
                    ZipEntry(photo.path).apply {
                        method = ZipEntry.STORED
                        size = photo.hashes.size
                        compressedSize = photo.hashes.size
                        crc = photo.hashes.crc32
                    },
                )
                photo.file.inputStream().use { it.copyTo(zip, BUFFER_SIZE) }
                zip.closeEntry()
            }
            // Finishes the ZIP, then age's final chunk, then closes output.
            zip.close()
            WrittenBackup(manifest, contents.dataVersion)
        } catch (t: Throwable) {
            runCatching { output.close() }
            throw t
        } finally {
            snapshot.delete()
        }
    }

    /**
     * At app start: deletes plaintext snapshots a Backup left behind when its process died (any made
     * before [startedAtMillis]).
     */
    fun deleteStaleSnapshots(startedAtMillis: Long) {
        workDir.listFiles { file -> file.name.startsWith(SNAPSHOT_PREFIX) && file.lastModified() < startedAtMillis }
            ?.forEach { it.delete() }
    }

    private class PhotoToWrite(val path: String, val file: File, val hashes: FileHashes)

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val SNAPSHOT_PREFIX = "snapshot-"
    }
}
