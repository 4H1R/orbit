package dev.alializadeh.orbit.data

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.alializadeh.orbit.backup.BackupStatusDao
import dev.alializadeh.orbit.interaction.Interaction
import dev.alializadeh.orbit.interaction.InteractionDao
import dev.alializadeh.orbit.interaction.InteractionPerson
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.GiftIdea
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.PersonDao
import dev.alializadeh.orbit.person.PersonItem
import dev.alializadeh.orbit.person.PersonTag
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.TagDao
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingDao
import kotlinx.coroutines.Dispatchers

/**
 * Orbit's one database.
 *
 * **The schema is frozen** (since slice 5, when real data started): every change bumps [VERSION],
 * adds a [Migration] to [MIGRATIONS] and a test in `DatabaseMigrationTest`, and commits the newly
 * exported `app/schemas/.../<version>.json`. Destructive fallback is never used.
 */
@Database(
    entities = [
        Person::class,
        BackedUpPreferences::class,
        Ring::class,
        PersonItem::class,
        Tag::class,
        PersonTag::class,
        ContactMethod::class,
        ImportantDate::class,
        GiftIdea::class,
        Interaction::class,
        InteractionPerson::class,
        DataVersion::class,
    ],
    version = OrbitDatabase.VERSION,
    exportSchema = true,
)
@ColumnTypeConverters(InstantConverters::class, LocalDateConverters::class, PreferenceConverters::class)
abstract class OrbitDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao

    abstract fun backedUpPreferencesDao(): BackedUpPreferencesDao

    abstract fun ringDao(): RingDao

    abstract fun tagDao(): TagDao

    abstract fun interactionDao(): InteractionDao

    abstract fun backupStatusDao(): BackupStatusDao

    /**
     * Seeds the single-row tables ([BackedUpPreferences] with its column defaults, [DataVersion])
     * when the database file is first created, and keeps the [DataVersionTriggers] in place on
     * every open. The seed Rings are not created here but when first run finishes, in the language
     * chosen on Welcome.
     */
    object SeedCallback : Callback() {
        override suspend fun onCreate(connection: SQLiteConnection) {
            connection.execSQL(
                "INSERT INTO ${DataVersion.TABLE} (id, version) VALUES (${DataVersion.SINGLE_ROW_ID}, 0)",
            )
            connection.execSQL(
                "INSERT INTO backed_up_preferences (id) VALUES (${BackedUpPreferences.SINGLE_ROW_ID})",
            )
        }

        override suspend fun onOpen(connection: SQLiteConnection) {
            DataVersionTriggers.install(connection)
        }
    }

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "orbit.db"

        /** Every migration, oldest first. */
        val MIGRATIONS: List<Migration> = listOf(Migration1To2)
    }
}

/** v1.1: the Lunar date adjustment, a backed-up preference (ADR 0007). */
object Migration1To2 : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE backed_up_preferences ADD COLUMN lunarAdjustmentDays INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * The setup every Orbit database gets, on the phone and in tests: Room 3 on the bundled SQLite
 * driver (the same SQLite, with `VACUUM INTO`, everywhere), I/O off the main thread, the
 * migrations, and, unless it's only being validated, [OrbitDatabase.SeedCallback].
 */
fun RoomDatabase.Builder<OrbitDatabase>.orbitSetup(seed: Boolean = true): RoomDatabase.Builder<OrbitDatabase> =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*OrbitDatabase.MIGRATIONS.toTypedArray())
        .apply { if (seed) addCallback(OrbitDatabase.SeedCallback) }
