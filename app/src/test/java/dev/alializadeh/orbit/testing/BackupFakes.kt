package dev.alializadeh.orbit.testing

import dev.alializadeh.orbit.AppLocales
import dev.alializadeh.orbit.backup.BackupDocuments
import dev.alializadeh.orbit.backup.PassphraseCipher
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * Stands in for the Android Keystore: reversible, but never stores the plain text as it is.
 * [lose] simulates the key being gone (what it sealed can't be opened any more); [failing] makes
 * every call throw like a broken Keystore does (a runtime `ProviderException`).
 */
class FakePassphraseCipher : PassphraseCipher {
    private var key = 0
    var resets = 0
        private set

    /** Thrown by every call while set. */
    var failing: RuntimeException? = null

    fun lose() {
        key++
    }

    override fun seal(passphrase: String): String {
        failing?.let { throw it }
        return "sealed:$key:" + Base64.getEncoder().encodeToString(passphrase.reversed().toByteArray())
    }

    override fun open(sealed: String): String {
        failing?.let { throw it }
        val parts = sealed.split(":")
        if (parts.size != 3 || parts[0] != "sealed" || parts[1] != key.toString()) throw GeneralSecurityException("Key gone")
        return Base64.getDecoder().decode(parts[2]).decodeToString().reversed()
    }

    override fun reset() {
        resets++
        key++
    }
}

/** The per-app locale, in memory; [applied] records every change. */
class TestAppLocales(var tags: String? = null) : AppLocales {
    val applied = mutableListOf<String?>()

    override fun languageTags(): String? = tags

    override fun apply(tags: String?) {
        this.tags = tags
        applied += tags
    }
}

/** "Documents" that are plain files: the URI is the file's path. */
class FileBackupDocuments : BackupDocuments {
    val deleted = mutableListOf<String>()

    /** Fails writes after this many bytes (null: never). */
    var failAfterBytes: Long? = null

    override fun openOutput(uri: String): OutputStream {
        val out = File(uri).outputStream()
        val limit = failAfterBytes ?: return out
        return object : OutputStream() {
            var written = 0L

            override fun write(b: Int) {
                if (written >= limit) throw IOException("disk full")
                out.write(b)
                written++
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (written + len > limit) throw IOException("disk full")
                out.write(b, off, len)
                written += len
            }

            override fun close() = out.close()
        }
    }

    override fun openInput(uri: String): InputStream = File(uri).inputStream()

    override fun delete(uri: String) {
        deleted += uri
        File(uri).delete()
    }
}
