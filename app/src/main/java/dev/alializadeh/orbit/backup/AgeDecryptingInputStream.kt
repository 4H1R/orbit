package dev.alializadeh.orbit.backup

import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kage.Age
import kage.Identity
import kotlin.concurrent.thread

/**
 * The plaintext of the age file in [source], decrypted with the first of [identities] that fits,
 * as a stream to pull from.
 *
 * kage only decrypts by pushing into an [java.io.OutputStream], so a worker thread decrypts into a
 * pipe. Nothing is buffered beyond the pipe and nothing touches the disk. If decryption fails (wrong
 * passphrase, damaged or truncated file), reading ends with an [AgeDecryptionException] carrying
 * kage's error. Only a read that reaches the end has proved the whole file authentic.
 *
 * Read from one thread at a time, and close it to stop the worker. [source] stays owned by the
 * caller.
 */
internal class AgeDecryptingInputStream(
    identities: List<Identity>,
    source: InputStream,
) : InputStream() {

    private val pipe = PipedInputStream(PIPE_BUFFER_SIZE)
    private val sink = PipedOutputStream(pipe)

    @Volatile private var failure: Throwable? = null

    private val worker = thread(name = "orbit-age-decrypt", isDaemon = true) {
        try {
            Age.decryptStream(identities, source, sink)
        } catch (t: Throwable) {
            failure = t
        } finally {
            // Closing the writing end is what lets the reader see the end of the stream.
            runCatching { sink.close() }
        }
    }

    override fun read(): Int = pipe.read().also { if (it == -1) throwIfFailed() }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        pipe.read(b, off, len).also { if (it == -1) throwIfFailed() }

    override fun available(): Int = pipe.available()

    override fun close() {
        pipe.close() // A worker blocked on a full pipe now fails and ends.
        worker.join()
    }

    private fun throwIfFailed() {
        worker.join()
        failure?.let { throw AgeDecryptionException(it) }
    }

    private companion object {
        /** Matches age's 64 KiB STREAM chunk. */
        const val PIPE_BUFFER_SIZE = 64 * 1024
    }
}

/** kage failed to decrypt; [cause] is kage's own exception. */
internal class AgeDecryptionException(cause: Throwable) : IOException(cause.toString(), cause)
