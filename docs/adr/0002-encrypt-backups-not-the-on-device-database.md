# Encrypt Backups, not the on-device database

Only the Backup file is encrypted with a user passphrase; the on-device database relies on Android's file-based encryption plus Orbit's App lock. Encrypting the live database (e.g. SQLCipher) would add key management and a native dependency to defend against rooted or forensic access, which is not part of the threat model for a personal sideloaded app; the Backup is the copy that leaves the device, so that's where the passphrase goes.
