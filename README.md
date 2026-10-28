# Orbit

A personal, offline relationship manager for one person on one Android phone. Orbit remembers the people in your life, what matters to them (birthdays and other important dates, gift ideas, likes, who can help with what) and when to reach out, in English and Persian, with dates in Gregorian and Solar Hijri. There's no account, no server and no `INTERNET` permission: everything stays on the phone, and an encrypted Backup file (an [age](https://age-encryption.org) file you save wherever you like) is how it survives a lost phone.

Native Android (Kotlin, Jetpack Compose, Material 3 Expressive), minSdk 31, sideloaded rather than published.

## Where the spec lives

- [docs/prd-v1.md](docs/prd-v1.md): what v1 is, with the build order.
- [GLOSSARY.md](GLOSSARY.md): the domain words (Person, Ring, Cadence, Due…) and their Persian UI words.
- [docs/copy-deck-v1.md](docs/copy-deck-v1.md): how Orbit's English and Persian are written.
- [docs/adr/](docs/adr/): the decisions that are hard to reverse.
- [docs/notes/device-checklist.md](docs/notes/device-checklist.md): the manual checks on a real phone.
- [CLAUDE.md](CLAUDE.md): commands, code layout and conventions for working on the code.

## Build

Use Android Studio's JDK 21 and an Android SDK whose path is in `local.properties` (`sdk.dir=…`). In Git Bash on Windows:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug                      # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest lintDebug verifyRoborazziDebug   # tests, lint, screenshots
```

## Install a debug build

The debug build is `dev.alializadeh.orbit.debug` ("Orbit" with its own icon), so it installs next to the real app without touching its data. With the phone connected over USB debugging (or an emulator running):

```bash
./gradlew installDebug
```

## Release builds

The release app's identity is permanent ([ADR 0005](docs/adr/0005-orbits-app-identity-is-permanent.md)): Android only installs an update signed with the same key, and losing the key means uninstalling, which deletes everything not in a Backup.

1. **Once, ever**: create the release keystore outside the repository.

   ```bash
   scripts/generate-release-keystore.sh          # ~/.orbit/orbit-release.jks + keystore.properties
   ```

   Then, before anything else, put the keystore file and the printed password in the password manager, and keep a second offline copy of both. `keystore.properties` and `*.jks` are gitignored; never commit them. On a new computer, restore the keystore from the password manager and recreate `keystore.properties` (the script shows its four lines) instead of running the script again.

2. **Each release**: raise `versionCode` (and `versionName`) in `app/build.gradle.kts`, then

   ```bash
   ./gradlew assembleRelease                     # app/build/outputs/apk/release/app-release.apk, signed
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

   `install -r` keeps the app's data. Keep `app/build/outputs/mapping/release/mapping.txt` with each release: it turns the shrunk class names in a shared crash log (Settings → About) back into readable ones (`retrace` in the Android SDK's command-line tools).

Without `keystore.properties` the release APK is built unsigned and can't be installed.
