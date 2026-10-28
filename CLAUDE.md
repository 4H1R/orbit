# Orbit

A personal, offline relationship manager for one person on one Android phone, in English and Persian. v1 and v1.1 are feature-complete. Domain words are in [GLOSSARY.md](GLOSSARY.md) (use them in code and UI); hard-to-reverse decisions are in [docs/adr/](docs/adr/); building, installing and release signing are in [README.md](README.md).

## Commands (Git Bash on Windows)

Always use Android Studio's JDK 21 (the system `java` is 23):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ORBIT_AGE_CLI=/d/Development/tools/age-dl/age/age.exe   # else AgeCliInteropTest is skipped
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug verifyRoborazziDebug   # the "done" gate
./gradlew recordRoborazziDebug          # re-record screenshots after an intended UI change (look at the PNGs)
./gradlew testDebugUnitTest --tests '*BackupRoundTripTest'
./gradlew installDebug                  # onto the running emulator
```

- `local.properties` (gitignored) holds `sdk.dir=D\:/Android/Sdk`.
- **Emulator**: AVD `Pixel_9_Pro_API_35`. Start it detached so it outlives the session: `powershell.exe -NoProfile -Command "Start-Process 'D:\Android\Sdk\emulator\emulator.exe' -ArgumentList '-avd','Pixel_9_Pro_API_35','-no-window','-no-audio','-no-boot-anim' -WindowStyle Hidden"`. `adb` is `/d/Android/Sdk/platform-tools/adb`; prefix adb commands that take device paths with `MSYS_NO_PATHCONV=1`. Screenshot: `adb exec-out screencap -p > build/shots/x.png`. `connectedDebugAndroidTest` uninstalls the debug app when it finishes.
- **Notifications on the emulator**: set the Reminder time a few minutes ahead in Settings → Reminders; check the alarm with `adb shell dumpsys alarm | grep -A3 dev.alializadeh` and the posted ones with `adb shell dumpsys notification --noredact | grep -A30 dev.alializadeh`. Grant the permission without the dialog: `adb shell pm grant dev.alializadeh.orbit.debug android.permission.POST_NOTIFICATIONS`.
- **Release signing**: `scripts/generate-release-keystore.sh` is run once, by the user only (ADR 0005). To smoke-test R8 on the emulator, sign a **throwaway copy** with a temporary key under `build/` (`apksigner sign --ks build/tmp.jks …`) and uninstall it afterwards: it has the permanent applicationId with the wrong key.

## Layout

Single `:app` module, package by feature under `dev.alializadeh.orbit`: `person/` (model, list, detail, edit form, photos, contact methods, dates, gifts), `interaction/`, `due/`, `ring/`, `upcoming/`, `reminders/`, `occasion/`, `wishes/`, `search/`, `orbitview/`, `widget/`, `backup/`, `lock/`, `calendar/`, `format/`, `language/`, `settings/`, `firstrun/`, `about/`, `data/`, `ui/` (`theme/`, `navigation/`, `components/`, `expressive/`, `datepicker/`, `icons/`). Test fakes are in `testing/`.

Each rule is decided in one place. Use it rather than re-deriving:

- **Due**: `due/KeepInTouch.on(today)` → `DueState`.
- **Upcoming**: `UpcomingFeed.of(people, today, occasions)`.
- **Notifications and alarms**: `ReminderPlanner` (pure), applied only by `ReminderReconciler.reconcile()`.
- **Who an Occasion is for**: `OccasionWithAudience.audience()` (`occasion/OccasionAudience.kt`).
- **Search matching**: `search/SearchText` (normalisation, also used by the People picker) and `SearchIndex`; filters in `PeopleFilters`.
- **App lock**: `lock/AppLockMachine` (pure).
- **Backup nudges**: `backup/BackupRules`. **Passphrases**: `PassphraseRules`.
- **Calendars**: `calendar/` (`RecurringDate`, `SolarHijriRules`).
- **What's stored for a Person**: `PersonDraft.normalised()`, which is also what "changed?" compares.

## Conventions

- **MVVM + UDF**: one `StateFlow<XUiState>` per screen via `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`; user actions are plain methods; a stateless `XScreen(uiState, callbacks)` plus a stateful `XRoute` that gets the ViewModel with `hiltViewModel()`. Keys reach a ViewModel through assisted injection. Text fields hold a `TextFieldState` in the ViewModel. Large forms (`PersonFormViewModel`): `TextFieldState`s + snapshot state, one `snapshotFlow { snapshot() }`; JVM tests call `Snapshot.sendApplyNotifications()` + `runCurrent()` after each change.
- **Navigation 3**: every destination is a `@Serializable` `NavKey` declared next to its screen and registered in `OrbitApp`'s `entryProvider`. Tab rules live in `OrbitNavigator`. Settings sub-screens use `OrbitSettingsScaffold` and push onto the Settings tab.
- **Language-dependent presentation** (displayed name, subtitle, People sort) is computed in the composable from `LocalOrbitFormats.current.language`: a language switch recreates the activity, not the ViewModels.
- **Numbers and dates are shown only through `LocalOrbitFormats.current`** (`numbers.format()`, `dates.format()`…), never `"$n"`, `%d`, `DateTimeFormatter` or ICU. Month and weekday names live in `format/CalendarNames`. Join "A · B" parts with the `separator` string. Wrap user values placed in a sentence with `bidiIsolate()`; contact values with `bidiIsolateLtr` (edited with `TextDirection.Ltr`).
- **Time**: inject `java.time.Clock`; tests pass fixed clocks.
- **Dates**: absolute days are `LocalDate`; anything that recurs is a `RecurringDate` in its own Calendar (ADR 0003). Lunar Hijri is Umm al-Qura moved by the process-wide `LunarHijriAdjustment` (ADR 0007); tests that set it put it back to 0. Lunar Hijri is never a Primary calendar.
- **Strings**: every key goes in `values/strings.xml` and `values-fa/strings.xml` in the same change. Every count is a `<plurals>`, even when Persian's forms match (a second count goes in as an already-pluralised `%2$s`). A sentence-start string reused mid-sentence gets an `_inline` lower-case twin; at a line start use `sentenceStart()`. Keys `screen_element_purpose`; sentence case, British spelling; placeholders `%1$s` only. "Orbit" stays Latin. Persian is in the written register with شما and spells «جست‌وجو», «یک‌شنبه»/«پنج‌شنبه», «به‌صورت»/«به‌جای». The user reviews all Persian. `StringResourcesTest` checks keys, placeholders and plurals.
- **Preferences**: backed-up ones are columns of `BackedUpPreferences` (ADR 0006) with `@ColumnInfo(defaultValue)` matching the Kotlin default; device-bound ones are fields with defaults on `DeviceState`. Enums are stored by constant name: never rename one.
- **Schema** (version 3, frozen since real data exists): a change bumps `OrbitDatabase.VERSION`, adds the `Migration` to `OrbitDatabase.MIGRATIONS`, commits the newly exported `app/schemas/.../<N>.json`, and adds a `DatabaseMigrationTest` case. Never edit an exported JSON; never use destructive fallback. A change to what a Backup holds may also need `BACKUP_FORMAT` (ADR 0004/0006).
- **Data version**: SQLite triggers (generated from the live schema on open) bump `data_version` on every data change; it drives the Backup nudge and reminder reconciling. A column Orbit writes by itself (like `lastNotifiedDueOn`) goes in `DataVersionTriggers.ignoredColumns` and is written with a targeted `UPDATE`.
- **Notifications**: only `reconcile()` posts or sets alarms. Anything that changes what's due either writes data or calls `ReminderTriggers.request()`. Texts live in `NotificationTexts`, built with `AndroidReminderNotifier.localizedContext()` (Android 12 doesn't apply the per-app language without an activity). Rendering (Detailed/Discreet) comes from `NotificationRenderingPolicy` (App lock).
- **Restore** never touches live data until the Backup is fully staged and validated, and swaps in one SQLite transaction; `Restorer`'s KDoc has the crash-safety rules (journal, restore token, `recoverInterrupted()`). Listeners that must act around it bind a `RestoreListener`.
- **App lock gate**: while Locked, `AppLockGate` composes only `LockedRoute`, so everything behind it (its sheets and dialogs included) is out of composition. Keep it that way; don't hide composed content instead. State survives through hoisted holders (`NavEntryStates`, which `OrbitApp` must pass to `rememberEntries`), so keep what the user typed or chose in the ViewModel or `rememberSaveable`: plain `remember` is lost on unlock (`AppLockGateTest`). Launch pickers with `startActivityForResult` (exempt from the lock); use plain `startActivity` for anything that leaves Orbit (counts as time away).
- **Privacy**: log only through `OrbitLog` (fixed message + exception class name; `OrbitLogTest` fails on any other `Log.` call). The crash log never records exception messages. Copy user values with `sensitiveClipData()`. A secret on screen sits in a `SecureFlagPolicy.SecureOn` dialog and is cleared on `ON_STOP`.
- **No INTERNET permission.** After adding a dependency, check the merged manifest and list it in `about/OpenSourceLicences` with its licence text in `assets/licences/` (`LicencesTest` compares it with the release runtime classpath).
- **Contact actions** use implicit intents only; catch `ActivityNotFoundException` and show `contact_no_app`.
- **Photos** only through `PhotoStore`. A form owns photos it cropped until saved and deletes them on discard.
- **Snackbars** that outlive a screen (Undo) go through `LocalOrbitSnackbars`; every screen `Scaffold` sets `snackbarHost = { OrbitSnackbarHost() }`. Undo lambdas must not capture `viewModelScope`.
- **Log interaction / Snooze** on any screen: a `KeepInTouchViewModel = hiltViewModel()` in the Route, `KeepInTouchSheets(it)`, and route actions to it. A sheet inside a sheet is its own `OrbitBottomSheet` with a fixed height.
- `SheetCloser.close(then)` runs `onDismiss` **before** `then`: pass the row key into the callback instead of reading state `onDismiss` clears.
- **UI**: never set `fontWeight` on a `Text` (the per-weight font families ignore it); pick a `MaterialTheme.typography` style. Experimental Material APIs only inside `ui/expressive/`. Screen, sheet and section titles are headings; icon-only buttons have a content description; never ellipsize a choice label (`OrbitConnectedChoice` wraps to chips instead).
- **Motion**: anything that animates endlessly must check `LocalStillMotion` (true in screenshot tests and with system animations off): Roborazzi waits for idle, so an endless animation hangs the capture forever while plain `testDebugUnitTest` still passes.
- CPU-heavy flow work goes on the injected `@DefaultDispatcher`.

## Tests

- Room DAO tests run on the **plain JVM**: `TestDatabases.inMemory()` / `onDisk(file)`. Every Person needs a Ring (foreign keys are on): `database.insertTestRing()`. Migration tests build old databases from the exported JSON via `data/SchemaMigrations` (room3-testing's helper doesn't fit the Android artifact).
- Robolectric is for Compose tests, Roborazzi, and anything needing Android's ICU (Persian collation: the JDK's `Collator` has no Persian rules). It starts the real `OrbitApplication`; `ReminderTriggers.start()` is skipped there.
- ViewModel tests use the fakes in `testing/` (`FakePersonRepository` keeps the Room repository's rules).
- Screenshots: `testing/Screenshots.kt` `captureScreen(name, …)` renders en/fa × light/dark (`tall = true` for long pages). Sheets and dialogs are their own windows: capture `XSheetContent` in `testing/SheetFrame` + `NoCloser`, dialogs in a frame.

## Gotchas

- AGP 9.4.1 with Gradle 9.8.2 and built-in Kotlin: no `org.jetbrains.kotlin.android` plugin, KSP only.
- Room 3: `androidx.room3.*`, `@ColumnTypeConverter(s)`, Gradle `room3 { }`, raw SQL via `useWriterConnection { it.usePrepared(...) }`, `@Relation` takes arrays (`parentColumns = ["id"]`).
- `app/build.gradle.kts` excludes `sqlite-bundled-android` from unit-test classpaths so JVM tests load host natives.
- Robolectric 4.17 at SDK 37 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` (set on every `Test` task) and current androidx.test (espresso-core 3.7.0).
- material3 1.5.0-beta02: use the Expressive `ListItem(onClick = …) { Text(…) }` overloads; `TopAppBar`, `ModalBottomSheet`, `ExposedDropdownMenuBox` and `MaterialShapes` are still experimental; `ExposedDropdownMenu` needs an explicit import.
- A `ModalBottomSheet` whose content changes height right after a scroll gesture inside it gets stuck (`hide()` never completes). Keep sheet heights stable: the wheel picker reserves its "Converted using" line and consumes leftover scroll.
- minSdk 31: API 33/34 members like `LocalDate.EPOCH` or `URLEncoder.encode(String, Charset)` fail lint `NewApi`.
- Release R8 must keep `NavKey` class names (back stacks restore by class name; see `proguard-rules.pro`). Retrace a crash log with that build's `mapping.txt`.
- Kotlin's infix `and` binds looser than `!=`: write `(flags and FLAG) != 0`.
- `.gitattributes` keeps the age test vectors byte-exact; don't let line-ending conversion touch them.
- kage 0.8.0 is stricter than the age spec on six payload-failure vectors; `AgeTestVectorsTest` asserts that.
- `DeviceState` JSON uses `coerceInputValues`: an unknown enum value falls back to the field default instead of replacing the whole state (which would rerun first run).
- The outer `Scaffold` in `OrbitApp` has zero `contentWindowInsets`: each screen's own `Scaffold` handles the system bars.

### Emulator

- The x86_64 image SIGSEGVs inside ART now and then in any process; check `adb logcat -b crash` before blaming Orbit.
- No system photo picker: the form falls back to `OpenDocument`. Push an image with `adb push x.png /sdcard/Pictures/` + `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/x.png`.
- The credential prompt is a secure window (`screencap` shows black). Set a PIN with `adb shell locksettings set-pin 1234`, type it with `input text 1234` + keyevent 66, and remove it afterwards with `locksettings clear --old 1234`.
- `adb shell input` can't pinch or type Persian (tap Gboard's Persian layout keys instead). Double-tap: `adb shell "input tap x y & sleep 0.12; input tap x y"`. Drag wheels with slow swipes (`input swipe x y1 x y2 1500`). Keyevent 111 (Esc) acts as Back and can leave the app; a swipe starting on the keyboard glide-types.
- Seed the debug DB by pulling `databases/orbit.db*` with `run-as … cat`, editing with Python's `sqlite3` (checkpoint the WAL), and copying back via `/data/local/tmp` with the app stopped.
- The passphrase fields use `KeyboardType.Password` + `hintLocales` en, which opens the English layout and keeps the passphrase out of the keyboard's learning.
