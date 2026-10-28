package dev.alializadeh.orbit.data

import androidx.room3.useReaderConnection
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The schema is frozen (since slice 5): every version is exported to `app/schemas/`, and a
 * database from any older version migrates to the current one without losing data.
 *
 * Adding version N+1: bump [OrbitDatabase.VERSION], add the Migration to
 * [OrbitDatabase.MIGRATIONS], build once so Room exports `N+1.json` (commit it), and add a test
 * here that creates a version-N database with [SchemaMigrations.createDatabase] (with rows), opens
 * it with [SchemaMigrations.openWithRoom] and checks the rows came through.
 */
class DatabaseMigrationTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun everyVersionUpToTheCurrentOneIsExported() {
        assertEquals((1..OrbitDatabase.VERSION).toList(), SchemaMigrations.exportedVersions())
    }

    @Test
    fun everyStepHasAMigration() {
        val steps = OrbitDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until OrbitDatabase.VERSION).map { it to it + 1 }, steps)
    }

    /** The placeholder until version 2: version 1 opens from its exported schema, as it is. */
    @Test
    fun version1OpensFromItsExportedSchema() = runTest {
        val file = File(temp.root, "v1.db")
        SchemaMigrations.createDatabase(1, file) { connection ->
            connection.execSQL("INSERT INTO ring (id, name, position, cadence_count, cadence_unit) VALUES (1, 'Close', 0, 30, 'DAYS')")
            connection.execSQL("INSERT INTO person (name, ringId, hasOwnCadence, createdAt) VALUES ('Sara', 1, 0, 0)")
        }

        val database = SchemaMigrations.openWithRoom(file)
        try {
            assertEquals(listOf("Sara"), database.personDao().getAll().map { it.name })
        } finally {
            database.close()
        }
    }

    /** Runs every migration path to the current version; Room validates each result. */
    @Test
    fun everyOlderVersionMigratesToTheCurrentOne() = runTest {
        for (version in SchemaMigrations.exportedVersions().filter { it < OrbitDatabase.VERSION }) {
            val file = File(temp.root, "v$version.db")
            SchemaMigrations.createDatabase(version, file)
            val database = SchemaMigrations.openWithRoom(file)
            try {
                val userVersion = database.useReaderConnection { connection ->
                    connection.usePrepared("PRAGMA user_version") { it.step(); it.getLong(0) }
                }
                assertEquals(OrbitDatabase.VERSION.toLong(), userVersion)
            } finally {
                database.close()
            }
        }
        assertTrue(SchemaMigrations.exportedVersions().isNotEmpty())
    }
}
