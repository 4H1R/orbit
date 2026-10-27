# Orbit v1: product requirements

Orbit is a personal, offline relationship manager for one person on one Android phone. It remembers the people in the user's life, what matters to them and when to reach out, in English and Persian, with dates in Gregorian and Solar Hijri.

This document gathers the decisions in one place. It doesn't replace the sources it links:

- **Words**: the [glossary](../GLOSSARY.md) defines every domain term and its Persian UI word. Capitalised terms here (Person, Ring, Due…) are glossary terms.
- **Hard-to-reverse choices**: [ADRs 0001–0006](adr/).
- **UI wording**: the [copy deck](copy-deck-v1.md) holds the writing rules and the tricky strings in both languages.
- **Look and feel**: the visual direction prototype, direction A "Expressive": `.scratch/orbit-v1/prototypes/visual-direction-prototype.html` on branch `prototype/visual-direction` (open `?variant=A`). It's a throwaway, so never merge it.
- **Why each decision was made**: the resolved tickets in `.scratch/orbit-v1/issues/`, linked per section as *Decided in*.

Where two tickets overlapped, the later decision is used here. The overlaps found are listed in [Conflicts resolved](#conflicts-resolved).

## Goals and non-goals

**Goals**
- Never forget an Important date, and have the context ready (age, Gift ideas, how to reach them) when it comes.
- Notice who the user is drifting away from (Cadence and Due) without nagging.
- Find "who can help with X?" or "who likes jazz?" in one search.
- Survive a lost or replaced phone through an encrypted Backup that the user controls.
- Feel native and equally at home in English (LTR) and Persian (RTL).

**Non-goals** (see the map's Out of scope): sync, accounts or a server; sharing; iOS, desktop and the Play Store; merging a Backup into existing data; encrypting the on-device database; relationships between People; merging duplicates; custom fields; contact import; widgets; automatic Backups; an app-wide Interaction timeline; tablet layouts.

## Platform and constraints

- Native Android in Kotlin, Jetpack Compose and Material 3. minSdk 31, compileSdk and targetSdk 37. Sideloaded, so Play policy doesn't apply. One user, one device, no network: **no `INTERNET` permission**. See [ADR 0001](adr/0001-android-only-single-device-no-sync.md).
- The applicationId `dev.alializadeh.orbit` and the release signing key are permanent. Debug builds use `.debug` and install next to the real app. See [ADR 0005](adr/0005-orbits-app-identity-is-permanent.md).
- Only Backups are encrypted. On-device data relies on Android file-based encryption plus the optional App lock. See [ADR 0002](adr/0002-encrypt-backups-not-the-on-device-database.md).

## What Orbit stores

*Decided in [Person data model](../.scratch/orbit-v1/issues/05-person-data-model.md) and [Calendar model for Important dates](../.scratch/orbit-v1/issues/06-calendar-model-for-important-dates.md).*

- **Person**:
  - **Name** is the only required field. **Nickname** and **Name in other script** are optional. The UI shows whichever name matches the UI script, with the other as a subtitle.
  - One square photo, or an initials avatar coloured from the name.
  - **How we met** and **Notes** are free text.
  - No gender or pronoun field, and no custom fields. Duplicate names are allowed after a notice.
  - **Archive** is reversible: an Archived Person is hidden from lists, Upcoming, reminders, the Orbit view and the Interaction picker. **Delete** is permanent.
- **Likes / Dislikes / Can help with**: three lists of short one-line items owned by the Person. Autocomplete suggests wording already used on other People, but items aren't shared entities.
- **Ring**: a user-editable ordered list, innermost first, of **at most 6** Rings. Each Ring has a default Cadence.
  - Seeds: Inner (14 days), Close (30), Friends (90), Acquaintances (none). They're created once in the language chosen on Welcome, and are user data from then on.
  - A new Person defaults to the outermost Ring. A Ring with People in it can't be removed until they're moved.
- **Tag**: a shared entity. Tags are created inline, and renamed, merged or deleted in Settings.
- **Contact method**:
  - Fixed types: Phone (also SMS), WhatsApp, Telegram, Signal, Email, Instagram, LinkedIn, X, Address, Website / Other link.
  - Each has a value and an optional label, and at most one per Person is **primary**.
  - Values are always shown as entered, in Latin script. Persian or Arabic digits typed into a phone number are saved as ASCII.
- **Important date**: a Birthday (at most one), an Anniversary or a Custom date with a label. Each stores **(Calendar, month, day, optional year)** and recurs in its own Calendar.
  - A missing day falls on the month's last day.
  - Years run from 1900 CE / 1279 SH up to the current year.
  - Each date has either the default lead times or its own override.
  - See [ADR 0003](adr/0003-important-dates-keep-their-own-calendar.md).
- **Interaction**: one or more People, a type (Call / Message / In person / Other), a date with no time, and an optional note. Deleting a Person removes them from shared Interactions, which stay for the others.
- **Cadence**: comes from the Ring unless the Person overrides it, and may be none. **Due**, **Snooze** and "last notified as Due" state live with the Person.
- **Gift idea**: text, an optional link, an optional note, and a status of Idea or Given (with the date). Open ideas surface with every Important date of that Person.

## Experience

### Shell and first run

*Decided in [Screen inventory and flows](../.scratch/orbit-v1/issues/14-screen-inventory-and-flows.md) and [Visual direction](../.scratch/orbit-v1/issues/08-visual-direction.md).*

- **Tabs**: four bottom tabs, Upcoming · People · Orbit · Settings, each with its own back stack.
  - Detail screens, forms and Settings sub-screens push onto the current tab and hide the bar.
  - Re-selecting a tab returns it to its root, then scrolls to the top.
  - Back at the root of any other tab goes to Upcoming.
- **Layout**: phone-first and single-pane at every size, with content capped at about 640dp and centred. Edge-to-edge, with predictive back.
- **FAB**: an extended **New person** FAB on Upcoming and People.
- **Long-press** anywhere a Person appears opens the **quick-action sheet**: the primary contact action, Log Interaction, and Snooze when Due.
- **First run**:
  1. **Welcome**: language, Primary calendar (it follows the language until touched), "Use Latin digits" (Persian only), and **Restore from a Backup**.
  2. **App lock offer**: skipped when the phone has no screen lock.
  3. Upcoming.

  There's no notification-permission step and no Ring setup. A Restore on Welcome replaces the language and calendar choices, and the App lock step still follows.

### Upcoming (Home)

*Decided in [Reminder and Cadence rules](../.scratch/orbit-v1/issues/07-reminder-and-cadence-rules.md) and [Screen inventory and flows](../.scratch/orbit-v1/issues/14-screen-inventory-and-flows.md).*

Sections run top to bottom. Empty ones are hidden.

1. **Warning cards**:
   - App lock paused, when the phone's screen lock was removed;
   - a critical Reminder health problem: notifications off, or exact alarms unavailable on Android 12/12L.
2. **Backup nudge** card: **Back up now** and "Not now", which hides it for 7 days.
3. **Today**: the nearest date is a hero card with the photo, the years count, open Gift ideas, and [contact action] [Log Interaction]. Further dates today are rows with the same actions.
4. **Due**: innermost Ring first, then longest since the last Interaction. Each row has a contact action, Log Interaction and Snooze. 5 rows show, and "Show all (n)" expands them in place.
5. **Coming up**: Important dates in the next 30 days, with relative words up to a week ahead, the second Calendar when it differs, and "2 gift ideas". Tapping a row opens Person detail.

**Empty states**: with no People, "Keep the people who matter in orbit". With People but nothing to show, "All caught up", plus "Next: …" when an Important date lies beyond 30 days.

### People list and search

*Decided in [Search and filter](../.scratch/orbit-v1/issues/12-search-and-filter.md) and [Screen inventory and flows](../.scratch/orbit-v1/issues/14-screen-inventory-and-flows.md).*

- **Layout**:
  - A docked search bar, then one chip row: **Sort ▾ · Ring ▾ · Tags ▾ · Due · Date soon · Archived**.
  - A "Filtered · Clear" bar shows while any filter is on.
  - Rows show the avatar, the displayed name, and "Ring · last talked 34 days ago", with a Due chip and the apricot date badge.
- **Search**:
  - Results are always People, updating as you type. Search covers every Person field plus the notes of their Interactions.
  - **Matching**: "contains" matching, with several words combined by AND across fields.
  - **Normalisation**: Persian/Arabic folding (ي/ی, ك/ک, alef forms, ة; harakat, tatweel and ZWNJ removed), Latin case and accent folding, and digit folding. A digits-only query ignores phone punctuation. There's no transliteration.
  - **Ranking**, in four tiers: names; Tags and Can help with; Likes, Dislikes, Gift ideas, How we met and Contact methods; Notes and Interaction notes. A non-name match adds a "why it matched" line.
  - Nothing about queries is stored.
- **Filters**: Ring (any of), Tags (any of), Due, and Important date soon (next 30 days), combined with AND. **Archived** is a separate view, and normal search ends with "N archived people match".
- **Sort**: Name (the default, with Persian collation in Persian UI), Ring, Last Interaction or Recently added. List headers follow the sort.
- **What's remembered**: the sort is a backed-up preference. Filters and the query survive switching tabs but are cleared on restart.

### Person detail and editing

*Decided in [Screen inventory and flows](../.scratch/orbit-v1/issues/14-screen-inventory-and-flows.md).*

- **Header**:
  - Photo (cookie shape), names, Ring and Tag chips.
  - A Cadence line with wavy progress ("Last talked 34 days ago · every 30 days", **Due** + Snooze, or "Snoozed until … · Cancel snooze").
  - A button group: [primary contact] [Log Interaction] [More contact ▾].
- **Sections**: Important dates, Gift ideas, Contact methods, Can help with, Likes, Dislikes, How we met, Notes, Interactions (the latest 5, then "All Interactions (n)").
  - Empty sections are hidden, except Gift ideas and Interactions, which always show.
  - An **Add details** row offers chips for the missing fields.
  - Tapping a Likes, Dislikes or Can help with chip searches People for that text.
- **Menu**: Archive (with an Undo snackbar) and Delete…, which asks for confirmation. An Archived Person shows an "Archived · Unarchive" banner.
- **Add/Edit form**: one scrolling form.
  - Photo, Name, Ring, Contact methods and Birthday come up front. Everything else sits behind "More details".
  - Save is enabled once Name isn't blank. Leaving with changes asks "Discard changes?". A duplicate name asks "Save anyway / Go back".
  - **Photo**: system photo picker → Orbit's crop screen → a 768 px JPEG in app storage. No camera.
  - **Important date sheet**: type, the wheel picker, and Reminders (Default, or Custom lead-time chips).
- **Sheets**:
  - **Log Interaction**: type, date (Today / Yesterday / Pick…), People pre-filled with the starting Person, and a note. It always starts from a Person. Edit and Delete happen by reopening it.
  - **Snooze**: 3 days, 1 week or 1 month, with an Undo snackbar.
  - **Gift idea**: has **Mark as given** with a date.

### Calendars, dates, language and digits

*Decided in [Calendar model for Important dates](../.scratch/orbit-v1/issues/06-calendar-model-for-important-dates.md), [Persian locale and Solar Hijri support](../.scratch/orbit-v1/issues/03-persian-locale-and-solar-hijri-support.md) and [Translation and copy](../.scratch/orbit-v1/issues/15-translation-and-copy.md).*

- **Calendar conversion**: Orbit's own pure-Kotlin 33-year Solar Hijri converter, tested against the official leap table (correct for 1206–1497 SH).
- **Primary calendar**: a setting that defaults from the first-run language and then is independent of it.
  - An Important date in the other Calendar also shows its own Calendar, after the Primary one ("15 Mar · 25 Esfand").
  - Every other date shows the Primary calendar only.
- **Formats**: Orbit's own patterns, day first. The year is dropped for the current year. Weekdays appear only in Upcoming.
- **Years count**: "Sara turns 35", "5th anniversary" / «پنجمین سالگرد», measured in the date's own Calendar and shown only when the year is known.
- **Date entry**: one custom wheel picker (day · month · year or "No year") with a Calendar toggle. Flipping the toggle converts the selection. Interactions use the same picker.
- **Language**: English and Persian with full RTL, through AppCompat per-app locales.
- **Digits**: they follow the language, and Persian UI has a "Use Latin digits" toggle. Numbers are always formatted through `OrbitNumberFormatter`.
- **Copy**: every user value is bidi-isolated, and only directional icons mirror. The writing rules, the separator rule («، » in Persian) and the key strings are in the [copy deck](copy-deck-v1.md).

### Reminders and Cadence

*Decided in [Reminder and Cadence rules](../.scratch/orbit-v1/issues/07-reminder-and-cadence-rules.md) and [Reliable reminder scheduling on Android 12+](../.scratch/orbit-v1/issues/02-reliable-reminder-scheduling.md).*

- **Reminder time**: one setting, default 09:00, for every Reminder and the Cadence digest. No quiet hours.
- **Lead times**: a global default (1 week before + on the day). A date may override it with On the day, 1 day, 3 days, 1 week, 2 weeks, 1 month, or No reminders.
- **Missed Reminders**: posted only on the same day.
- **Reminder notifications**: one per Reminder, on the "Important dates" channel.
  - Title "Sara's birthday · in 7 days" / "Today is Sara's birthday". The body has the years count and the open Gift ideas, and the large icon is the photo or avatar. Tapping opens Person detail.
  - Only the **on-the-day** Reminder has actions: [primary contact] [Mark as contacted]. Important dates can't be snoozed.
  - A Reminder expires at the end of its day, and editing the date cancels it.
- **Due**:
  - A Person is Due once the days since their latest Interaction of any type (or since they were added) reach their Cadence. There's no grace period.
  - They're notified **once** per time they become Due, through a daily **Cadence digest** on the "Keep in touch" channel.
  - With one Person, the digest has a title and body plus [contact] [Mark as contacted] [Snooze 1 week]. With several, it names them, has no actions, and opens Upcoming at Due.
  - A Person with an on-the-day Reminder is left out of that day's digest, and counts as notified for that Due period.
  - A Due notification is cancelled once the Person stops being Due.
- **Mark as contacted** logs an Interaction of type Other, dated today, with no note.
- **Snooze** only delays. It isn't an Interaction.
- **Permissions**:
  - `POST_NOTIFICATIONS` is asked for when the user first saves an Important date or a Person with a Cadence, then the battery exemption is asked for once.
  - **Reminder health** in Settings is a checklist: notifications, exact alarms, battery exemption, "Pause app activity if unused", and the phone maker's steps.
  - Settings links to the two channels and has no on/off switches of its own.

### Orbit view

*Decided in [Orbit view behaviour](../.scratch/orbit-v1/issues/13-orbit-view-behaviour.md).*

- **What's drawn**: "You" at the centre, with up to 6 equally spaced Rings and every non-Archived Person on their Ring. Empty Rings are drawn faintly. The geometry doesn't mirror in RTL.
- **Position**: the angle is stable and meaningless. Only Due People drift outward, by `min(days past Due ÷ Cadence, 1)` × half the gap to the next Ring, and glow in the primary colour.
- **Badges**: an apricot badge for an Important date in the next 30 days, filled on the day.
- **Density**: each Ring uses the largest size that fits: Avatar 36dp, Small 24dp or Dot 10dp. Due and badged People stay at least Small. A Dot Ring shows its count in the label.
- **Gestures and zoom**: zoom from 1× to 4×, double-tap to 2×, and it opens at fit. Tap opens a Person, or zooms in on a crowd. Long-press opens the quick-action sheet. No drag between Rings.
- **Filters**: the People list's definitions, with **separate state**. Non-matches are dimmed, and filters clear on restart.
- **Motion**: no rotation. People spring out when the tab opens, and the glow pulses. "Remove animations" turns all of it off.
- **Legend**: an ⓘ sheet.
- **TalkBack**: Ring by Ring, innermost first. Each Ring is a heading, and each Person has custom actions.

### Settings

*Decided in [Screen inventory and flows](../.scratch/orbit-v1/issues/14-screen-inventory-and-flows.md).*

A grouped list. Each row shows a one-line summary and opens a sub-screen.

- **Appearance**: Theme (System/Light/Dark) and Colour source (Orbit violet, Match wallpaper, or Your colour: 9 swatches plus a picker).
- **Language & dates**: Language, Primary calendar, Use Latin digits.
- **Rings**: reorder by drag, rename, default Cadence, remove (offering to move the Ring's People first). Add is disabled at 6.
- **Tags**: rename (renaming to an existing name merges), delete, and Show People.
- **Reminders**: Reminder time, default lead times, Reminder health, and the channel links.
- **App lock**: on/off, re-lock timing, "Show details in notifications".
- **Backup**: last Backup date, Back up now, Restore, Change passphrase, nudge interval.
- **About**: version, licences, and Share crash log (a local log, shared by hand).

### Backup and Restore

*Decided in [Backup encryption format](../.scratch/orbit-v1/issues/01-backup-encryption-format.md) and [Backup and restore behaviour](../.scratch/orbit-v1/issues/09-backup-and-restore-behaviour.md).*

- **Format**: an **age v1** file (one scrypt recipient, logN=17, written with pinned **kage**) wrapping a ZIP of `manifest.json`, a `VACUUM INTO` snapshot of the database and the referenced photos. Anyone can open it with `age -d`. See [ADR 0004](adr/0004-backups-are-age-files.md).
- **Backup passphrase**:
  - At least 12 printable ASCII characters, entered twice, with one warning that must be acknowledged. No hint and no strength meter.
  - Remembered on the device, wrapped by a Keystore key. On every third Backup, Orbit asks the user to type it. If that check fails, Orbit offers to show the passphrase or set a new one, and both need the phone's screen lock if it has one.
  - Change passphrase needs the current one and affects only future Backups.
- **Contents**: all database data plus the referenced photos, and the backed-up preferences, which live in the database ([ADR 0006](adr/0006-backed-up-preferences-live-in-the-database.md)). The language is recorded in the manifest.
  - Never included: App lock settings, the passphrase, the "last Backup" date, notification and Reminder health state.
- **Back up now**: the system Save picker every time, suggesting `orbit-backup-YYYY-MM-DD.age`, with a blocking progress sheet.
  - The written file is re-read and decrypted to the end and its hashes are checked. Only then does Orbit report success and reset "last Backup".
  - Orbit never deletes or rotates Backup files.
- **Restore**: from Settings or first run.
  1. Pick the file, then enter the passphrase. Retries are unlimited.
  2. Staged decryption and validation.
  3. A summary, then a replace confirmation (offering "Back up current data first" when the data has changed).
  4. An atomic swap, then a reminder reconcile, then Upcoming. "last Backup" becomes the restored Backup's date.

  A Backup from a newer Orbit is refused.
- **Nudge**: shown when there has never been a Backup and at least one Person exists, or when the last Backup is at least the interval old (14/30/60/90 days/Off, default 30) **and** the data has changed since.

### App lock

*Decided in [App lock behaviour](../.scratch/orbit-v1/issues/10-app-lock-behaviour.md).*

- **Unlocking**: the phone's own screen lock (`BIOMETRIC_STRONG | DEVICE_CREDENTIAL`, biometric 1.1.0). There's no Orbit PIN and nothing to recover.
- **Setting**: optional and off by default. It's offered at first run, and turning it on or off needs an unlock.
- **Re-locking**: always on a cold start, and after time away (Immediately / 1 min, the default / 5 min) on elapsed realtime. Orbit's own pickers don't count as time away.
- **Locked screen**: it replaces the whole UI and draws no content. Entry points continue to their destination once unlocked.
- **No screen lock**: if the phone's screen lock is removed, the gate is paused and Upcoming shows a warning.
- **Recents**: hidden while App lock is on: `setRecentsScreenshotEnabled(false)` on 33+, `FLAG_SECURE` on 31/32.
- **Notifications** while App lock is on are **discreet**: no names, photos, Gift ideas or actions. "Show details in notifications" brings the full ones back, with `setAuthenticationRequired(true)` on the actions. Every notification is `VISIBILITY_PRIVATE` with a neutral public version.

### Visual design

*Decided in [Visual direction](../.scratch/orbit-v1/issues/08-visual-direction.md).*

- **Style**: Material 3 Expressive, with **Orbit violet `#5B4BD6`** as the seed and **apricot `#FF8A5B`** reserved for Important dates. Light and dark.
- **Type**: Outfit (Latin) with Vazirmatn (Persian), using per-script fallback. Both are bundled.
- **Shape and motion**:
  - large radii, grouped lists and connected button groups;
  - MaterialShapes for photos and badges, with shape morph on press;
  - the Expressive spring motion;
  - wavy progress for Cadence.
- **Reference**: the prototype is the reference for feel, not pixels. Its date field and separators are superseded by the picker and the copy deck.

## Architecture

*Decided in [Architecture and stack lock-in](../.scratch/orbit-v1/issues/11-architecture-and-stack-lock-in.md), from [Stack baseline (late 2026)](../.scratch/orbit-v1/issues/04-stack-baseline-late-2026.md).*

- **Toolchain**: Kotlin 2.4.21, AGP 9.4.1 with KSP only, a version catalog, and R8 in release.
- **Libraries**:
  - Compose BOM 2026.09.00, with **material3 1.5.0-beta02 pinned**. The experimental APIs go through `ui/expressive`.
  - MaterialKolor 6 for seed colours.
  - Hilt 2.60.1, Navigation 3 1.2.0, Room 3.0.3 with `BundledSQLiteDriver`, DataStore 1.3.0, Coil 3 (no network artifact), biometric 1.1.0, kage (pinned).
- **Shape**: one `:app` module, organised by feature, with MVVM + UDF.
  - One `StateFlow<UiState>` per screen, and repositories over the DAOs.
  - Pure domain logic only where it pays: the calendar converter, occurrence resolution, Due, the reminder planner and the search index.
  - An injected `Clock`.
- **Host**: a single `AppCompatActivity`. Navigation 3 keeps one back stack per tab, behind the App lock gate.
- **Persistence**:
  - Exported schemas, a migration test for every version, and never a destructive fallback.
  - Backed-up preferences live in a single-row Room table, and device-bound state in a typed DataStore ([ADR 0006](adr/0006-backed-up-preferences-live-in-the-database.md)).
- **Reminders**: a pure **reminder planner** `(data, now, zone) → (post, cancel, next alarm)`, applied by one idempotent `reconcile()`.
  - Triggers: the alarm, boot, time and zone changes, app updates, exact alarms being granted, app start, edits and Restore.
  - One `setExactAndAllowWhileIdle` alarm plus `USE_EXACT_ALARM` (and `SCHEDULE_EXACT_ALARM` up to API 32).
  - No WorkManager and no `setAlarmClock`.
- **Search**: an in-memory `SearchIndex` rebuilt from the repositories' Flows. Nothing is persisted, and there's no FTS.
- **Testing**:
  - JVM-first: the converter, planner, Due and the search index under a fixed clock and zone.
  - ViewModels with fakes and Turbine.
  - Room DAO and migration tests, on the JVM if the first data slice proves it works, otherwise on a managed emulator.
  - age test vectors plus a real `age` CLI interop test.
  - A few Compose UI tests under Robolectric.
  - Roborazzi screenshots in en/fa × light/dark.
  - The en/fa string parity test and lint errors.
  - A manual on-device checklist for alarms, notifications and the biometric prompt.

## Conflicts resolved

These are where tickets or docs disagreed. The later decision wins.

- **Copy**: the [copy deck](copy-deck-v1.md) supersedes the example wording in earlier tickets. Examples: the single-Person digest is now a title plus a body; the Restore confirmation, the wrong-passphrase and newer-version errors, the passphrase character rule, and the discreet notification texts all use the deck's wording. English UI is sentence case ("Log interaction"), while the docs capitalise domain terms. That difference is intentional.
- **Ring limit**: [Person data model](../.scratch/orbit-v1/issues/05-person-data-model.md) set no limit. [Orbit view behaviour](../.scratch/orbit-v1/issues/13-orbit-view-behaviour.md) capped it at 6, and that cap holds.
- **Navigation**: the map's original "Navigation Compose" was replaced by Navigation 3.
- **Upcoming warning cards**: App lock added a "paused" warning, which Reminder and Cadence rules didn't order. Both are warning cards at the top of Upcoming, App lock paused first.
- **"Notification state"**: in [ADR 0006](adr/0006-backed-up-preferences-live-in-the-database.md) this means device-bound state: permission asks, channel state, and which Reminders have already been posted today. Snooze-until and "last notified as Due" are Person data in Room, and they are backed up.
- **Decorative rotation**: [Visual direction](../.scratch/orbit-v1/issues/08-visual-direction.md) has slowly rotating decorative shapes, but [Orbit view behaviour](../.scratch/orbit-v1/issues/13-orbit-view-behaviour.md) rules out any rotation on the Orbit view. The rotation therefore applies to decoration elsewhere only, and like every animation it stops under "Remove animations".

## Settled during assembly

These three gaps turned up while this document was being put together, and were decided on 2026-10-28 in [Spec assembly](../.scratch/orbit-v1/issues/16-spec-assembly.md).

- **Revealing the Backup passphrase**: after a failed passphrase check, **Show passphrase** and **Set a new one** both need the phone's screen lock (the system prompt), whether App lock is on or off. On a phone without a screen lock, they go straight through.
- **Restore resets the nudge**: after a Restore, "last Backup" is set to the restored Backup's own date, and the data counts as unchanged since it.
- **Digest and on-the-day Reminders**: a Person left out of the digest because of an on-the-day Reminder counts as notified for that Due period. The Reminder's actions stand in for the digest's, and the Person stays in Upcoming's Due section.

## Build order

Vertical slices, each one shippable to the user's phone and usable on its own. Approved on 2026-10-28, and ready to slice into implementation issues.

0. **Walking skeleton**
   - **Project**: `dev.alializadeh.orbit` and `.debug`; the **release keystore generated once** and stored per ADR 0005; the version catalog, Hilt, R8 release build with keep rules.
   - **Shell**: the four-tab Navigation 3 shell with stub tabs, and `MaterialExpressiveTheme` with Orbit violet through MaterialKolor plus the Outfit/Vazirmatn fallback fonts.
   - **Strings**: `values`/`values-fa`, with the parity test and lint errors on.
   - **Data**: Room 3 with the bundled driver and an exported schema. A Person with only a Name: add, list, detail. **Settles whether DAO tests run on the JVM.**
   - **Backup format spike**:
     - `VACUUM INTO`, then a ZIP, then age/kage through the Save picker, then a re-read and verify;
     - the age test vectors and `age` CLI interop test;
     - **the on-device scrypt measurement on the user's phone.** logN=17 stays unless a Backup or Restore takes more than about 3 s or runs out of memory, in which case it drops to 16.
   - **Tests**: a Roborazzi baseline.
1. **Language, dates and first run**
   - Per-app locales and RTL, `OrbitNumberFormatter` and "Use Latin digits".
   - The Solar Hijri converter with its table test, Orbit's date patterns, the wheel picker, and Primary calendar.
   - The backed-up preferences table.
   - Settings → Language & dates and Appearance (colour source).
   - Welcome, creating the seed Rings in the chosen language.
2. **Person profile**: the full Add/Edit form and Person detail (except Important dates, Gift ideas and Interactions), with:
   - Contact methods, their actions and the primary star;
   - Likes, Dislikes and Can help with with autocomplete, and Tags inline;
   - Ring choice and the Cadence override;
   - the photo picker, the crop screen and Coil;
   - the duplicate notice, Archive, Unarchive and Delete;
   - the basic People list sorted by Name.
3. **Important dates and Gift ideas**:
   - the Important date sheet (the default lead time only), occurrence resolution, the years count and the second-Calendar display;
   - the Gift ideas sheet and Mark as given;
   - Upcoming's **Today** and **Coming up** sections and its empty states.
4. **Interactions, Cadence and Rings**:
   - the Log Interaction sheet (several People, edit and delete) and All Interactions;
   - Due, Snooze, and the Cadence line with wavy progress;
   - Upcoming's **Due** section and the quick-action sheet;
   - Settings → Rings (cap of 6) and Tags (rename, merge, delete).
5. **Backup and Restore**: the full flows, built on the slice-0 format:
   - passphrase setup with the Keystore wrap, the every-third check, Change passphrase;
   - verified Backups and the staged atomic Restore, including from Welcome;
   - the nudge card and its interval, Settings → Backup.

   From here on the user can keep real data, so the **schema is frozen at version 1** at the end of this slice, and every later change ships a migration and its test.
6. **Reminders and notifications**:
   - the reminder planner and `reconcile()` with every trigger, the alarm and receivers;
   - the two channels, Reminder and digest notifications with their actions and cleanup;
   - lead-time overrides on the date sheet;
   - the permission timing and the battery-exemption ask;
   - Settings → Reminders, Reminder health, and the Upcoming warning card;
   - the on-device alarm checklist.
7. **App lock**: the gate, re-lock timing and picker exemption, the locked screen, the first-run offer, the Recents protection, the discreet/detailed notification rendering and `setAuthenticationRequired`, the paused warning, and Settings → App lock.
8. **Search, filter and sort**:
   - the `SearchIndex` with normalisation, tiers and "why it matched";
   - the chip row, the filter sheets, the sort and its headers;
   - the Archived view, the "Filtered · Clear" bar, chip-tap searches from Person detail, and Tag → Show People.
9. **Orbit view**: drawing, drift, glow, badges, density, zoom, gestures, its own filters, motion and "Remove animations", the legend, and TalkBack.
10. **Release hardening**: About (licences and the crash log), the full Roborazzi set in en/fa × light/dark, a review of every Persian string, and the manual device checklist on the user's phone.

The ordering puts Backup (5) before notifications (6) on purpose: once the data model is complete, the user can start entering real People, and Backup has to exist before that. Slices 6–9 depend only on 0–5, so they can run in any order or in parallel. The exceptions are App lock (7), which needs 6 for discreet notifications, and the Orbit view (9), which reuses the filters from 8.
