package dev.alializadeh.orbit.backup

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kage.Age
import kage.crypto.scrypt.ScryptIdentity
import kage.crypto.scrypt.ScryptRecipient
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the Backup's scrypt cost on a real device (docs/notes/scrypt-measurement.md): key
 * derivation at logN 16, 17 and 18, and a ~50 MB encrypt + decrypt round trip at Orbit's logN 17,
 * with the peak Java heap during each. Results go to logcat under the tag `OrbitScrypt`.
 *
 * Run it on the phone:
 * `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.alializadeh.orbit.backup.ScryptMeasurement`
 * then `adb logcat -d -s OrbitScrypt`.
 */
@RunWith(AndroidJUnit4::class)
class ScryptMeasurement {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun measure() {
        logDevice()
        for (logN in listOf(16, 17, 18)) {
            try {
                measureKeyDerivation(logN)
            } catch (e: OutOfMemoryError) {
                // An answer, not a test failure: this phone can't afford this cost.
                log("scrypt logN=$logN: OUT OF MEMORY (scrypt needs ${(128L shl logN) * 8 / (1024 * 1024)} MiB): $e")
            }
        }
        measureRoundTrip(sizeBytes = 50L * 1024 * 1024, logN = BACKUP_WRITE_WORK_FACTOR)
    }

    private fun logDevice() {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        log(
            "device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "ABIs ${Build.SUPPORTED_ABIS.joinToString()}, memoryClass ${activityManager.memoryClass} MiB, " +
                "largeMemoryClass ${activityManager.largeMemoryClass} MiB, maxMemory ${Runtime.getRuntime().maxMemory().mib} MiB",
        )
    }

    /** One scrypt derivation per direction: writing (wrap) and reading (unwrap), three runs each. */
    private fun measureKeyDerivation(logN: Int) {
        val passphrase = PASSPHRASE.toByteArray()
        val encryptRuns = mutableListOf<Measured>()
        val decryptRuns = mutableListOf<Measured>()
        repeat(RUNS) {
            var header = ByteArray(0)
            encryptRuns += measured {
                val out = ByteArrayOutputStream()
                Age.encryptStream(listOf(ScryptRecipient(passphrase, logN)), out).close()
                header = out.toByteArray()
            }
            decryptRuns += measured {
                Age.decryptStream(listOf(ScryptIdentity(passphrase, maxWorkFactor = logN)), header.inputStream(), NullOutputStream)
            }
        }
        log("scrypt logN=$logN: derive for Backup ${encryptRuns.summary()}; derive for Restore ${decryptRuns.summary()}")
    }

    /** A Backup-sized payload (incompressible, like photos) through age at Orbit's cost. */
    private fun measureRoundTrip(sizeBytes: Long, logN: Int) {
        val dir = File(context.cacheDir, "scrypt-measurement").apply { deleteRecursively(); mkdirs() }
        try {
            val plain = File(dir, "plain.bin")
            val digest = MessageDigest.getInstance("SHA-256")
            plain.outputStream().buffered().use { out ->
                val random = Random(17)
                val chunk = ByteArray(1024 * 1024)
                repeat((sizeBytes / chunk.size).toInt()) {
                    random.nextBytes(chunk)
                    digest.update(chunk)
                    out.write(chunk)
                }
            }
            val expected = digest.digest()
            val encrypted = File(dir, "plain.bin.age")

            val encrypt = measured {
                plain.inputStream().buffered().use { input ->
                    Age.encryptStream(listOf(ScryptRecipient(PASSPHRASE.toByteArray(), logN)), encrypted.outputStream().buffered())
                        .use { input.copyTo(it, 64 * 1024) }
                }
            }
            val actual = MessageDigest.getInstance("SHA-256")
            val decrypt = measured {
                encrypted.inputStream().buffered().use { source ->
                    AgeDecryptingInputStream(listOf(ScryptIdentity(PASSPHRASE.toByteArray())), source).use { plaintext ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = plaintext.read(buffer)
                            if (n < 0) break
                            actual.update(buffer, 0, n)
                        }
                    }
                }
            }
            assertEquals(expected.toHexString(), actual.digest().toHexString())
            log(
                "round trip ${sizeBytes.mib} MiB at logN=$logN: Backup (encrypt) ${encrypt.describe()}; " +
                    "Restore (decrypt + verify to the end) ${decrypt.describe()}",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    private class Measured(val millis: Long, val peakHeapBytes: Long) {
        fun describe() = "${millis} ms, peak heap ${peakHeapBytes.mib} MiB"
    }

    private fun List<Measured>.summary(): String {
        val times = map(Measured::millis).sorted()
        return "median ${times[size / 2]} ms (min ${times.first()}, max ${times.last()}), peak heap ${maxOf(Measured::peakHeapBytes).mib} MiB"
    }

    /** Times [block] while sampling the used Java heap every 2 ms. */
    private fun measured(block: () -> Unit): Measured {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        SystemClock.sleep(200)
        val peak = AtomicLong(runtime.totalMemory() - runtime.freeMemory())
        val sampling = AtomicBoolean(true)
        val sampler = Thread {
            while (sampling.get()) {
                peak.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory(), ::maxOf)
                SystemClock.sleep(2)
            }
        }.apply { start() }
        val start = SystemClock.elapsedRealtimeNanos()
        try {
            block()
        } finally {
            sampling.set(false)
            sampler.join()
        }
        return Measured((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000, peak.get())
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private object NullOutputStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private companion object {
        const val TAG = "OrbitScrypt"
        const val PASSPHRASE = "a passphrase of typical length"
        const val RUNS = 3
        val Long.mib: Long get() = this / (1024 * 1024)
    }
}
