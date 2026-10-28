package dev.alializadeh.orbit.about

import dev.alializadeh.orbit.AppVersion
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The local crash log: what a report holds (and never holds), the cap, Clear, and the handler chain. */
class CrashLogTest {

    private val dir: File = Files.createTempDirectory("crash").toFile()
    private val file = File(File(dir, CrashLog.DIRECTORY), CrashLog.FILE_NAME)
    private val clock = Clock.fixed(Instant.parse("2026-10-28T09:15:30.123Z"), ZoneOffset.UTC)
    private val device = CrashLog.DeviceDescription(release = "12", sdk = 32, model = "Google Pixel 6")

    private fun log(maxBytes: Int = CrashLog.MAX_BYTES) = CrashLog(file, AppVersion("1.2.0", 7), clock, device, maxBytes)

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aReportHasTheTimeVersionPhoneThreadAndTrace() {
        val crash = IllegalStateException("Sara Ahmadi's notes", IllegalArgumentException("+98 912 345 6789"))
        log().record("main", crash)

        val text = file.readText()
        assertTrue(text.startsWith(CrashLog.HEADER))
        assertTrue(text, "Time: 2026-10-28T09:15:30Z" in text)
        assertTrue("Orbit: 1.2.0 (7)" in text)
        assertTrue("Android: 12 (API 32)" in text)
        assertTrue("Device: Google Pixel 6" in text)
        assertTrue("Thread: main" in text)
        assertTrue("java.lang.IllegalStateException" in text)
        assertTrue("Caused by: java.lang.IllegalArgumentException" in text)
        assertTrue("\tat dev.alializadeh.orbit.about.CrashLogTest" in text)
    }

    @Test
    fun exceptionMessagesAreLeftOutBecauseTheyCanHoldUserData() {
        log().record("main", IllegalStateException("Sara Ahmadi's notes", IllegalArgumentException("+98 912 345 6789")))

        val text = file.readText()
        assertFalse("Sara" in text)
        assertFalse("912" in text)
    }

    @Test
    fun reportsAddUpAndClearEmptiesTheLog() {
        val log = log()
        assertEquals(0, log.count())
        log.record("main", RuntimeException())
        log.record("DefaultDispatcher-worker-1", RuntimeException())
        assertEquals(2, log.count())

        log.clear()
        assertEquals(0, log.count())
        assertFalse(file.exists())
    }

    @Test
    fun theOldestReportsGoWhenTheLogIsFull() {
        val log = log(maxBytes = 20_000)
        repeat(30) { log.record("thread-$it", RuntimeException()) }

        assertTrue(file.length() <= 20_000)
        val text = file.readText()
        assertTrue("Thread: thread-29" in text)
        assertFalse("Thread: thread-0\n" in text)
        assertTrue(log.count() in 2..29)
    }

    @Test
    fun oneHugeReportKeepsItsStart() {
        val log = log(maxBytes = 2_000)
        log.record("main", deep(200))

        assertTrue(file.length() <= 2_100)
        assertEquals(1, log.count())
        assertTrue(file.readText().startsWith(CrashLog.HEADER))
    }

    @Test
    fun theHandlerRecordsThenPassesTheCrashOn() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        var passedOn: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> passedOn = e }
        try {
            log().install()
            val crash = IllegalStateException()
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), crash)

            assertEquals(crash, passedOn)
            assertEquals(1, log().count())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    private fun deep(depth: Int): Throwable = if (depth == 0) RuntimeException() else deep(depth - 1)
}
