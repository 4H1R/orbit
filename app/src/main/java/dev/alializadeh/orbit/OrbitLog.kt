package dev.alializadeh.orbit

import android.util.Log

/**
 * Orbit's only way to Logcat: a fixed message and, for a failure, just the exception's class name.
 * Never an exception message or stack trace, a URI, a name or a file name: those can quote the
 * user's data (like `CrashLog`). Pass constant text as [message].
 */
object OrbitLog {

    fun i(tag: String, message: String, error: Throwable? = null) {
        Log.i(tag, line(message, error))
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, line(message, error))
    }

    /** "Backup failed: java.io.IOException". */
    fun line(message: String, error: Throwable?): String =
        if (error == null) message else "$message: ${error.javaClass.name}"
}
