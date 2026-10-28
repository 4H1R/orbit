package dev.alializadeh.orbit

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.about.CrashLog
import dev.alializadeh.orbit.language.AppCompatAppLanguage
import dev.alializadeh.orbit.language.AppLanguage
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** All time comes from here, so tests can pin it with a fixed clock. */
    @Provides
    fun provideClock(): Clock = SystemZoneClock

    @Provides
    fun provideAppVersion(): AppVersion =
        AppVersion(name = BuildConfig.VERSION_NAME, code = BuildConfig.VERSION_CODE.toLong())

    @Provides
    fun provideAppLocales(): AppLocales = AppCompatAppLocales

    @Provides
    fun provideAppLanguage(): AppLanguage = AppCompatAppLanguage()

    /** Work that outlives any screen (a reconcile, a notification action), for the whole process. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** CPU work kept off the main thread (building the search index); tests pass their own. */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /** Small file work kept off the main thread (the crash log); tests pass their own. */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** The local crash log, `filesDir/crash/orbit-crash-log.txt` (Settings → About shares it). */
    @Provides
    @Singleton
    fun provideCrashLog(@ApplicationContext context: Context, version: AppVersion, clock: Clock): CrashLog =
        CrashLog(File(File(context.filesDir, CrashLog.DIRECTORY), CrashLog.FILE_NAME), version, clock)
}

/** [Dispatchers.IO], injectable so tests can run the work on their own dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** [Dispatchers.Default], injectable so tests can run the work on their own dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** The process-wide [CoroutineScope]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The system clock in the phone's zone **as it is now**: `Clock.systemDefaultZone()` keeps the zone
 * it was created with, so a singleton holding it would miss a time-zone change (reminders are local
 * times, ticket 02).
 */
object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
