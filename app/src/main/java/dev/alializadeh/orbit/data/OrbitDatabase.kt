package dev.alializadeh.orbit.data

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
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

/**
 * Orbit's one database.
 *
 * Schema policy until the end of slice 5: the version stays 1 and the schema is edited in place
 * (no real data exists yet, so there are no migrations). From slice 5 on, the schema is frozen and
 * every change ships a migration and its test. Destructive fallback is never used.
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

    /**
     * Seeds the single [BackedUpPreferences] row (with its column defaults) when the database file
     * is first created. The seed Rings are not created here but when first run finishes, in the
     * language chosen on Welcome.
     */
    object SeedCallback : Callback() {
        override suspend fun onCreate(connection: SQLiteConnection) {
            connection.execSQL(
                "INSERT INTO backed_up_preferences (id) VALUES (${BackedUpPreferences.SINGLE_ROW_ID})",
            )
        }
    }

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "orbit.db"
    }
}
