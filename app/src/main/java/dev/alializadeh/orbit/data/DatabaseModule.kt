package dev.alializadeh.orbit.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.person.PersonDao
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
    fun provideBackedUpPreferencesDao(database: OrbitDatabase): BackedUpPreferencesDao =
        database.backedUpPreferencesDao()
}
