package dev.alializadeh.orbit

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review finding 6: Logcat never gets an exception's message (it can quote user data). */
class OrbitLogTest {

    @Test
    fun onlyTheExceptionsClassIsLogged() {
        val error = IOException("open failed: content://downloads/Sara Karimi/orbit-backup.age")

        assertEquals("Backup failed: java.io.IOException", OrbitLog.line("Backup failed", error))
        assertEquals("Backup failed", OrbitLog.line("Backup failed", null))
    }

    /** Every Logcat line goes through [OrbitLog]: no other file uses `android.util.Log`. */
    @Test
    fun nothingElseLogsDirectly() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        val direct = sources
            .filter { it.name != "OrbitLog.kt" }
            .filter { file -> file.readText().let { "android.util.Log" in it || Regex("""\bLog\.[a-z]+\(""").containsMatchIn(it) } }
            .map { it.name }
        assertEquals(emptyList<String>(), direct)
    }
}
