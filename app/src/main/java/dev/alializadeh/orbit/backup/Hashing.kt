package dev.alializadeh.orbit.backup

import java.io.File
import java.security.MessageDigest

/** Lower-case hex SHA-256 of the file's bytes. */
internal fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().toHexString()
}
