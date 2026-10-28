package dev.alializadeh.orbit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.interaction.InteractionDao
import dev.alializadeh.orbit.person.PersonDao
import dev.alializadeh.orbit.person.TagDao
import dev.alializadeh.orbit.ring.RingDao
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Room 3 on the bundled SQLite driver: the same SQLite (with `VACUUM INTO`) on every phone and
     * in the JVM tests.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OrbitDatabase =
        Room.databaseBuilder<OrbitDatabase>(context, OrbitDatabase.FILE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(OrbitDatabase.SeedCallback)
            .build()

    @Provides
    fun providePersonDao(database: OrbitDatabase): PersonDao = database.personDao()

    @Provides
    fun provideTagDao(database: OrbitDatabase): TagDao = database.tagDao()

    @Provides
    fun provideRingDao(database: OrbitDatabase): RingDao = database.ringDao()

    @Provides
    fun provideInteractionDao(database: OrbitDatabase): InteractionDao = database.interactionDao()

    /** Device-bound state (ADR 0006), in app storage that no Android backup includes. */
    @Provides
    @Singleton
    fun provideDeviceStateStore(@ApplicationContext context: Context): DataStore<DeviceState> =
        DataStoreFactory.create(
            serializer = DeviceStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { DeviceState() },
            produceFile = { context.dataStoreFile(DeviceStateSerializer.FILE_NAME) },
        )
}

@Module
@InstallIn(SingletonComponent::class)
interface PreferencesModule {

    @Binds
    fun bindPreferencesRepository(repository: RoomPreferencesRepository): PreferencesRepository
}
