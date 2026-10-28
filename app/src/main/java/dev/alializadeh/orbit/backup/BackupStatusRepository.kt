package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.data.OrbitDatabase
import dev.alializadeh.orbit.data.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Where Backups stand, live: device-bound bookkeeping against the database's data version. */
interface BackupStatusRepository {
    val status: Flow<BackupStatus>
}

class RoomBackupStatusRepository @Inject constructor(
    database: OrbitDatabase,
    preferences: PreferencesRepository,
) : BackupStatusRepository {

    private val dao = database.backupStatusDao()

    override val status: Flow<BackupStatus> = combine(
        preferences.deviceState,
        preferences.preferences,
        dao.observeDataVersion(),
        dao.observePeopleCount(),
    ) { device, backedUp, dataVersion, people ->
        BackupStatus.of(device, dataVersion, people, backedUp.backupNudgeInterval)
    }.distinctUntilChanged()
}
