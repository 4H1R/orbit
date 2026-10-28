package dev.alializadeh.orbit.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.alializadeh.orbit.interaction.InteractionType
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Device-bound state (ADR 0006): what belongs to this phone and never travels in a Backup. Kept in
 * a typed DataStore as JSON. Later slices add App lock settings and notification state as fields
 * with defaults.
 */
@Serializable
data class DeviceState(
    /** Welcome (and the rest of first run) has been finished once on this device. */
    val firstRunFinished: Boolean = false,
    /** The type last chosen on the Log interaction sheet, its default next time. */
    val lastInteractionType: InteractionType = InteractionType.Call,
    /**
     * The Backup passphrase, encrypted with this phone's Android Keystore key (see
     * `PassphraseCipher`); null until the first Backup sets one up.
     */
    val wrappedBackupPassphrase: String? = null,
    /** The last Backup made (or restored) on this device; null when there has never been one. */
    val lastBackup: LastBackup? = null,
    /** Backups made on this device; every third one asks for the passphrase first. */
    val backupCount: Int = 0,
    /** [backupCount] when the passphrase was last typed (setup, check, change or Restore). */
    val passphraseTypedAtBackupCount: Int = 0,
    /** "Not now" on the Backup nudge hides it until this day (epoch day). */
    val backupNudgeHiddenUntilEpochDay: Long? = null,
)

/**
 * When the last Backup was made, and the database's data version (`DataVersion`) it holds: the data
 * has changed since when the live version differs.
 */
@Serializable
data class LastBackup(
    val createdAtEpochMillis: Long,
    val dataVersion: Long,
)

object DeviceStateSerializer : Serializer<DeviceState> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // An enum value this version doesn't know falls back to the field's default, rather than
        // the whole state being unreadable (and replaced, first run included).
        coerceInputValues = true
    }

    override val defaultValue: DeviceState = DeviceState()

    override suspend fun readFrom(input: InputStream): DeviceState = try {
        json.decodeFromString(DeviceState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable device state", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable device state", e)
    }

    override suspend fun writeTo(t: DeviceState, output: OutputStream) {
        output.write(json.encodeToString(DeviceState.serializer(), t).encodeToByteArray())
    }

    const val FILE_NAME = "device_state.json"
}
