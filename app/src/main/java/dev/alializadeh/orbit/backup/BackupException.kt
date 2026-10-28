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

    /**
     * Its scrypt cost needs more memory than this phone gives Orbit (a Backup written at logN 18,
     * age's own default, on a 192 MiB heap).
     */
    class NeedsMoreMemory(cause: Throwable? = null) :
        BackupException("Not enough memory to open this backup", cause)

    /** An Orbit Backup that's damaged: truncated, tampered with, or failing its hashes. */
    class Corrupt(message: String, cause: Throwable? = null) : BackupException(message, cause)
}
