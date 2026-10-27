package dev.alializadeh.orbit.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kage.crypto.scrypt.ScryptIdentity
import kage.errors.IncorrectIdentityException
import kage.errors.InvalidIdentityException
import kage.errors.ParseException
import kotlinx.serialization.SerializationException

/**
 * Reads and verifies Backups: decrypts to the very end (so age has authenticated every chunk),
 * checks the manifest, the entry names, sizes and SHA-256 hashes, and refuses Backups from a newer
 * Orbit.
 */
class BackupReader(private val currentDbSchemaVersion: Int) {

    /**
     * Verifies the Backup in [input] with [passphrase] and returns its manifest. With [extractTo]
     * (an empty staging folder), the entries are also written there under their ZIP paths.
     *
     * @throws BackupException if the Backup can't be opened or fails verification.
     */
    fun read(input: InputStream, passphrase: String, extractTo: File? = null): BackupManifest {
        val identity = ScryptIdentity(passphrase.toByteArray(Charsets.UTF_8), BACKUP_MAX_READ_WORK_FACTOR)
        try {
            AgeDecryptingInputStream(listOf(identity), input).use { plaintext ->
                val manifest = readZip(ZipInputStream(plaintext), extractTo)
                // Read to the end: only then has age authenticated the final chunk.
                val buffer = ByteArray(BUFFER_SIZE)
                do {
                    val n = plaintext.read(buffer)
                } while (n >= 0)
                return manifest
            }
        } catch (e: AgeDecryptionException) {
            throw e.cause.toBackupException()
        } catch (e: ZipException) {
            throw BackupException.NotABackup("Not a ZIP inside the age file", e)
        }
    }

    private fun readZip(zip: ZipInputStream, extractTo: File?): BackupManifest {
        val first = zip.nextEntry
            ?: throw BackupException.NotABackup("The backup is empty")
        if (first.name != MANIFEST_ENTRY) {
            throw BackupException.NotABackup("The first entry is ${first.name}, not $MANIFEST_ENTRY")
        }
        val manifest = parseManifest(zip.readCapped(MAX_MANIFEST_BYTES))
        val expected = manifest.entries.associateBy(BackupEntry::path)
        val seen = mutableSetOf<String>()

        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) {
                if (entry.name != PHOTOS_DIRECTORY) {
                    throw BackupException.Corrupt("Unexpected folder ${entry.name}")
                }
                continue
            }
            val spec = expected[entry.name]
                ?: throw BackupException.Corrupt("Unexpected entry ${entry.name}")
            if (!seen.add(entry.name)) throw BackupException.Corrupt("Duplicate entry ${entry.name}")
            val target = extractTo?.let { File(it, entry.name).apply { parentFile?.mkdirs() } }
            val (size, sha256) = target?.outputStream().use { out -> zip.copyHashing(out) }
            if (size != spec.size || sha256 != spec.sha256) {
                throw BackupException.Corrupt("${entry.name} doesn't match its manifest hash")
            }
        }
        val missing = expected.keys - seen
        if (missing.isNotEmpty()) throw BackupException.Corrupt("Missing entries: $missing")
        return manifest
    }

    private fun parseManifest(bytes: ByteArray): BackupManifest {
        val manifest = try {
            BackupJson.decodeFromString(BackupManifest.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            throw BackupException.NotABackup("Unreadable manifest", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException.NotABackup("Unreadable manifest", e)
        }
        if (manifest.backupFormat > BACKUP_FORMAT || manifest.dbSchemaVersion > currentDbSchemaVersion) {
            throw BackupException.NewerVersion(
                "Backup format ${manifest.backupFormat}, schema ${manifest.dbSchemaVersion}; " +
                    "this Orbit reads up to $BACKUP_FORMAT, schema $currentDbSchemaVersion",
            )
        }
        val paths = manifest.entries.map(BackupEntry::path)
        if (paths.any { !isAllowedEntryPath(it) } || paths.toSet().size != paths.size) {
            throw BackupException.NotABackup("The manifest lists invalid entries: $paths")
        }
        if (DATABASE_ENTRY !in paths) throw BackupException.NotABackup("The manifest has no database")
        return manifest
    }

    private fun InputStream.readCapped(limit: Int): ByteArray {
        val bytes = readBytes(limit + 1)
        if (bytes.size > limit) throw BackupException.NotABackup("The manifest is too large")
        return bytes
    }

    /** Reads at most [count] bytes (fewer at the end of the stream). */
    private fun InputStream.readBytes(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val n = read(buffer, filled, count - filled)
            if (n < 0) break
            filled += n
        }
        return buffer.copyOf(filled)
    }

    /** Copies the current entry to [out] (if any) and returns its size and SHA-256. */
    private fun InputStream.copyHashing(out: OutputStream?): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var size = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            out?.write(buffer, 0, n)
            size += n
        }
        return size to digest.digest().toHexString()
    }

    private fun Throwable?.toBackupException(): BackupException = when (this) {
        // The passphrase didn't unwrap the file key, or the file isn't for a passphrase.
        is InvalidIdentityException, is IncorrectIdentityException ->
            BackupException.WrongPassphrase(this)
        is ParseException -> BackupException.NotABackup("Not an age file", this)
        else -> BackupException.Corrupt("Decryption failed", this)
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_MANIFEST_BYTES = 1024 * 1024
    }
}
