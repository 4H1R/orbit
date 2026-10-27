# Android-only, single device, no sync

Orbit is a personal tool for one user on one Android phone, built natively with Kotlin, Jetpack Compose and Material 3 (minSdk 31) and sideloaded rather than published to the Play Store. There are no accounts, no server and no sync: data moves between devices only through a passphrase-encrypted Backup that replaces everything on restore. Compose Multiplatform, cloud sync and merge-on-restore were rejected because they multiply complexity for a single user who only needs to survive a phone change.
