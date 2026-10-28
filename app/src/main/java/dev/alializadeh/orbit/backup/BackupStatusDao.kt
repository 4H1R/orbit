package dev.alializadeh.orbit.backup

import androidx.room3.Dao
import androidx.room3.Query
import dev.alializadeh.orbit.data.DataVersion
import kotlinx.coroutines.flow.Flow

/** What Backup bookkeeping reads from the live database. */
@Dao
interface BackupStatusDao {

    /** The live data version; see [DataVersion]. */
    @Query("SELECT version FROM ${DataVersion.TABLE} WHERE id = ${DataVersion.SINGLE_ROW_ID}")
    fun observeDataVersion(): Flow<Long>

    @Query("SELECT version FROM ${DataVersion.TABLE} WHERE id = ${DataVersion.SINGLE_ROW_ID}")
    suspend fun dataVersion(): Long

    /** The restore token (see [DataVersion.RESTORE_TOKEN_ROW_ID]); null before the first Restore. */
    @Query("SELECT version FROM ${DataVersion.TABLE} WHERE id = ${DataVersion.RESTORE_TOKEN_ROW_ID}")
    suspend fun restoreToken(): Long?

    /** Every Person, Archived ones included. */
    @Query("SELECT COUNT(*) FROM person")
    fun observePeopleCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM person")
    suspend fun peopleCount(): Int

    /** The photo files the live data references. */
    @Query("SELECT DISTINCT photoFileName FROM person WHERE photoFileName IS NOT NULL")
    suspend fun photoFileNames(): List<String>
}
