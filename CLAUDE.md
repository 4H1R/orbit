# Orbit

A personal, offline relationship manager for one person on one Android phone, in English and Persian. The spec is [docs/prd-v1.md](docs/prd-v1.md) (build order at the end); words are in [GLOSSARY.md](GLOSSARY.md), UI wording rules in [docs/copy-deck-v1.md](docs/copy-deck-v1.md), hard decisions in [docs/adr/](docs/adr/). Slices 0 (walking skeleton) and 1 (language, dates and first run) are done.

## Commands (Git Bash on Windows)

Always use Android Studio's JDK 21 (the system `java` is 23):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug   # the "done" gate
./gradlew verifyRoborazziDebug          # compare screenshots with src/test/screenshots
./gradlew recordRoborazziDebug          # re-record them after an intended UI change (look at the PNGs)
./gradlew installDebug                  # onto the running emulator
./gradlew testDebugUnitTest --tests '*BackupRoundTripTest'
```

- `local.properties` (gitignored) holds `sdk.dir=D\:/Android/Sdk`.
- **age CLI interop test**: `export ORBIT_AGE_CLI=/d/Development/tools/age-dl/age/age.exe` before running tests; without it `AgeCliInteropTest` is skipped. It drives `age` through `age-plugin-batchpass` (same folder), since `age -p` only reads a TTY.
- **Emulator**: AVD `Pixel_9_Pro_API_35`. Start it detached so it outlives the session: `powershell.exe -NoProfile -Command "Start-Process 'D:\Android\Sdk\emulator\emulator.exe' -ArgumentList '-avd','Pixel_9_Pro_API_35','-no-window','-no-audio','-no-boot-anim' -WindowStyle Hidden"`. `adb` is `/d/Android/Sdk/platform-tools/adb`; prefix adb commands that take device paths with `MSYS_NO_PATHCONV=1`. Screenshot: `adb exec-out screencap -p > build/shots/x.png`. `connectedDebugAndroidTest` uninstalls the debug app when it finishes.
- **scrypt benchmark** (androidTest `ScryptMeasurement`): see [docs/notes/scrypt-measurement.md](docs/notes/scrypt-measurement.md).
- Release signing: `scripts/generate-release-keystore.sh` (run once, by the user only; ADR 0005). Without `keystore.properties` the release APK is unsigned.

## Layout

Single `:app` module, package by feature under `dev.alializadeh.orbit`:

- `person/` Person entity, DAO, repository, `list/` `add/` `detail/` screens; `upcoming/`, `orbitview/` (stubs).
- `settings/` the grouped root list, Appearance, Language & dates (`OrbitSettingsScaffold`), plus the temporary Backup spike row.
- `firstrun/` the step sequence (`FirstRunStep`; Welcome now, App lock offer in slice 7, Restore branch in slice 5) and `FirstRunRepository.finish()` (Welcome choices + seed Rings, then the device-bound flag). `MainActivity` shows it until `DeviceState.firstRunFinished`.
- `calendar/` pure Kotlin: `CalendarSystem`, `SolarHijriRules` (33-year rule, tested against the vendored official table `src/test/resources/calendar/kabise.txt`), `CalendarDate` (a day in a Calendar), `RecurringDate` ((Calendar, month, day, year?): occurrences, missing day → month's last day, years count, conversion between Calendars).
- `format/` `OrbitNumberFormatter` (digits, ordinals), `OrbitDateFormatter` (Orbit's day-first patterns, the second-Calendar rule), `RelativeDays` + `text()` renderers, `Digits` (normalise to ASCII), `bidiIsolate()`. `LocalOrbitFormats` is provided by `OrbitTheme`.
- `language/` `OrbitLanguage` (en, fa with `fa-IR` formatting: Saturday week start), `AppLanguage` (AppCompat per-app locales; setting it recreates the activity).
- `ring/` `Ring` entity (+ `SeedRing`, max 6) and `Cadence` (count + DAYS/WEEKS/MONTHS; "no Cadence" is null). `reminders/LeadTime`, `backup/BackupNudgeInterval`, `person/PeopleSort` are the backed-up preference types.
- `ui/datepicker/` the wheel picker: rules in the plain `WheelDatePickerState` (JVM-tested), drawing in `WheelDatePicker`. Not used by a screen yet (slices 3 and 4).
- `ui/components/` `OrbitConnectedChoice` (connected toggle-button group) and `OrbitListGroup` rows (Expressive `SegmentedListItem`s).
- `backup/` the Backup format: `BackupWriter` (VACUUM INTO → ZIP → age), `BackupReader` (decrypt to the end, verify), `AgeDecryptingInputStream`, manifest. `backup/spike/` is TEMPORARY; slice 5 deletes it.
- `data/` `OrbitDatabase`, the single-row `BackedUpPreferences` table (ADR 0006: every backed-up column exists already), `DeviceState` (typed JSON DataStore, device-bound), `PreferencesRepository` over both, Hilt modules.
- `ui/theme/` (MaterialKolor scheme, fonts, typography), `ui/navigation/` (tabs, `OrbitNavigator`, `OrbitApp`), `ui/expressive/` (wrappers for experimental Material APIs), `ui/icons/`.
- Not created yet, add when needed: `search/`, `lock/`.

## Conventions

- **MVVM + UDF**: one `StateFlow<XUiState>` per screen via `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`; user actions are plain methods; a stateless `XScreen(uiState, callbacks)` plus a stateful `XRoute` that gets the ViewModel with `hiltViewModel()`. Keys needed by a ViewModel come through assisted injection (`PersonDetailViewModel`). Text fields hold a `TextFieldState` in the ViewModel, not a String flow.
- **Navigation 3**: every destination is a `@Serializable` `NavKey` declared next to its screen; register it in `OrbitApp`'s `entryProvider`. Push with `navigator.navigate(key)`. Tab rules live in `OrbitNavigator` (tested in `OrbitNavigatorTest`).
- **Time**: inject `java.time.Clock`; never call `Instant.now()`/`LocalDate.now()` without it.
- **Numbers and dates are shown only through `LocalOrbitFormats.current`** (`numbers.format()`, `dates.format()`...), never `"$n"`, `%d`, `DateTimeFormatter` or ICU. Month and weekday names live in `format/CalendarNames` (data, not sentences). Join "A · B" parts with the `separator` string. Wrap user values placed in a sentence with `bidiIsolate()`.
- **Dates**: absolute days are `LocalDate`; anything that recurs is a `RecurringDate` in its own Calendar (ADR 0003). Never compute Solar Hijri any other way than `calendar/`.
- **Preferences**: backed-up ones are columns of `BackedUpPreferences` (with `@ColumnInfo(defaultValue)` matching the Kotlin default: `PreferencesRepositoryTest` checks it); device-bound ones are fields with defaults on `DeviceState`. Enums are stored by constant name: never rename one.
- **Settings sub-screens** use `OrbitSettingsScaffold` (`surfaceContainer` page, `surface` segmented rows) and push their `NavKey` on the Settings tab.
- **Strings**: every key in `values/strings.xml` AND `values-fa/strings.xml` in the same change (lint `MissingTranslation`/`ExtraTranslation` are errors; `StringResourcesTest` checks keys, placeholders and plurals). Keys `screen_element_purpose`; sentence case, British spelling; placeholders `%1$s` only, never `%d` (numbers go through `OrbitNumberFormatter`); "Orbit" stays Latin; Persian in the written register with شما; bidi-isolate user values in sentences. The user reviews all Persian.
- **Type**: never set `fontWeight` on a `Text` (Orbit's per-weight `FontFamily(typeface)` ignores it); pick a `MaterialTheme.typography` style. Bundled weights: Regular, Medium, SemiBold (emphasised styles).
- **Experimental Material APIs** (`@ExperimentalMaterial3Api`, `@ExperimentalMaterial3ExpressiveApi`) only inside `ui/expressive/`.
- **Schema policy until the end of slice 5**: database version stays 1 and the schema is edited in place (commit the regenerated `app/schemas/.../1.json`). From slice 5 on: frozen, every change ships a migration and a migration test. Never use destructive fallback.
- **No INTERNET permission.** After adding a dependency, check `app/build/intermediates/merged_manifest/*/process*MainManifest/AndroidManifest.xml`.
- Room DAO tests run on the **plain JVM** (no Robolectric): `TestDatabases.inMemory()` / `onDisk(file)` with `BundledSQLiteDriver`. Robolectric is for Compose tests and Roborazzi only.

## Version gotchas (stack locked in ticket 11)

- AGP 9.4.1 needs Gradle ≥ 9.6; the wrapper is 9.8.2. Built-in Kotlin: no `org.jetbrains.kotlin.android` plugin, KSP only.
- Room 3 renames: `androidx.room3.*`, `@ColumnTypeConverter(s)`, Gradle extension `room3 { }`, raw SQL via `useWriterConnection { it.usePrepared(...) }`.
- `sqlite-bundled` puts both its Android and JVM variants on the unit-test classpath; `app/build.gradle.kts` excludes `sqlite-bundled-android` from `*UnitTestRuntimeClasspath` so JVM tests load host natives.
- Robolectric 4.17 at SDK 37 on JDK 21 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` (set on every `Test` task) and a current androidx.test (espresso-core 3.7.0, test core 1.7.0); Compose ui-test's transitive Espresso 3.5 breaks on Android 17's `InputManager`.
- material3 1.5.0-beta02 deprecates the old `ListItem(headlineContent = …)`; use the Expressive overloads (`ListItem(onClick = …) { Text(…) }`). `TopAppBar` is still experimental, hence `ui/expressive/OrbitTopAppBar`.
- MaterialKolor 6: `com.materialkolor.material3.rememberDynamicColorScheme`.
- kage 0.8.0 passes the C2SP/CCTV age vectors except that on six payload-failure vectors (trailing data after the final chunk) it withholds the final chunk before failing: stricter than the spec, asserted in `AgeTestVectorsTest`.
- Release R8: `NavKey` class names must be kept (back stacks are restored by class name); see `app/proguard-rules.pro`. Verified by a process-death restore on a minified build.
- `.gitattributes` keeps the age test vectors byte-exact; Windows `core.autocrlf` corrupts them otherwise.
- Inside `enum class CalendarSystem`, `SolarHijri` means the enum constant, hence the converter object's name `SolarHijriRules`.
- The shell's outer `Scaffold` (`OrbitApp`) has zero `contentWindowInsets`: each screen's own `Scaffold` draws behind the system bars. `MainActivity` re-applies `enableEdgeToEdge` so bar icons follow Orbit's theme, not the phone's.
- The splash screen (`Theme.Orbit.Starting`) stays up until `MainViewModel` has read the preferences and `DeviceState`.
- `SegmentedListItem`, `ToggleButton` and button groups are stable in material3 1.5.0-beta02 (no opt-in needed).
- Robolectric screenshot helpers: `testing/Screenshots.kt` (`captureScreen(name, variant)`, en/fa × light/dark).
