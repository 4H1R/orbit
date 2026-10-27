package dev.alializadeh.orbit.data

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * The single row of preferences that travel inside a Backup (ADR 0006): later slices add the
 * Appearance and colour source, Primary calendar, "Use Latin digits", Reminder time, default lead
 * times, the Backup nudge interval and the People-list sort as columns here.
 *
 * Device-bound state (App lock, the remembered passphrase, the "last Backup" date...) never goes
 * here; it lives in DataStore. Language lives in AppCompat per-app locales.
 */
@Entity(tableName = "backed_up_preferences")
data class BackedUpPreferences(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
) {
    companion object {
        /** The table only ever holds this one row, inserted when the database is created. */
        const val SINGLE_ROW_ID = 1
    }
}

@Dao
interface BackedUpPreferencesDao {

    @Query("SELECT * FROM backed_up_preferences WHERE id = ${BackedUpPreferences.SINGLE_ROW_ID}")
    fun observe(): Flow<BackedUpPreferences>
}
