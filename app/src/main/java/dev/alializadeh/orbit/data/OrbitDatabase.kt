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
import dev.alializadeh.orbit.occasion.Occasion
import dev.alializadeh.orbit.occasion.OccasionDao
import dev.alializadeh.orbit.occasion.OccasionPerson
import dev.alializadeh.orbit.occasion.OccasionRing
import dev.alializadeh.orbit.occasion.OccasionTag
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.FollowUp
import dev.alializadeh.orbit.person.GiftIdea
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.Loan
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
        Occasion::class,
        OccasionRing::class,
        OccasionTag::class,
        OccasionPerson::class,
        FollowUp::class,
        Loan::class,
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

    abstract fun occasionDao(): OccasionDao

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
        const val VERSION = 4
        const val FILE_NAME = "orbit.db"

        /** Every migration, oldest first. */
        val MIGRATIONS: List<Migration> = listOf(Migration1To2, Migration2To3, Migration3To4)
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

/** v1.1: Occasions and who they're for (PRD v1.1). */
object Migration2To3 : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // As exported in 3.json.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `occasion` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `builtIn` TEXT, " +
                "`label` TEXT, `forEveryone` INTEGER NOT NULL, `leadTimes` TEXT, `calendar` TEXT NOT NULL, " +
                "`month` INTEGER NOT NULL, `day` INTEGER NOT NULL, `year` INTEGER)",
        )
        for ((table, column, parent) in listOf(Triple("occasion_ring", "ringId", "ring"), Triple("occasion_tag", "tagId", "tag"), Triple("occasion_person", "personId", "person"))) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `$table` (`occasionId` INTEGER NOT NULL, `$column` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`occasionId`, `$column`), " +
                    "FOREIGN KEY(`occasionId`) REFERENCES `occasion`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`$column`) REFERENCES `$parent`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_$column` ON `$table` (`$column`)")
        }
    }
}

/** Follow-ups, and what's lent and borrowed. */
object Migration3To4 : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // As exported in 4.json.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `follow_up` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, `dueOn` TEXT NOT NULL, `doneOn` TEXT, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`personId`) REFERENCES `person`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_follow_up_personId` ON `follow_up` (`personId`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `loan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, " +
                "`direction` TEXT NOT NULL, `text` TEXT NOT NULL, `returnBy` TEXT, `returnedOn` TEXT, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`personId`) REFERENCES `person`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_personId` ON `loan` (`personId`)")
    }
}
