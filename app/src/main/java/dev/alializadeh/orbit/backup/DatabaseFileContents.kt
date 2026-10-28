package dev.alializadeh.orbit.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import dev.alializadeh.orbit.data.DataVersion
import dev.alializadeh.orbit.data.queryLong
import dev.alializadeh.orbit.data.queryStrings
import java.io.File

/**
 * What a database file outside Room holds (a Backup's snapshot, or a staged Restore): read straight
 * from the file, so it describes exactly what the Backup contains.
 */
internal data class DatabaseFileContents(
    val schemaVersion: Int,
    val dataVersion: Long,
    val people: Int,
    val interactions: Int,
    /** The photo file names its People reference. */
    val photoFileNames: List<String>,
) {
    companion object {
        fun read(file: File): DatabaseFileContents =
            BundledSQLiteDriver().open(file.path, SQLITE_OPEN_READONLY).use { connection ->
                DatabaseFileContents(
                    schemaVersion = connection.queryLong("PRAGMA user_version").toInt(),
                    dataVersion = connection.queryLong(
                        "SELECT version FROM ${DataVersion.TABLE} WHERE id = ${DataVersion.SINGLE_ROW_ID}",
                    ),
                    people = connection.queryLong("SELECT COUNT(*) FROM person").toInt(),
                    interactions = connection.queryLong("SELECT COUNT(*) FROM interaction").toInt(),
                    photoFileNames = connection.queryStrings(
                        "SELECT DISTINCT photoFileName FROM person WHERE photoFileName IS NOT NULL ORDER BY photoFileName",
                    ),
                )
            }
    }
}
