package dev.alializadeh.orbit.firstrun

import android.content.Context
import android.content.res.Configuration
import androidx.room3.withWriteTransaction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.SeedRing
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What the user chose on Welcome. */
data class WelcomeChoices(
    val language: OrbitLanguage,
    val primaryCalendar: CalendarSystem,
    /** Only meaningful for Persian; English always shows Latin digits. */
    val useLatinDigits: Boolean,
)

/** The seed Ring names in a given language, whatever the current UI language is. */
fun interface SeedRingNames {
    fun nameOf(ring: SeedRing, language: OrbitLanguage): String
}

class ResourceSeedRingNames @Inject constructor(
    @ApplicationContext private val context: Context,
) : SeedRingNames {
    override fun nameOf(ring: SeedRing, language: OrbitLanguage): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(language.tag))
        }
        return context.createConfigurationContext(configuration).getString(ring.nameRes)
    }
}

/**
 * Finishing first run: stores the Welcome choices, creates the seed Rings once (in the chosen
 * language; they're user data from then on), then marks first run finished on this device.
 *
 * Safe to repeat: if the process dies before the flag is written, Welcome shows again and
 * finishing again doesn't add a second set of Rings.
 */
class FirstRunRepository @Inject constructor(
    private val database: OrbitDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val seedRingNames: SeedRingNames,
) {

    suspend fun finish(choices: WelcomeChoices) {
        // A Restore brought its own preferences and Rings: Welcome's choices never replace them.
        if (restoredDuringFirstRun()) return finishAfterRestore()
        val ringDao = database.ringDao()
        val preferencesDao = database.backedUpPreferencesDao()
        database.withWriteTransaction {
            preferencesDao.update(
                preferencesDao.get().copy(
                    primaryCalendar = choices.primaryCalendar,
                    useLatinDigits = choices.useLatinDigits,
                ),
            )
            if (ringDao.count() == 0) {
                ringDao.insertAll(
                    SeedRing.entries.mapIndexed { position, seed ->
                        Ring(
                            name = seedRingNames.nameOf(seed, choices.language),
                            position = position,
                            cadence = seed.cadence,
                        )
                    },
                )
            }
        }
        preferencesRepository.updateDeviceState { it.copy(firstRunFinished = true) }
    }

    /**
     * Finishing first run after a Restore on Welcome: the Backup brought its own preferences and
     * Rings, so only this device's flag is set (no Welcome choices, no seed Rings).
     */
    suspend fun finishAfterRestore() {
        preferencesRepository.updateDeviceState { it.copy(firstRunFinished = true) }
    }

    /** Whether a Restore has replaced the data during this device's first run. */
    suspend fun restoredDuringFirstRun(): Boolean = preferencesRepository.deviceState.first().restoredDuringFirstRun

    /** The App lock offer's "Turn on", after the unlock. Device-bound, like the flag. */
    suspend fun turnOnAppLock() {
        preferencesRepository.updateDeviceState { it.copy(appLock = it.appLock.copy(enabled = true)) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface FirstRunModule {

    @Binds
    fun bindSeedRingNames(names: ResourceSeedRingNames): SeedRingNames
}
