package dev.alializadeh.orbit.backup

import java.time.LocalDate

/*
 * A Backup is an age v1 file (ADR 0004): one scrypt passphrase recipient wrapping a ZIP of
 *
 *   manifest.json   always the first entry (BackupManifest)
 *   db/orbit.db     a VACUUM INTO snapshot of the database
 *   photos/         the photos the snapshot references (none until slice 2)
 *
 * Anyone can open one without Orbit: `age -d orbit-backup-2026-10-28.age > backup.zip`.
 */

/** Orbit's own version of the ZIP's content. Bump it whenever what a Backup holds changes. */
const val BACKUP_FORMAT = 1

/** scrypt cost (log2 N) for new Backups: 128 MiB of memory, measured on devices by the androidTest ScryptMeasurement. */
const val BACKUP_WRITE_WORK_FACTOR = 17

/** The highest scrypt cost a Restore accepts: 256 MiB, as age's own default. */
const val BACKUP_MAX_READ_WORK_FACTOR = 18

const val BACKUP_MIME_TYPE = "application/octet-stream"

internal const val MANIFEST_ENTRY = "manifest.json"
internal const val DATABASE_ENTRY = "db/orbit.db"
internal const val PHOTOS_DIRECTORY = "photos/"

/** Only these entry names may appear in a Backup's ZIP, so nothing can escape a staging folder. */
private val PHOTO_ENTRY = Regex("""photos/[A-Za-z0-9][A-Za-z0-9._-]*""")

internal fun isAllowedEntryPath(path: String): Boolean =
    path == DATABASE_ENTRY || PHOTO_ENTRY.matches(path)

/** The Save picker's suggestion: always an ISO Gregorian date in Latin digits. */
fun suggestedBackupFileName(date: LocalDate): String = "orbit-backup-$date.age"
