# Backed-up preferences live in the database

Orbit's preferences come in two groups. **Backed up**: Appearance and colour source, Primary calendar, "Use Latin digits", Reminder time, default lead times, the Backup nudge interval, and the People-list sort. **Device-bound**: App lock settings, the remembered Backup passphrase, the "last Backup" date and passphrase-check count, Reminder health, and notification state. The backed-up group lives in a single-row Room table and the device-bound group lives in DataStore. A Backup's `VACUUM INTO` snapshot then carries the backed-up preferences automatically, and a Restore stays one atomic database swap. Keeping both groups in DataStore would be the usual Android choice, but it would make Backup copy a second file and make Restore a two-phase swap that can fail halfway. Language is the one exception: AppCompat per-app locales store it themselves, so the Backup manifest records the locale and Restore applies it.

## Consequences

- Reading the theme and colour source at startup goes through Room, so the splash screen stays up until that first read completes.
- Moving a preference between the two groups changes what a Backup contains, so it bumps `manifest.backupFormat` (see [ADR 0004](0004-backups-are-age-files.md)).
