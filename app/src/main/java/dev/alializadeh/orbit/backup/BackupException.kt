package dev.alializadeh.orbit.backup

/** Why a Backup couldn't be read. */
sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The passphrase doesn't open the file (copy deck: `restore_wrong_passphrase`). */
    class WrongPassphrase(cause: Throwable? = null) :
        BackupException("The passphrase doesn't open this backup", cause)

    /** Not an age file, or an age file that isn't an Orbit Backup. */
    class NotABackup(message: String, cause: Throwable? = null) : BackupException(message, cause)

    /** Made by a newer Orbit (copy deck: `restore_newer_version`). */
    class NewerVersion(message: String) : BackupException(message)

    /** An Orbit Backup that's damaged: truncated, tampered with, or failing its hashes. */
    class Corrupt(message: String, cause: Throwable? = null) : BackupException(message, cause)
}
