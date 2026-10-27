package dev.alializadeh.orbit.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers

/**
 * Room on the plain JVM (no Robolectric, no emulator): the bundled SQLite driver's JVM artifact
 * (`sqlite-bundled-jvm`) carries the host's native SQLite, and Room 3 builds without a Context.
 */
object TestDatabases {

    fun inMemory(): OrbitDatabase =
        Room.inMemoryDatabaseBuilder<OrbitDatabase>()
            .configured()
            .build()

    /** A file-backed database, for what needs a real file (`VACUUM INTO`, WAL). */
    fun onDisk(file: File): OrbitDatabase =
        Room.databaseBuilder<OrbitDatabase>(file.absolutePath)
            .configured()
            .build()

    private fun androidx.room3.RoomDatabase.Builder<OrbitDatabase>.configured() =
        setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(OrbitDatabase.SeedCallback)
}
