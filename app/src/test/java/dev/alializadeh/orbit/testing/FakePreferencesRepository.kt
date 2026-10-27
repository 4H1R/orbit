package dev.alializadeh.orbit.testing

import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.data.BackedUpPreferences
import dev.alializadeh.orbit.data.DeviceState
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.ui.theme.ColourSource
import dev.alializadeh.orbit.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An in-memory [PreferencesRepository]. */
class FakePreferencesRepository(
    initial: BackedUpPreferences = BackedUpPreferences(),
    initialDeviceState: DeviceState = DeviceState(),
) : PreferencesRepository {

    val backedUp = MutableStateFlow(initial)
    val device = MutableStateFlow(initialDeviceState)

    override val preferences: Flow<BackedUpPreferences> = backedUp
    override val deviceState: Flow<DeviceState> = device

    override suspend fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }

    override suspend fun setColourSource(source: ColourSource) = update { it.copy(colourSource = source) }

    override suspend fun setCustomColour(argb: Int) =
        update { it.copy(colourSource = ColourSource.Custom, customSeedArgb = argb) }

    override suspend fun setPrimaryCalendar(calendar: CalendarSystem) = update { it.copy(primaryCalendar = calendar) }

    override suspend fun setUseLatinDigits(useLatinDigits: Boolean) = update { it.copy(useLatinDigits = useLatinDigits) }

    override suspend fun update(transform: (BackedUpPreferences) -> BackedUpPreferences) {
        backedUp.value = transform(backedUp.value)
    }

    override suspend fun updateDeviceState(transform: (DeviceState) -> DeviceState) {
        device.value = transform(device.value)
    }
}
