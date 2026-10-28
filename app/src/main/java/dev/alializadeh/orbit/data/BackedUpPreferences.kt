package dev.alializadeh.orbit.data

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import dev.alializadeh.orbit.backup.BackupNudgeInterval
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.person.PeopleSort
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/**
 * The single row of preferences that travel inside a Backup (ADR 0006). Read them through
 * `PreferencesRepository`.
 *
 * Device-bound state (first run finished, App lock, the remembered passphrase, the "last Backup"
 * date...) never goes here; it lives in [DeviceState]. Language lives in AppCompat per-app locales.
 *
 * Enums are stored by constant name. The column defaults are the values the row is seeded with.
 */
@Entity(tableName = "backed_up_preferences")
data class BackedUpPreferences(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(defaultValue = "System") val theme: ThemeMode = ThemeMode.System,
    @ColumnInfo(defaultValue = "OrbitViolet") val colourSource: ColourSource = ColourSource.OrbitViolet,
    /** The "Your colour" seed as ARGB; kept when another source is chosen, so it can be restored. */
    val customSeedArgb: Int? = null,
    /** Set from the language on Welcome; independent of the language afterwards. */
    @ColumnInfo(defaultValue = "Gregorian") val primaryCalendar: CalendarSystem = CalendarSystem.Gregorian,
    /** Persian UI only; English always shows Latin digits. */
    @ColumnInfo(defaultValue = "0") val useLatinDigits: Boolean = false,
    /** One time of day for every Reminder and the Cadence digest, stored as minutes since midnight. */
    @ColumnInfo(defaultValue = "540") val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
    @ColumnInfo(defaultValue = "OneWeek,OnTheDay") val defaultLeadTimes: Set<LeadTime> = LeadTime.DEFAULT,
    @ColumnInfo(defaultValue = "Days30") val backupNudgeInterval: BackupNudgeInterval = BackupNudgeInterval.DEFAULT,
    @ColumnInfo(defaultValue = "Name") val peopleSort: PeopleSort = PeopleSort.Name,
    /** The Lunar date adjustment, −2…+2 days (schema 2, ADR 0007). */
    @ColumnInfo(defaultValue = "0") val lunarAdjustmentDays: Int = 0,
) {
    companion object {
        /** The table only ever holds this one row, inserted when the database is created. */
        const val SINGLE_ROW_ID = 1

        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

@Dao
interface BackedUpPreferencesDao {

    @Query("SELECT * FROM backed_up_preferences WHERE id = ${BackedUpPreferences.SINGLE_ROW_ID}")
    fun observe(): Flow<BackedUpPreferences>

    @Query("SELECT * FROM backed_up_preferences WHERE id = ${BackedUpPreferences.SINGLE_ROW_ID}")
    suspend fun get(): BackedUpPreferences

    @Update
    suspend fun update(preferences: BackedUpPreferences)
}
