package dev.alializadeh.orbit.backup

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alializadeh.orbit.AppLocales
import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.data.OrbitDatabase
import java.io.File
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    fun provideDatabaseSnapshotter(snapshotter: RoomDatabaseSnapshotter): DatabaseSnapshotter =
        snapshotter

    @Provides
    fun provideBackupWriter(
        snapshotter: DatabaseSnapshotter,
        clock: Clock,
        appVersion: AppVersion,
        appLocales: AppLocales,
        @ApplicationContext context: Context,
    ): BackupWriter = BackupWriter(
        snapshotter = snapshotter,
        clock = clock,
        appVersion = appVersion,
        appLocales = appLocales,
        // App-private and excluded from every kind of Android backup.
        workDir = File(context.noBackupFilesDir, "backup-work"),
    )

    @Provides
    fun provideBackupReader(): BackupReader = BackupReader(currentDbSchemaVersion = OrbitDatabase.VERSION)
}
