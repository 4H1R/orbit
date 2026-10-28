# Device checklist (the user's real phone)

The emulator can't settle these: they depend on the phone's speed, its maker's battery rules, its lock screen, its GPU and how it behaves after a day idle. Run them on the phone that will hold real data, with the **release** build (signed with the real key, see the [README](../../README.md)) unless a step says otherwise. Note the phone model, Android version and date next to each result, and anything that fails as an issue.

Before starting: Settings → About shows the version you installed, and Settings → About → Share crash log says "No crashes recorded".

## 1. Backup cost (scrypt)

The rule ([ADR 0004](../adr/0004-backups-are-age-files.md)): Backups stay at **logN 17** unless a Backup or a Restore takes more than about **3 s**, or runs out of memory, on this phone; then `BACKUP_WRITE_WORK_FACTOR` drops to 16.

- [ ] Run the benchmark exactly as in [scrypt-measurement.md](scrypt-measurement.md) ("How to run it on the phone"; it uses the debug build and uninstalls it afterwards) and paste the `OrbitScrypt` lines into that file with the phone's model.
- [ ] Apply the rule to the 50 MiB Backup and Restore lines. If it says 16: change `BACKUP_WRITE_WORK_FACTOR` in `backup/BackupFormat.kt`, ADR 0004 and the PRD, in one change.
- [ ] Known limitation: if Orbit is killed in the middle of a Backup (seen once on the emulator, below), the unfinished file stays where you saved it, since only Orbit itself could delete it. An incomplete file can't be restored (Orbit reads a Backup to the end and checks it before touching anything); delete it by hand.
- [ ] With real data: Settings → Backup → Back up now. Time from "Backing up…" to "Backup saved and checked" (stopwatch is fine). Then Restore that file on the debug build (Welcome → Restore from a backup) and time "Opening the backup…".

## 2. Reminders arrive on time

- [ ] Settings → Reminders → Reminder health: everything "All set" (notifications, Alarms & reminders, battery Unrestricted, "Pause app activity if unused" off). If the phone's maker row has steps (Samsung, Xiaomi, OnePlus, Huawei…), follow them through See steps, and confirm each setting stuck after a reboot.
- [ ] Give a test Person a Birthday tomorrow (lead times Default: 1 week before + on the day) and set the Reminder time a few minutes ahead. Lock the phone and leave it. The "… · tomorrow" Reminder arrives within a minute of the Reminder time.
- [ ] **After a day idle**: leave the phone untouched, on battery, screen off, for 24 h (overnight is the real test: Doze and app standby). The on-the-day Reminder for that Birthday arrives at the Reminder time the next day, not when you pick the phone up.
- [ ] Reboot the phone and don't open Orbit: the next Reminder still arrives (the alarm is set again on boot).
- [ ] Change the time zone in system settings and back: the next Reminder keeps its local time.
- [ ] A Person whose Cadence has run out (a Ring with "every 1 day" and an Interaction logged 2 days ago) appears in the next day's "Keep in touch" digest, once.
- [ ] With a "… · in 7 days" Reminder showing, look at it the next morning (before opening Orbit): it says "in 6 days", and it didn't sound again.
- [ ] Only on an Android 12/12L phone: with a Reminder due in a few minutes, turn off Alarms & reminders for Orbit (this kills Orbit and removes its exact alarm) and don't open Orbit. The Reminder still arrives, up to an hour late (Orbit's inexact stand-by alarm), and Reminder health then shows the problem. Turn it back on.

## 3. Notifications on the lock screen

- [ ] With App lock **off**, on the locked phone: the on-the-day Reminder shows the discreet public text ("There's a birthday today") until the phone is unlocked, then the full one.
- [ ] Its actions (call/message and Mark as contacted) work from the notification shade; Mark as contacted logs an "Other" Interaction dated today (check Person detail).
- [ ] The single-Person digest's Snooze 1 week works from the shade; Person detail then says "Snoozed until …".
- [ ] With App lock **on** (Show details in notifications off): notifications never name anyone or show photos or gift ideas, and have no actions.
- [ ] With App lock on and **Show details in notifications** on: tapping an action on the lock screen asks for the phone's unlock first.

## 4. App lock and the biometric prompt

- [ ] Settings → App lock → turn on: the system prompt appears with fingerprint (or face) and "Use PIN"; both unlock. Turning it off asks again.
- [ ] Re-lock: with "Lock again: After 1 minute", leave Orbit (Home) for under a minute: no prompt on return; for over a minute: locked. "Immediately" locks on every return. Force-stop or reboot: always locked.
- [ ] Orbit's own pickers don't lock it: add a photo, Back up now (the Save picker), Restore (the file picker), all take longer than the re-lock time without a prompt on return.
- [ ] Sharing leaves Orbit: Settings → About → Share crash log (after a crash, or skip), or a contact action (Call): with "Immediately", coming back asks to unlock.
- [ ] Recents: with App lock on, Orbit's thumbnail in Recents is blank.
- [ ] Remove the phone's screen lock (then put it back): Upcoming shows "App lock is paused" and Orbit opens without a prompt; after setting the screen lock again, App lock works again.
- [ ] A notification tap while locked: unlock, then Orbit opens on that Person.

## 5. Battery settings through Reminder health

- [ ] Each "Fix"/"Turn on"/"Allow" button in Reminder health opens the right system screen, and coming back updates the row without restarting Orbit.
- [ ] On the maker's battery screen, set Orbit to the most permissive option ("Unrestricted", "No restrictions", "Don't optimise"), and check Orbit isn't in any "sleeping apps" or "deep sleeping apps" list.

## 6. The Orbit view on a real GPU

- [ ] With the real data (ideally 100+ People, many with photos): the Orbit tab opens smoothly, People spring out from "You", and the Due glow pulses without stutter.
- [ ] Pinch from 1× to 4× and pan around: smooth, photos sharp at 4×. Double-tap toggles 2× and back.
- [ ] Rings step from Avatars to Small to Dots as they fill, and a crowded spot zooms in when tapped rather than opening the wrong Person.
- [ ] System settings → Accessibility → Remove animations: the Orbit view has no entrance, pulse or transitions.

## 7. TalkBack on the Orbit view

- [ ] Turn on TalkBack. On the Orbit tab, swipe right through it: Rings are read innermost first, each as a heading ("Inner, 6 people, 2 due"), then its People ("Sara Ahmadi, due, last talked 34 days ago, birthday in 5 days"), Due People first.
- [ ] On a Person, the actions menu (three-finger tap or the TalkBack menu) offers the contact action, Log interaction and, when Due, Snooze; each works.
- [ ] Double-tap opens Person detail. Repeat once in Persian.

## 8. Language and the first-language-switch crash watch

Slice 1 saw one native crash (SIGSEGV, a null dereference inside the Android runtime under Compose) on the very first tap of «فارسی» on Welcome after a fresh install. It never came back, and slice 10 couldn't reproduce it on the emulator (below). Watch for it on the phone:

- [ ] Install the release build fresh (no Orbit data), tap «فارسی» on Welcome, then English, then «فارسی» again; finish first run in Persian.
- [ ] Settings → Language & dates: switch English ↔ فارسی a few times, then open each tab.
- [ ] If Orbit ever closes by itself: Settings → About → Share crash log. A Java crash is in it; a native crash (no report there) shows in `adb logcat -b crash` and `adb bugreport` (tombstones): keep both.
- [ ] On Android 12/12L only: with Orbit in Persian, force-stop it and wait for a Reminder: its text and the channel names in system settings are Persian (they're read from the stored language, not the phone's).

### What slice 10 tried on the emulator (2026-10-28)

Emulator `Pixel_9_Pro_API_35` (Android 15, x86_64 16 KB-page image on WHPX), debug build:

- 15 fresh starts (`pm clear`, launch, tap «فارسی» 2 s later) with animations on, then 30 taps toggling the language every 1.5 s and 30 every 0.3 s on Welcome;
- the same with all three animation scales at 0: 10 fresh starts tapping after 2 s, 10 tapping after 0.7 s (during the first frames), 30 fast toggles;
- 20 slow and 20 fast (animations off) language switches in Settings → Language & dates on the debug build, 12 more on the release build, and the release build's whole first run in Persian during the smoke test.

Result: **no crash in any of these**: the process id stayed the same through every toggle run and `adb logcat -b crash` stayed empty.

The emulator's own records point away from Orbit. Its 32 tombstones go back to 10 September: `system_server` (8), Google Play services (4), the Play Store (4), zygote, SurfaceFlinger, Bluetooth, Messages, the media provider, Digital Wellbeing and another app under development all died the same way, a "null pointer dereference" SIGSEGV, mostly inside ART-compiled Java code; 7 of today's 10 are outside Orbit. Orbit's 3 today (two debug, during earlier slices; one release, during this smoke test) look the same: the faulting frames are plain managed code (`TreeMap.compare`, `PersistentCompositionLocalHashMap.get`, Compose's draw pass), which can't dereference null natively; a null there is normally a Java `NullPointerException`, which Orbit's crash log would have caught. The release one hit while Orbit drew its first frame back from the Save picker during a Backup, at the same millisecond as ART's concurrent mark-compact GC was freeing the 128 MiB scrypt buffer; retrying the same Backup worked. Orbit has no native code of its own (its only native library is SQLite, on none of these stacks).

So the likeliest cause is this emulator image (x86_64, 16 KB pages, on WHPX) rather than Orbit; if it were Orbit, the phone would show it again. Hence the watch above, including during a Backup.
