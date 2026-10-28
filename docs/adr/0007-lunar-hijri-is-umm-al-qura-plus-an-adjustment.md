# Lunar Hijri dates are Umm al-Qura plus a user adjustment

Iran's official Lunar Hijri calendar starts each month from a moon sighting announced a day or two before, so no table can know it years ahead. Orbit computes Lunar Hijri with the Umm al-Qura table instead, through `java.time.chrono.HijrahDate`. Android and the JDK ship the same table (`hijrah-config-Hijrah-umalqura_islamic-umalqura.properties`, compared byte for byte in late 2026), so the JVM tests check the data the phone uses, and it covers 1300–1600 AH. On top of it, one backed-up preference moves every Lunar Hijri date by −2…+2 days. That's crude: Iran and Umm al-Qura differ by 0–2 days, and not by the same amount every month. But it's honest, it can be fixed by hand when a day is wrong, and it needs no network and no yearly data update. Bundling a table of Iran's announced month starts would be right only for past months and would need an app update every year, so we didn't.

## Consequences

- The adjustment is process-wide state (`LunarHijriAdjustment`), read by every conversion, so no screen can disagree with another or with the reminder planner. Flows that compute occurrences combine with its flow so they recompute when it changes. Tests that touch it set it and put it back.
- Changing the adjustment moves Lunar Hijri dates already entered, including their years. A date's own (Calendar, month, day, year) never changes, as ADR 0003 requires.
- Adding a Calendar is a schema change in effect, because an older Orbit couldn't read `LunarHijri` in a Backup: the adjustment column brings schema version 2, and an older Orbit refuses a Backup with a newer schema.
