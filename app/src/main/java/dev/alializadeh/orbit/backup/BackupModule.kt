package dev.alializadeh.orbit.backup

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dev.alializadeh.orbit.AppLocales
import dev.alializadeh.orbit.AppVersion
import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.person.photo.FilesPhotoStore
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
        photosDir = File(context.filesDir, FilesPhotoStore.DIRECTORY),
    )

    @Provides
    fun provideBackupReader(): BackupReader = BackupReader(currentDbSchemaVersion = OrbitDatabase.VERSION)

    @Provides
    fun provideRestorer(
        database: OrbitDatabase,
        reader: BackupReader,
        preferences: PreferencesRepository,
        passphrases: BackupPassphraseStore,
        appLocales: AppLocales,
        listeners: Set<@JvmSuppressWildcards RestoreListener>,
        notices: RestoreNotices,
        stagedDatabaseCheck: StagedDatabaseCheck,
        @ApplicationContext context: Context,
    ): Restorer = Restorer(
        database = database,
        reader = reader,
        preferences = preferences,
        passphrases = passphrases,
        appLocales = appLocales,
        listeners = listeners,
        notices = notices,
        stagedDatabaseCheck = stagedDatabaseCheck,
        workDir = File(context.noBackupFilesDir, "restore"),
        photosDir = File(context.filesDir, FilesPhotoStore.DIRECTORY),
    )
}

@Module
@InstallIn(SingletonComponent::class)
interface BackupBindings {

    @Binds
    fun bindPassphraseCipher(cipher: KeystorePassphraseCipher): PassphraseCipher

    @Binds
    fun bindBackupDocuments(documents: ContentResolverBackupDocuments): BackupDocuments

    @Binds
    fun bindBackupStatusRepository(repository: RoomBackupStatusRepository): BackupStatusRepository

    @Binds
    fun bindStagedDatabaseCheck(check: RoomStagedDatabaseCheck): StagedDatabaseCheck

    /** Slice 6 adds its reminder reconcile with `@Binds @IntoSet`. */
    @Multibinds
    fun restoreListeners(): Set<RestoreListener>
}
