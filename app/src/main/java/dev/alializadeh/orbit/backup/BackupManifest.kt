package dev.alializadeh.orbit.backup

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/** `manifest.json`, the first entry of every Backup's ZIP. */
@Serializable
data class BackupManifest(
    /** Version of the ZIP's content ([BACKUP_FORMAT]). A newer one is refused. */
    val backupFormat: Int,
    /** The Room schema version of `db/orbit.db`. A newer one is refused. */
    val dbSchemaVersion: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    @Serializable(with = InstantIso8601Serializer::class)
    val createdAt: Instant,
    /**
     * AppCompat per-app locale tags, or null when Orbit followed the system language. Language
     * isn't in the database (ADR 0006), so a Restore applies it from here.
     */
    val locale: String?,
    /** How many People (Archived ones included), Interactions and photos it holds, for the summary. */
    val people: Int = 0,
    val interactions: Int = 0,
    val photos: Int = 0,
    /** Every file in the ZIP except the manifest itself. */
    val entries: List<BackupEntry>,
)

@Serializable
data class BackupEntry(
    val path: String,
    val size: Long,
    /** Lower-case hex SHA-256 of the entry's bytes. */
    val sha256: String,
)

internal val BackupJson = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

internal object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.alializadeh.orbit.backup.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
