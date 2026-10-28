package dev.alializadeh.orbit.about

import android.os.Build
import dev.alializadeh.orbit.AppVersion
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Orbit's crash log: there's no crash reporting (no INTERNET, ticket 11), so an uncaught exception
 * is appended to a small file on the phone, which the user can share by hand from Settings → About.
 *
 * A report holds the time, Orbit's version, the Android version and device model, the thread and
 * the stack trace: exception class names and stack frames only. Exception messages are left out,
 * because they can quote user data (a name in a parse error, a value in SQL). The file is capped at
 * [maxBytes]: the oldest reports go first.
 */
class CrashLog(
    private val file: File,
    private val version: AppVersion,
    private val clock: Clock,
    private val device: DeviceDescription = DeviceDescription.current(),
    private val maxBytes: Int = MAX_BYTES,
) {

    /** The file to share, once there's something in it. */
    val logFile: File get() = file

    /**
     * Records every uncaught exception, then hands it to the handler that was there before (the
     * system's, which shows the crash and ends the process).
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record(thread.name, throwable)
            } catch (_: Throwable) {
                // Never let the log get in the way of the crash itself.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Appends one report, dropping the oldest ones if the file would grow past the cap. */
    @Synchronized
    fun record(threadName: String, throwable: Throwable) {
        // A single report longer than the whole cap (a deep recursion) keeps its start.
        val newest = report(threadName, throwable).let { if (it.length > maxBytes) it.take(maxBytes - CUT.length) + CUT else it }
        var kept = reports() + newest
        while (kept.size > 1 && kept.sumOf { it.toByteArray().size } > maxBytes) kept = kept.drop(1)
        file.parentFile?.mkdirs()
        file.writeText(kept.joinToString(""))
    }

    /** How many reports the log holds. */
    @Synchronized
    fun count(): Int = reports().size

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun reports(): List<String> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank()) return emptyList()
        // Each report starts with the header line; anything before the first one is a cut-off tail.
        val starts = Regex("^" + Regex.escape(HEADER) + "$", RegexOption.MULTILINE).findAll(text).map { it.range.first }.toList()
        return starts.mapIndexed { i, start -> text.substring(start, starts.getOrElse(i + 1) { text.length }) }
    }

    internal fun report(threadName: String, throwable: Throwable): String = buildString {
        appendLine(HEADER)
        appendLine("Time: ${Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)}")
        appendLine("Orbit: ${version.name} (${version.code})")
        appendLine("Android: ${device.release} (API ${device.sdk})")
        appendLine("Device: ${device.model}")
        appendLine("Thread: $threadName")
        appendTrace(throwable, prefix = "", seen = HashSet())
        appendLine()
    }

    private fun StringBuilder.appendTrace(throwable: Throwable, prefix: String, seen: MutableSet<Throwable>) {
        if (!seen.add(throwable)) {
            appendLine("$prefix[circular reference: ${throwable.javaClass.name}]")
            return
        }
        appendLine("$prefix${throwable.javaClass.name}")
        throwable.stackTrace.forEach { appendLine("\tat $it") }
        throwable.suppressed.forEach { appendTrace(it, prefix = "Suppressed: ", seen = seen) }
        throwable.cause?.let { appendTrace(it, prefix = "Caused by: ", seen = seen) }
    }

    /** What a report says about the phone. */
    data class DeviceDescription(val release: String, val sdk: Int, val model: String) {
        companion object {
            fun current() = DeviceDescription(
                release = Build.VERSION.RELEASE.orEmpty(),
                sdk = Build.VERSION.SDK_INT,
                model = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" "),
            )
        }
    }

    companion object {
        const val HEADER = "=== Orbit crash ==="

        private const val CUT = "\t... (cut)\n\n"

        /** About a dozen long stack traces. */
        const val MAX_BYTES = 128 * 1024

        /** Where the log lives, under `filesDir` (shared through the FileProvider's `crash/` path). */
        const val DIRECTORY = "crash"
        const val FILE_NAME = "orbit-crash-log.txt"
    }
}
