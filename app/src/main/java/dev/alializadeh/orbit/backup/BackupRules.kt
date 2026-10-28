package dev.alializadeh.orbit.backup

import dev.alializadeh.orbit.data.DeviceState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Where Backups stand on this device: what the nudge, Settings → Backup and Restore need. */
data class BackupStatus(
    /** When the last Backup was made (or the restored Backup's own date); null if never. */
    val lastBackupAt: Instant?,
    /** The data differs from what the last Backup holds (always true when there has been none). */
    val dataChanged: Boolean,
    /** Every Person on the device, Archived ones included. */
    val peopleCount: Int,
    val nudgeInterval: BackupNudgeInterval,
    val nudgeHiddenUntil: LocalDate?,
    val hasPassphrase: Boolean,
) {
    companion object {
        fun of(
            device: DeviceState,
            dataVersion: Long,
            peopleCount: Int,
            nudgeInterval: BackupNudgeInterval,
        ) = BackupStatus(
            lastBackupAt = device.lastBackup?.let { Instant.ofEpochMilli(it.createdAtEpochMillis) },
            dataChanged = device.lastBackup?.dataVersion != dataVersion,
            peopleCount = peopleCount,
            nudgeInterval = nudgeInterval,
            nudgeHiddenUntil = device.backupNudgeHiddenUntilEpochDay?.let(LocalDate::ofEpochDay),
            hasPassphrase = device.wrappedBackupPassphrase != null,
        )
    }
}

/** The Backup rules that aren't I/O: the nudge and the every-third passphrase check. */
object BackupRules {

    /** "Not now" hides the nudge for this many days. */
    const val NUDGE_SNOOZE_DAYS = 7L

    /** Every this-many-th Backup asks for the passphrase first. */
    const val CHECK_EVERY = 3

    /**
     * Whether Upcoming shows the Backup nudge [today]: not while "Not now" hides it, never with the
     * interval Off; otherwise when there has never been a Backup and at least one Person exists, or
     * when the last Backup is at least the interval old and the data has changed since.
     */
    fun showsNudge(status: BackupStatus, today: LocalDate, zone: ZoneId): Boolean {
        if (status.nudgeHiddenUntil != null && today < status.nudgeHiddenUntil) return false
        val intervalDays = status.nudgeInterval.days ?: return false
        val last = status.lastBackupAt ?: return status.peopleCount > 0
        val lastDay = last.atZone(zone).toLocalDate()
        return status.dataChanged && ChronoUnit.DAYS.between(lastDay, today) >= intervalDays
    }

    /** The nudge after "Not now" [today]. */
    fun hideNudge(device: DeviceState, today: LocalDate): DeviceState =
        device.copy(backupNudgeHiddenUntilEpochDay = today.plusDays(NUDGE_SNOOZE_DAYS).toEpochDay())

    /**
     * Whether the next Backup asks for the passphrase first: every third Backup made on this device
     * (the 3rd, 6th, 9th…), unless it was already typed for this one (a check passed, or a new
     * passphrase set, before a Save picker that was then cancelled).
     */
    fun passphraseCheckDue(device: DeviceState): Boolean =
        (device.backupCount + 1) % CHECK_EVERY == 0 && device.passphraseTypedAtBackupCount != device.backupCount

    /** The passphrase was just typed (setup, a check, a change, a Restore). */
    fun passphraseTyped(device: DeviceState): DeviceState =
        device.copy(passphraseTypedAtBackupCount = device.backupCount)
}
