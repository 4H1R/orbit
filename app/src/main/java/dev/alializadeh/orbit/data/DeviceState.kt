package dev.alializadeh.orbit.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Device-bound state (ADR 0006): what belongs to this phone and never travels in a Backup. Kept in
 * a typed DataStore as JSON. Later slices add App lock settings, the wrapped Backup passphrase, the
 * "last Backup" date and notification state as fields with defaults.
 */
@Serializable
data class DeviceState(
    /** Welcome (and the rest of first run) has been finished once on this device. */
    val firstRunFinished: Boolean = false,
)

object DeviceStateSerializer : Serializer<DeviceState> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
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
