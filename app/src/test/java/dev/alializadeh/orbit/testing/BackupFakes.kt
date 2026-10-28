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
 * [lose] simulates the key being gone (a sealed passphrase can't be opened any more).
 */
class FakePassphraseCipher : PassphraseCipher {
    private var keyLost = false

    fun lose() {
        keyLost = true
    }

    override fun seal(passphrase: String): String =
        "sealed:" + Base64.getEncoder().encodeToString(passphrase.reversed().toByteArray())

    override fun open(sealed: String): String {
        if (keyLost || !sealed.startsWith("sealed:")) throw GeneralSecurityException("Key gone")
        return Base64.getDecoder().decode(sealed.removePrefix("sealed:")).decodeToString().reversed()
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
