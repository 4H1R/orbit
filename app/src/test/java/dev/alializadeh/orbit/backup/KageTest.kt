package dev.alializadeh.orbit.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kage.Age
import kage.crypto.scrypt.ScryptIdentity
import kage.crypto.scrypt.ScryptRecipient
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** kage round trips on the JVM, through Orbit's [AgeDecryptingInputStream]. */
class KageTest {

    @Test
    fun roundTripsAtOrbitsWorkFactor() {
        val plaintext = Random(17).nextBytes(300_000)

        val decrypted = decrypt(encrypt(plaintext, BACKUP_WRITE_WORK_FACTOR), PASSPHRASE)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun roundTripsTheChunkBoundaries() {
        for (size in listOf(0, 1, CHUNK - 1, CHUNK, CHUNK + 1, 3 * CHUNK)) {
            val plaintext = Random(size).nextBytes(size)

            assertArrayEquals("size $size", plaintext, decrypt(encrypt(plaintext, workFactor = 10), PASSPHRASE))
        }
    }

    @Test
    fun acceptsTheHighestReadWorkFactorAndNoHigher() {
        val plaintext = "orbit".toByteArray()

        assertArrayEquals(plaintext, decrypt(encrypt(plaintext, BACKUP_MAX_READ_WORK_FACTOR), PASSPHRASE))
        // Refused from the header alone, before any scrypt work (a real 2^19 run needs 512 MiB).
        val tooCostly = encrypt(plaintext, workFactor = 10).claimingWorkFactor(BACKUP_MAX_READ_WORK_FACTOR + 1)
        val failure = assertThrows(AgeDecryptionException::class.java) { decrypt(tooCostly, PASSPHRASE) }
        assertTrue(failure.cause?.message.orEmpty(), failure.cause?.message.orEmpty().contains("too large"))
    }

    @Test
    fun aWrongPassphraseFailsWhenReadingReachesTheEnd() {
        assertThrows(AgeDecryptionException::class.java) {
            decrypt(encrypt("orbit".toByteArray(), workFactor = 10), "wrong")
        }
    }

    @Test
    fun closingEarlyStopsTheWorker() {
        val ciphertext = encrypt(Random(1).nextBytes(20 * CHUNK), workFactor = 10)
        val stream = AgeDecryptingInputStream(listOf(ScryptIdentity(PASSPHRASE.toByteArray())), ciphertext.inputStream())

        stream.read(ByteArray(10))
        val closed = Thread { stream.close() }.apply { start(); join(5_000) }

        assertEquals("close() returned", false, closed.isAlive)
    }

    private fun encrypt(plaintext: ByteArray, workFactor: Int): ByteArray {
        val out = ByteArrayOutputStream()
        Age.encryptStream(listOf(ScryptRecipient(PASSPHRASE.toByteArray(), workFactor)), out).use { it.write(plaintext) }
        return out.toByteArray()
    }

    private fun decrypt(ciphertext: ByteArray, passphrase: String): ByteArray =
        AgeDecryptingInputStream(
            listOf(ScryptIdentity(passphrase.toByteArray(), BACKUP_MAX_READ_WORK_FACTOR)),
            ciphertext.inputStream(),
        ).use(InputStream::readBytes)

    private companion object {
        /** Rewrites the scrypt stanza's work factor; the header MAC no longer matches, which is fine. */
        fun ByteArray.claimingWorkFactor(workFactor: Int): ByteArray =
            toString(Charsets.ISO_8859_1)
                .replaceFirst(Regex("""(-> scrypt \S+) \d+\n"""), "$1 $workFactor\n")
                .toByteArray(Charsets.ISO_8859_1)

        const val PASSPHRASE = "correct horse battery staple"
        const val CHUNK = 64 * 1024
    }
}
