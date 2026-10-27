# Backups are age files

A Backup is a standard age v1 file (one scrypt passphrase recipient, logN=17) wrapping a ZIP of manifest, database snapshot and photos, written with the pre-1.0 `kage` library rather than a container of our own. Argon2id would be OWASP's first-choice KDF, but age has no Argon2id, and we chose age anyway: anyone can open a Backup on a computer with the `age` CLI even if Orbit is gone, and Orbit owns no crypto format code. For the same reason, Backup passphrases are limited to printable ASCII. Persian keyboards disagree on letters like ی/ي, and `age -d` can't fold them, so a Persian passphrase might not match when typed on a computer.

## Consequences

- Backup files outlive app versions, so the format can't quietly change. The cost can rise (logN is in every file's header) and the ZIP content is versioned by `manifest.backupFormat`, but leaving age would mean reading both formats forever.
- If kage is abandoned we fork it; the spec fixes the format, so existing Backups are never stranded. Tests pin it against the spec's test vectors and the real `age` CLI.
