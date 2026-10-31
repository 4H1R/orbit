# Orbit

**A personal, offline relationship manager for Android, in English and Persian.**

Orbit remembers the people in your life: their birthdays and other important dates, gift ideas, what they like, what they can help with, and when you last talked. It tells you who you haven't been in touch with for a while. There's no account, no server and no internet permission. Everything stays on your phone.

<p align="center">
  <img src="app/src/test/screenshots/orbit_few_en_dark.png" width="200" alt="The Orbit view: People placed on rings around You, closest inside">
  <img src="app/src/test/screenshots/people_list_en_light.png" width="200" alt="The People list with search, filters and Due badges">
  <img src="app/src/test/screenshots/person_detail_due_fa_light.png" width="200" alt="A Person's page in Persian, with Solar Hijri dates">
  <img src="app/src/test/screenshots/wishes_sheet_en_light.png" width="200" alt="Sending birthday wishes through WhatsApp">
</p>

## Features

- **People and Rings.** Put each Person in a Ring (Inner, Close, Friends, Acquaintances, or your own) that sets how often you want to be in touch. The Orbit view draws everyone around you, closest in the middle.
- **Keep in touch.** Log calls, messages and meetups. Once it's been longer than a Person's Cadence they're **Due**, until you get in touch or snooze them.
- **Important dates and Occasions.** Birthdays, anniversaries and any date you choose, plus Occasions like Nowruz or Mother's Day for a group of people, each with reminders.
- **Reminders and a home-screen widget.** Notifications on the day or ahead of it, a daily digest of who's Due, and an Upcoming widget.
- **Send wishes.** A ready-made message in English or Persian, sent by SMS, WhatsApp, Telegram, Signal, email or any other app, and logged for you.
- **Gift ideas** for each Person, kept until you mark them as given.
- **Follow-ups.** Note what to come back to with someone ("ask how the interview went") and when, on its own or as you log an interaction. It comes up on Upcoming and as a notification on the day.
- **Lent and borrowed.** Keep track of what you've lent or borrowed, with an optional day for it to come back.
- **Add from contacts.** Start a new Person from the phone's contact picker. Orbit asks for no contacts permission and only sees the one name and number you pick.
- **Search and filters.** Search names, notes and details, Persian or English, and filter by Ring, Tag, Due or an upcoming date. Search for a skill and the people who can help with it come first.
- **Three calendars.** Gregorian, Solar Hijri and Lunar Hijri. Each date stays in the calendar it was entered in, so a Persian birthday comes round on the right day every year.
- **Fully bilingual.** English and Persian, right to left, with Persian digits.
- **Private by design.** An optional App lock uses your phone's screen lock. Notifications never show names on the lock screen, and with App lock on they don't show them anywhere.
- **Encrypted backups.** A Backup is a passphrase-protected [age](https://age-encryption.org) file that you save wherever you like. It's how your data survives a lost phone.

## Getting Orbit

Orbit isn't on any app store. Download an APK from the [latest release](https://github.com/4H1R/orbit/releases/latest) and open it on your phone (Android asks you to allow installs from your browser or file manager once). It needs Android 12 or later.

| Your phone | File |
| --- | --- |
| Almost every phone from the last several years (64-bit ARM) | `orbit-<version>-arm64-v8a.apk` |
| Older or low-end phones with a 32-bit ARM chip | `orbit-<version>-armeabi-v7a.apk` |
| Emulators, Chromebooks and other Intel/AMD devices | `orbit-<version>-x86_64.apk` |
| Not sure | `orbit-<version>-universal.apk` (works everywhere, a bit larger) |

Installing a newer release over an older one keeps your data. You can also build Orbit yourself (below).

## Building

You need Android Studio's JDK 21 and an Android SDK, with its path in `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

Then, in Git Bash on Windows (or any shell elsewhere):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

./gradlew assembleDebug          # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # installs it on a connected phone or a running emulator
./gradlew testDebugUnitTest lintDebug verifyRoborazziDebug   # unit tests, lint, screenshot tests
```

The debug build is a separate app (`dev.alializadeh.orbit.debug`, with its own icon), so it installs next to the real one without touching its data.

## Release builds

> [!WARNING]
> The release app's identity is permanent ([ADR 0005](docs/adr/0005-orbits-app-identity-is-permanent.md)). Android only installs an update signed with the same key. Lose the key and the only way forward is to uninstall, which deletes everything that isn't in a Backup.

### Once: create the signing key

```bash
scripts/generate-release-keystore.sh   # creates ~/.orbit/orbit-release.jks and keystore.properties
```

Before doing anything else, put the keystore file and the printed password in a password manager, and keep a second offline copy of both. `keystore.properties` and `*.jks` are gitignored. Never commit them.

Then give GitHub the key so it can sign releases, as two repository secrets:

```bash
base64 -w0 ~/.orbit/orbit-release.jks | gh secret set ORBIT_KEYSTORE_BASE64
gh secret set ORBIT_KEYSTORE_PASSWORD   # paste the printed password
```

On a new computer, don't run the script again. Restore the keystore from the password manager and recreate `keystore.properties` instead (the script shows its four lines).

### Each release

Tag the commit and push the tag:

```bash
git tag v1.2.0
git push origin v1.2.0
```

The [Release workflow](.github/workflows/release.yml) runs the unit tests and lint, then builds signed APKs for each chipset plus a universal one. It publishes them as a GitHub Release, with checksums and the `mapping.txt` that turns the shrunk class names in a shared crash log (Settings → About) back into readable ones (`retrace` in the Android SDK's command-line tools). The tag sets the version: `v1.2.0` is versionName `1.2.0` and versionCode `10200` (`MAJOR*10000 + MINOR*100 + PATCH`), so every tag must be higher than the last.

To try the workflow without publishing anything, run it from the Actions tab (Release → Run workflow). The APKs are then a downloadable artifact of that run.

### A local release build

```bash
./gradlew assembleRelease -Porbit.versionName=1.2.0 -Porbit.versionCode=10200
adb install -r app/build/outputs/apk/release/app-release.apk
```

`install -r` keeps the app's data. Without `-Porbit.versionCode` the build has versionCode 1, which Android won't install over a newer release. Without `keystore.properties` the APK is unsigned and won't install at all. Add `-Porbit.abiSplits=true` for the per-chipset APKs.

## Tech stack

Kotlin, Jetpack Compose and Material 3 Expressive, Navigation 3, Room, Hilt, Glance for the widget, and [kage](https://github.com/android-password-store/kage) for age encryption. It's a single `:app` module, organised by feature. Tests are JVM unit tests plus Robolectric and Roborazzi screenshot tests, in both languages and in light and dark themes.

## Project docs

- [GLOSSARY.md](GLOSSARY.md): the domain words (Person, Ring, Cadence, Due…) and their Persian UI words.
- [docs/adr/](docs/adr/): the decisions that are hard to reverse, such as [Android only, no sync](docs/adr/0001-android-only-single-device-no-sync.md), [encrypting Backups and not the database](docs/adr/0002-encrypt-backups-not-the-on-device-database.md) and [dates keeping their own calendar](docs/adr/0003-important-dates-keep-their-own-calendar.md).
- [CLAUDE.md](CLAUDE.md): commands, code layout and conventions for working on the code.
