package dev.alializadeh.orbit.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * "Has the data changed since the last Backup?": a counter that every write to Orbit's data bumps,
 * in the same transaction as the write.
 *
 * SQLite triggers on every table ([DataVersionTriggers]) do the bumping, so no write path can forget
 * it, raw SQL included. A Backup records the counter of its snapshot in device-bound state, and the
 * data has changed since when the live counter differs. Being in the database, the counter travels
 * in the Backup: after a Restore the restored counter is the recorded one, so the data counts as
 * unchanged (a Restore counts as a Backup).
 */
@Entity(tableName = DataVersion.TABLE)
data class DataVersion(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(defaultValue = "0") val version: Long = 0,
) {
    companion object {
        const val TABLE = "data_version"
        const val SINGLE_ROW_ID = 1

        /**
         * A second row (no schema change) whose [version] is the **restore token**: a random number
         * written inside every Restore's swap transaction, absent until the first Restore. It tells
         * whether a Restore's swap committed, and lets device-bound state that refers to rows by id
         * (notification state, notification actions) notice that the data was replaced. The bump
         * triggers only touch [SINGLE_ROW_ID].
         */
        const val RESTORE_TOKEN_ROW_ID = 2
    }
}

/**
 * Keeps one bump trigger per table and kind of write (insert, update, delete), for every table
 * Orbit's data lives in. Installed when the database opens, from the live schema, so a table or
 * column added by a later migration is covered without anyone remembering to.
 */
object DataVersionTriggers {

    private const val PREFIX = "data_version_bump_"

    /**
     * Columns whose updates aren't the user's data changing: the Cadence digest's "last notified as
     * Due" marker (slice 6), written by Orbit itself every day. A targeted `UPDATE person SET
     * lastNotifiedDueOn = …` therefore doesn't bump; any write that sets other columns still does.
     */
    private val ignoredColumns = mapOf("person" to setOf("lastNotifiedDueOn"))

    /** Creates, replaces or drops triggers so they match the current schema. Cheap when nothing changed. */
    fun install(connection: SQLiteConnection) {
        val wanted = wantedTriggers(connection)
        val existing = connection.queryPairs(
            "SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND name LIKE '$PREFIX%'",
        ).toMap()
        if (wanted == existing) return
        // A savepoint works whether or not Room has a transaction open on this connection.
        connection.execSQL("SAVEPOINT data_version_triggers")
        try {
            for ((name, sql) in existing) {
                if (wanted[name] != sql) connection.execSQL("DROP TRIGGER IF EXISTS `$name`")
            }
            for ((name, sql) in wanted) {
                if (existing[name] != sql) connection.execSQL(sql)
            }
            connection.execSQL("RELEASE data_version_triggers")
        } catch (t: Throwable) {
            connection.execSQL("ROLLBACK TO data_version_triggers")
            connection.execSQL("RELEASE data_version_triggers")
            throw t
        }
    }

    /** The tables holding Orbit's data: all but SQLite's, Room's and the counter itself. */
    fun dataTables(connection: SQLiteConnection, schema: String = "main"): List<String> =
        connection.queryStrings("SELECT name FROM $schema.sqlite_master WHERE type = 'table' ORDER BY name")
            .filter { name ->
                name != DataVersion.TABLE &&
                    !name.startsWith("sqlite_") &&
                    !name.startsWith("room_") &&
                    !name.startsWith("android_")
            }

    private fun wantedTriggers(connection: SQLiteConnection): Map<String, String> = buildMap {
        val bump = "BEGIN UPDATE ${DataVersion.TABLE} SET version = version + 1 " +
            "WHERE id = ${DataVersion.SINGLE_ROW_ID}; END"
        for (table in dataTables(connection)) {
            val ignored = ignoredColumns[table].orEmpty()
            val updateOf = if (ignored.isEmpty()) {
                ""
            } else {
                val columns = connection.queryStrings("SELECT name FROM pragma_table_info('$table')")
                    .filterNot { it in ignored }
                "OF " + columns.joinToString(", ") { "`$it`" } + " "
            }
            put("$PREFIX${table}_insert", "CREATE TRIGGER `$PREFIX${table}_insert` AFTER INSERT ON `$table` $bump")
            put("$PREFIX${table}_update", "CREATE TRIGGER `$PREFIX${table}_update` AFTER UPDATE ${updateOf}ON `$table` $bump")
            put("$PREFIX${table}_delete", "CREATE TRIGGER `$PREFIX${table}_delete` AFTER DELETE ON `$table` $bump")
        }
    }
}

internal fun SQLiteConnection.queryStrings(sql: String): List<String> = prepare(sql).use { statement ->
    buildList { while (statement.step()) add(statement.getText(0)) }
}

internal fun SQLiteConnection.queryPairs(sql: String): List<Pair<String, String>> = prepare(sql).use { statement ->
    buildList { while (statement.step()) add(statement.getText(0) to statement.getText(1)) }
}

internal fun SQLiteConnection.queryLong(sql: String): Long = prepare(sql).use { statement ->
    check(statement.step()) { "No row: $sql" }
    statement.getLong(0)
}
