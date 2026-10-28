package dev.alializadeh.orbit.backup

import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

/** A file's size, SHA-256 (lower-case hex, as the manifest records it) and CRC-32 (for ZIP). */
internal class FileHashes(val size: Long, val sha256: String, val crc32: Long)

/** Reads the file once for its [FileHashes]. */
internal fun File.hashes(): FileHashes {
    val digest = MessageDigest.getInstance("SHA-256")
    val crc = CRC32()
    var size = 0L
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            crc.update(buffer, 0, n)
            size += n
        }
    }
    return FileHashes(size, digest.digest().toHexString(), crc.value)
}

/** Lower-case hex SHA-256 of the file's bytes. */
internal fun File.sha256Hex(): String = hashes().sha256
