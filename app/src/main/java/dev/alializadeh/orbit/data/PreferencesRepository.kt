package dev.alializadeh.orbit.data

import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.room3.withWriteTransaction
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ColourSwatches
import dev.alializadeh.orbit.ui.theme.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Orbit's preferences: the backed-up ones from the database row and the device-bound ones from
 * DataStore (ADR 0006). The UI language isn't here; see `AppLanguage`.
 */
interface PreferencesRepository {

    val preferences: Flow<BackedUpPreferences>

    val deviceState: Flow<DeviceState>

    suspend fun setTheme(theme: ThemeMode)

    suspend fun setColourSource(source: ColourSource)

    /** Chooses "Your colour" with [argb] as its seed. */
    suspend fun setCustomColour(argb: Int)

    suspend fun setPrimaryCalendar(calendar: CalendarSystem)

    suspend fun setUseLatinDigits(useLatinDigits: Boolean)

    /** Applies [transform] to the backed-up row in one write transaction. */
    suspend fun update(transform: (BackedUpPreferences) -> BackedUpPreferences)

    suspend fun updateDeviceState(transform: (DeviceState) -> DeviceState)
}

class RoomPreferencesRepository @Inject constructor(
    private val database: OrbitDatabase,
    private val deviceStateStore: DataStore<DeviceState>,
) : PreferencesRepository {

    private val dao = database.backedUpPreferencesDao()

    override val preferences: Flow<BackedUpPreferences> = dao.observe()

    override val deviceState: Flow<DeviceState> = deviceStateStore.data

    override suspend fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }

    override suspend fun setColourSource(source: ColourSource) = update {
        if (source == ColourSource.Custom && it.customSeedArgb == null) {
            it.copy(colourSource = source, customSeedArgb = DEFAULT_CUSTOM_SEED)
        } else {
            it.copy(colourSource = source)
        }
    }

    override suspend fun setCustomColour(argb: Int) =
        update { it.copy(colourSource = ColourSource.Custom, customSeedArgb = argb) }

    override suspend fun setPrimaryCalendar(calendar: CalendarSystem) =
        update { it.copy(primaryCalendar = calendar) }

    override suspend fun setUseLatinDigits(useLatinDigits: Boolean) =
        update { it.copy(useLatinDigits = useLatinDigits) }

    override suspend fun update(transform: (BackedUpPreferences) -> BackedUpPreferences) {
        database.withWriteTransaction { dao.update(transform(dao.get())) }
    }

    override suspend fun updateDeviceState(transform: (DeviceState) -> DeviceState) {
        deviceStateStore.updateData(transform)
    }

    private companion object {
        /** "Your colour" chosen before any seed was picked starts on the blue swatch. */
        val DEFAULT_CUSTOM_SEED: Int = ColourSwatches[6].toArgb()
    }
}
