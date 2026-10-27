package dev.alializadeh.orbit

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** All time comes from here, so tests can pin it with a fixed clock. */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun provideAppVersion(): AppVersion =
        AppVersion(name = BuildConfig.VERSION_NAME, code = BuildConfig.VERSION_CODE.toLong())

    @Provides
    fun provideAppLocales(): AppLocales = AppCompatAppLocales
}
