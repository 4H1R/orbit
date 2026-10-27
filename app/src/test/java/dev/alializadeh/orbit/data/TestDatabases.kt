package dev.alializadeh.orbit.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
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

/** A Ring for tests that only need People to have one (every Person sits in a Ring). */
suspend fun OrbitDatabase.insertTestRing(name: String = "Friends", position: Int = 0): Long =
    ringDao().insert(Ring(name = name, position = position, cadence = Cadence.days(30)))
