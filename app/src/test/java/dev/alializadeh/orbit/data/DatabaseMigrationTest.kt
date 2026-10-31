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

    /** v1.1: the Lunar date adjustment column arrives as 0, and the other preferences stay. */
    @Test
    fun version1To2AddsTheLunarAdjustment() = runTest {
        val file = File(temp.root, "v1-prefs.db")
        SchemaMigrations.createDatabase(1, file) { connection ->
            connection.execSQL("INSERT INTO backed_up_preferences (id, primaryCalendar, useLatinDigits) VALUES (1, 'SolarHijri', 1)")
        }

        val database = SchemaMigrations.openWithRoom(file)
        try {
            val preferences = database.backedUpPreferencesDao().get()
            assertEquals(dev.alializadeh.orbit.calendar.CalendarSystem.SolarHijri, preferences.primaryCalendar)
            assertTrue(preferences.useLatinDigits)
            assertEquals(0, preferences.lunarAdjustmentDays)
        } finally {
            database.close()
        }
    }

    /** v1.1: the Occasion tables arrive empty, and CASCADE from a deleted Person works. */
    @Test
    fun version2To3AddsOccasions() = runTest {
        val file = File(temp.root, "v2.db")
        SchemaMigrations.createDatabase(2, file) { connection ->
            connection.execSQL("INSERT INTO ring (id, name, position, cadence_count, cadence_unit) VALUES (1, 'Close', 0, 30, 'DAYS')")
            connection.execSQL("INSERT INTO person (id, name, ringId, hasOwnCadence, createdAt) VALUES (1, 'Sara', 1, 0, 0)")
        }

        val database = SchemaMigrations.openWithRoom(file)
        try {
            val repository = dev.alializadeh.orbit.occasion.RoomOccasionRepository(database, database.occasionDao())
            val id = repository.save(
                dev.alializadeh.orbit.occasion.OccasionDraft(
                    label = "Book club",
                    date = dev.alializadeh.orbit.calendar.RecurringDate(dev.alializadeh.orbit.calendar.CalendarSystem.Gregorian, 3, 1),
                    personIds = setOf(1),
                ),
            )
            assertEquals(setOf(1L), repository.get(id)!!.personIds)
            database.personDao().delete(1)
            assertTrue(repository.get(id)!!.personIds.isEmpty())
        } finally {
            database.close()
        }
    }

    /** Follow-ups and Loans arrive empty, the data stays, and they go with a deleted Person. */
    @Test
    fun version3To4AddsFollowUpsAndLoans() = runTest {
        val file = File(temp.root, "v3.db")
        SchemaMigrations.createDatabase(3, file) { connection ->
            connection.execSQL("INSERT INTO ring (id, name, position, cadence_count, cadence_unit) VALUES (1, 'Close', 0, 30, 'DAYS')")
            connection.execSQL("INSERT INTO person (id, name, ringId, hasOwnCadence, createdAt) VALUES (1, 'Sara', 1, 0, 0)")
        }

        val database = SchemaMigrations.openWithRoom(file)
        try {
            val dao = database.personDao()
            assertEquals(listOf("Sara"), dao.getAll().map { it.name })
            assertTrue(dao.getWithDetails(1)!!.followUps.isEmpty())
            val date = java.time.LocalDate.of(2026, 11, 1)
            val followUp = dao.insertFollowUp(dev.alializadeh.orbit.person.FollowUp(personId = 1, text = "Interview", dueOn = date, createdAt = java.time.Instant.EPOCH))
            val loan = dao.insertLoan(dev.alializadeh.orbit.person.Loan(personId = 1, direction = dev.alializadeh.orbit.person.LoanDirection.Lent, text = "Book", createdAt = java.time.Instant.EPOCH))
            assertEquals(date, dao.getFollowUp(followUp)!!.dueOn)
            dao.delete(1)
            assertEquals(null, dao.getFollowUp(followUp))
            assertEquals(null, dao.getLoan(loan))
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
