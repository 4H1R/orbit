# scrypt cost measurement (Backup KDF)

Backups use age's scrypt recipient at **logN = 17** (N = 2^17, r = 8, p = 1, 128 MiB), and Restore accepts logN ≤ 18 ([ADR 0004](../adr/0004-backups-are-age-files.md), [Backup and restore behaviour](../../.scratch/orbit-v1/issues/09-backup-and-restore-behaviour.md)). kage runs scrypt in pure Java (Bouncy Castle), so all of its memory is Java heap.

**The decision needs a measurement on the user's real phone.** The rule: logN 17 stays unless a Backup or a Restore takes more than about 3 s, or runs out of memory, on that phone; then it drops to 16. The numbers below come from the emulator and only show the benchmark works. They don't settle anything.

## Emulator run (2026-10-28)

Device: Android Emulator `Pixel_9_Pro_API_35` (`sdk_gphone16k_x86_64`, Android 15 / API 35, x86_64 on WHPX). Java heap: `memoryClass` 192 MiB, `largeMemoryClass` 576 MiB, `maxMemory` 192 MiB (Orbit doesn't ask for `largeHeap`).

| What | Time | Peak Java heap |
|---|---|---|
| scrypt logN 16, derive for Backup / Restore | 1003 / 1004 ms (median of 3) | 74 MiB |
| scrypt logN 17, derive for Backup / Restore | 1992 / 1994 ms (median of 3) | 146 MiB |
| scrypt logN 18 | **out of memory** (needs 256 MiB; heap limit 192 MiB) | – |
| 50 MiB encrypt at logN 17 (scrypt + ChaCha20-Poly1305) | 3234 ms | 148 MiB |
| 50 MiB decrypt at logN 17, read and authenticated to the end | 3360 ms | 147 MiB |

What this suggests, to be confirmed on the phone:

- **scrypt dominates**: about 2 s of each 50 MiB operation is the key derivation, and about 1.2–1.4 s is streaming 50 MiB through pure-Java ChaCha20-Poly1305. A real Backup is far smaller than 50 MiB until photos arrive (a few People make a 28 KiB database), so for most Backups the time is the scrypt cost alone.
- On the emulator the 50 MiB round trip is slightly over the ~3 s line at logN 17, and logN 16 would save about 1 s. The emulator's speed says little about a phone's, which is why the phone run decides.
- **logN 18 cannot be read on a 192 MiB heap.** Orbit never writes 18, but Restore's ceiling of 18 only works on phones whose heap allows 256 MiB plus overhead. A Backup made at age's default cost (`age -p` writes 18) would fail to restore on such a phone with an out-of-memory error. Slice 5 should turn that into a clear "this Backup needs more memory than this phone allows" error, or lower the read ceiling to 17. Keep this in mind if logN 17 itself turns out tight: at 146 MiB peak it leaves only ~46 MiB of a 192 MiB heap.

## How to run it on the phone

1. Enable USB debugging on the phone and connect it (`adb devices` lists it). Disconnect or stop other devices and emulators.
2. From the repo root in Git Bash:

   ```bash
   export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
   adb logcat -c
   ./gradlew connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=dev.alializadeh.orbit.backup.ScryptMeasurement
   adb logcat -d -s OrbitScrypt
   ```

   It installs the **debug** app (`dev.alializadeh.orbit.debug`) and its test APK, runs about a minute, then uninstalls both. The real app and its data are never touched.
3. Copy the `OrbitScrypt` lines into a new section of this file, with the phone's model.
4. Apply the rule: if the 50 MiB Backup or Restore line is over about 3 s, or logN 17 runs out of memory, set `BACKUP_WRITE_WORK_FACTOR` to 16 in `app/src/main/java/dev/alializadeh/orbit/backup/BackupFormat.kt` (and update ADR 0004 and the PRD). Otherwise logN 17 stays.

The benchmark is `app/src/androidTest/java/dev/alializadeh/orbit/backup/ScryptMeasurement.kt`. It derives keys at logN 16, 17 and 18 (three runs each, both directions), then encrypts and decrypts 50 MiB of incompressible data (like photos) at logN 17, sampling the used Java heap every 2 ms. An out-of-memory at a given cost is logged as a result rather than failing the run.
