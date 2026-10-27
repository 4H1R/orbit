package dev.alializadeh.orbit.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.InflaterInputStream
import kage.Age
import kage.Identity
import kage.crypto.scrypt.ScryptIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Pins kage (and Orbit's decrypting stream around it) to the age spec's test vectors from C2SP/CCTV
 * (src/test/resources/age-testkit, see age-testkit-SOURCE.txt), as ADR 0004 requires.
 *
 * Each vector must decrypt exactly when it `expect`s success, and the plaintext released (even
 * before a payload failure) must match the vector's payload hash.
 *
 * Known kage deviation ([WITHHOLDS_FINAL_CHUNK]): when bytes follow the final STREAM chunk, kage
 * rejects the file but also withholds that final chunk's plaintext, where the spec expects it to be
 * released before the error. That's stricter, not weaker, and Orbit never uses plaintext from a
 * Backup that didn't decrypt cleanly to the end.
 */
@RunWith(Parameterized::class)
class AgeTestVectorsTest(private val name: String) {

    @Test
    fun decryptsExactlyAsTheSpecExpects() {
        val vector = Vector.parse(File(TESTKIT, name).readBytes())
        val released = ByteArrayOutputStream()
        val failure = runCatching {
            AgeDecryptingInputStream(vector.identities(), vector.ageFile()).use { it.copyTo(released) }
        }.exceptionOrNull()

        if (vector.expect == "success") {
            assertNull("$name should decrypt, but: $failure", failure)
        } else {
            assertNotNull("$name should fail (${vector.expect}) but decrypted", failure)
            assertTrue("$name: unexpected $failure", failure is AgeDecryptionException)
        }
        if (name in WITHHOLDS_FINAL_CHUNK) {
            assertTrue(
                "$name: expected kage's trailing-data error, got $failure",
                failure?.cause?.message == "trailing data after end of encrypted file",
            )
            assertEquals("$name: whole chunks released", 0, released.size() % CHUNK_SIZE)
            return
        }
        vector.payloadSha256?.let { expected ->
            assertEquals(
                "$name: the ${released.size()} plaintext bytes released (failure: $failure)",
                expected,
                released.toByteArray().sha256(),
            )
        }
    }

    private class Vector(
        val headers: List<Pair<String, String>>,
        private val body: ByteArray,
    ) {
        val expect = header("expect")
        /** SHA-256 of all the plaintext an implementation may release; absent when it's none. */
        val payloadSha256 = headers.firstOrNull { it.first == "payload" }?.second

        fun identities(): List<Identity> {
            val passphrases = headers.filter { it.first == "passphrase" }.map {
                ScryptIdentity(it.second.toByteArray(), BACKUP_MAX_READ_WORK_FACTOR)
            }
            val keys = headers.filter { it.first == "identity" }.map { it.second }
            val parsedKeys = if (keys.isEmpty()) emptyList() else Age.parseIdentities(keys.joinToString("\n").reader().buffered())
            return passphrases + parsedKeys
        }

        fun ageFile(): InputStream {
            val raw = ByteArrayInputStream(body)
            return if (headers.any { it == "compressed" to "zlib" }) InflaterInputStream(raw) else raw
        }

        private fun header(key: String) = headers.first { it.first == key }.second

        companion object {
            fun parse(bytes: ByteArray): Vector {
                // A textual header, an empty line, then the age file.
                val split = (0 until bytes.size - 1).first { bytes[it] == NEWLINE && bytes[it + 1] == NEWLINE }
                val headers = bytes.copyOfRange(0, split).decodeToString().lines().map { line ->
                    line.substringBefore(": ") to line.substringAfter(": ")
                }
                return Vector(headers, bytes.copyOfRange(split + 2, bytes.size))
            }

            private const val NEWLINE = '\n'.code.toByte()
        }
    }

    companion object {
        private val TESTKIT = File("src/test/resources/age-testkit")

        private const val CHUNK_SIZE = 64 * 1024

        /** Payload-failure vectors where kage withholds the final chunk; see the class comment. */
        private val WITHHOLDS_FINAL_CHUNK = setOf(
            "armor_garbage_encoded",
            "stream_trailing_garbage_long",
            "stream_trailing_garbage_short",
            "stream_two_final_chunks",
            "stream_two_final_chunks_full",
            "stream_two_final_chunks_second",
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun vectors(): List<String> = TESTKIT.list()!!.sorted()

        private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
    }
}
