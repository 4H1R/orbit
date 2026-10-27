package dev.alializadeh.orbit.backup

import androidx.room3.useWriterConnection
import dev.alializadeh.orbit.data.OrbitDatabase
import java.io.File
import javax.inject.Inject

/** Takes consistent copies of the live database for Backups. */
fun interface DatabaseSnapshotter {

    /**
     * Writes a consistent, compacted copy of the database to [target], which must not exist yet,
     * and returns the copy's schema version.
     */
    suspend fun snapshotTo(target: File): Int
}

/**
 * Snapshots with SQLite's `VACUUM INTO`, which reads a consistent view without blocking readers and
 * leaves out deleted-row leftovers. It must run outside any transaction, so it runs on the bare
 * writer connection; holding that connection also keeps writes out until the copy is done.
 */
class RoomDatabaseSnapshotter @Inject constructor(
    private val database: OrbitDatabase,
) : DatabaseSnapshotter {

    override suspend fun snapshotTo(target: File): Int {
        require(!target.exists()) { "VACUUM INTO needs a target that doesn't exist: $target" }
        return database.useWriterConnection { connection ->
            connection.usePrepared("VACUUM INTO ?") { statement ->
                statement.bindText(1, target.absolutePath)
                statement.step()
            }
            // VACUUM copies user_version, where Room keeps the schema version.
            connection.usePrepared("PRAGMA user_version") { statement ->
                statement.step()
                statement.getLong(0).toInt()
            }
        }
    }
}
