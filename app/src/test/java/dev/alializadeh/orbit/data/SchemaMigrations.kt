package dev.alializadeh.orbit.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Migration tests on the plain JVM, from the schemas Room exports to `app/schemas/` (committed
 * with every version, the frozen record of what each version's database looks like).
 *
 * [createDatabase] builds a database file exactly as version N left it, from N's exported JSON
 * (tables, indices, Room's identity hash, `user_version`), and lets the test put rows in it.
 * [openWithRoom] then opens it the way the app does: Room runs [OrbitDatabase.MIGRATIONS] up to the
 * current version and validates the result against the compiled schema, throwing on any mismatch.
 *
 * (room3-testing's `MigrationTestHelper` targets a pure JVM Room; this project's unit tests run
 * Room's Android artifact, so this small helper does the same job.)
 */
object SchemaMigrations {

    /** Unit tests run in the module folder. */
    private val schemaDirectory = File("schemas/${OrbitDatabase::class.qualifiedName}")

    /** Every exported version, oldest first. */
    fun exportedVersions(): List<Int> =
        schemaDirectory.listFiles { file -> file.extension == "json" }.orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()

    fun identityHash(version: Int): String =
        schema(version)["identityHash"]!!.jsonPrimitive.content

    /** Creates [file] as a version-[version] database, then runs [seed] on it (raw SQL, that version's tables). */
    fun createDatabase(version: Int, file: File, seed: (SQLiteConnection) -> Unit = {}) {
        val schema = schema(version)
        BundledSQLiteDriver().open(file.path).use { connection ->
            for (entity in schema["entities"]!!.jsonArray.map { it.jsonObject }) {
                val table = entity["tableName"]!!.jsonPrimitive.content
                connection.execSQL(entity["createSql"]!!.jsonPrimitive.content.withTable(table))
                (entity["indices"] as? JsonArray).orEmpty().forEach { index ->
                    connection.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.withTable(table))
                }
            }
            (schema["views"] as? JsonArray).orEmpty().forEach { view ->
                val name = view.jsonObject["viewName"]!!.jsonPrimitive.content
                connection.execSQL(view.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${VIEW_NAME}", name))
            }
            schema["setupQueries"]!!.jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL("PRAGMA user_version = $version")
            seed(connection)
        }
    }

    /** Opens [file] as the app would: migrating to [OrbitDatabase.VERSION] and validating the schema. */
    fun openWithRoom(file: File): OrbitDatabase = TestDatabases.onDisk(file)

    private fun schema(version: Int): JsonObject {
        val file = File(schemaDirectory, "$version.json")
        check(file.isFile) { "No exported schema for version $version: $file" }
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun String.withTable(table: String) = replace("\${TABLE_NAME}", table)
}
