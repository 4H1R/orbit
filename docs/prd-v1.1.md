# Orbit v1.1: product requirements

Four additions to [v1](prd-v1.md), found by looking at another Persian offline app (Rooznegar, a calendar and planner) and chosen by the user on 2026-10-28. Everything in v1 still holds unless a section here says otherwise. Words are in the [glossary](../GLOSSARY.md); UI wording follows the [copy deck](copy-deck-v1.md), whose v1.1 strings are in its [v1.1 section](copy-deck-v1.md#v11-strings).

The v1 non-goals still hold, with one change: **a home-screen widget is now in scope** (slice 14). "Sharing" stays out: Send wishes hands a message the user wrote to another app, it never shares Orbit's data.

## Send wishes

A ready, editable message for someone on their day, sent through their own messaging app.

- **Where**:
  - Upcoming's **Today**: a Send wishes button on the hero card and an icon on the other rows.
  - The **on-the-day Reminder** notification: a third action, Send wishes, after [contact] and Mark as contacted. It opens Orbit on Person detail with the sheet open. Like every action, it's only there when notifications are detailed.
  - **Person detail**: on an Important date's row while that date is today.
  - An **Occasion's** People (below).
- **The sheet**, *Wishes for Sara*:
  - **Message**: a text field holding the default text for the date (Birthday, Anniversary, Custom date, or the Occasion), which the user can edit.
  - **Language**: English / فارسی. It starts in the language of the script of the Person's Name, falling back to the UI language. Switching it puts that language's default text back.
  - **Name in the message**: the Nickname if there is one, else the first word of the Name, in the message's language. If the Name's script doesn't match, Name in other script is used when it does.
  - **Send with**: one choice per Contact method that can carry a message, plus **Other app** (the system share sheet). It starts on the primary Contact method if that can carry a message, else on the first one that can, else on Other app.
    - The text is filled in for SMS (`smsto:` + `sms_body`), WhatsApp (`wa.me/<number>?text=`), Telegram usernames (`t.me/<username>?text=`) and Email (`mailto:` with a body).
    - Signal, Telegram phone numbers, Instagram, X and LinkedIn can't take a text: Orbit copies the message (`sensitiveClipData`), opens the chat and says "Message copied. Paste it in the chat."
    - Address and Link aren't offered.
  - **Log as a message**: a checkbox, on by default. Send then logs an Interaction of type Message, dated today, with no note.
  - **Send** opens the app with plain `startActivity` (it counts as time away for App lock) and closes the sheet.
- **Default texts** follow the copy deck's tone rules (no emoji, no exclamation marks). The user reviews every Persian one. Custom dates get a neutral text, because a custom label can be anything, a memorial included.

## Lunar Hijri dates

A third Calendar for Important dates and Occasions: **Lunar Hijri** (قمری). See [ADR 0007](adr/0007-lunar-hijri-is-umm-al-qura-plus-an-adjustment.md).

- **Scope**: the Important date sheet's and the Occasion editor's Calendar toggle gain Lunar Hijri. The **Primary calendar** stays Gregorian or Solar Hijri, and the Interaction date picker is unchanged.
- **Calculation**: Umm al-Qura (`java.time.chrono.HijrahDate`), whose table is the same on Android and the JVM. Years run from 1317 AH (1900 CE) to the current year, inside the table's 1300–1600 AH.
- **Lunar date adjustment**: Settings → Language & dates has a −2…+2 day setting, default 0. It moves every Lunar Hijri date by that many days, so it can follow Iran's official calendar when that differs from Umm al-Qura. It's a backed-up preference, so this is **schema version 2**.
- **Rules from v1 that carry over**: a date recurs in its own Calendar (a Lunar Hijri date moves about 11 days earlier each solar year, and can fall twice in one Gregorian year); a missing day 30 falls on the 29th; the years count is in the date's own Calendar (lunar years); a date in another Calendar shows its own Calendar after the Primary one; "1 month before" counts back a lunar month.
- **Month names**: Muharram, Safar, Rabi' al-awwal, Rabi' al-thani, Jumada al-ula, Jumada al-akhirah, Rajab, Sha'ban, Ramadan, Shawwal, Dhu al-Qa'dah, Dhu al-Hijjah / محرم، صفر، ربیع‌الاول، ربیع‌الثانی، جمادی‌الاول، جمادی‌الثانی، رجب، شعبان، رمضان، شوال، ذی‌القعده، ذی‌الحجه.

## Occasions

A yearly day that isn't one Person's, on which the user wants to reach out to a group: Nowruz to everyone, Teacher's Day to the Tag Teachers, Mother's Day to Mum.

- **What an Occasion is**: a name (built-in, or the user's own), a recurring day with no year in any of the three Calendars, **who** it's for, and lead times (the default ones unless overridden, like an Important date).
  - **Who**: Everyone, or any of chosen Rings, Tags and People (a Person matching any of them is in). Archived People never are.
  - **Built-in list** (nothing is added by default): Nowruz (1 Farvardin), Yalda night (30 Azar), Sepandarmazgan (29 Bahman), Teacher's Day (12 Ordibehesht), Mother's Day (20 Jumada al-akhirah), Father's Day (13 Rajab), Eid al-Fitr (1 Shawwal), Eid al-Adha (10 Dhu al-Hijjah), Eid al-Ghadir (18 Dhu al-Hijjah), New Year's Day (1 January), Christmas (25 December), Valentine's Day (14 February). A built-in Occasion's name and wishes follow the UI language; its day can't be edited, its People and Reminders can.
  - Occasions on moving weekdays (a "second Sunday of May") aren't supported.
- **Settings → Occasions**: the list, soonest first. **Add** shows the built-in list (minus those already added) and **Custom occasion**. The editor has Name (custom only), Date (the wheel picker with No year only), Who, Reminders, and Delete.
- **Occasion screen**: its date and when, then its People (Upcoming's order: innermost Ring first, then name), each with Send wishes. Someone with an Interaction logged on the day shows a tick, and the header counts "3 of 12 reached". The menu has Edit.
- **Upcoming**: an Occasion is a row in **Today** ("Nowruz · 12 people", with an Occasion badge) and in **Coming up**, ordered with the Important dates, and it counts for "Next: …". Tapping it opens the Occasion screen. An Occasion with nobody in it isn't shown.
- **Reminders**: on a new **Occasions** channel, one notification slot per Occasion (`orbit.occasion.<id>`), at the Reminder time on each lead time, with the same posting, missing and expiry rules as Important dates. "Nowruz · in 7 days" with "12 people", and "Today is Nowruz" with "Send wishes to 12 people". Tapping opens the Occasion screen. No actions. Discreet: "An occasion is coming up · in 7 days" / "There's an occasion today", without the name.
- **Data**: tables `occasion`, `occasion_ring`, `occasion_tag` and `occasion_person` (CASCADE both ways), so this is **schema version 3**. They're in every Backup like the rest of the database.

## Home-screen widget

One resizable **Upcoming** widget (Glance), 4×2 by default, at least 2×2.

- **Content**: a header (Orbit, today in the Primary calendar), then Today's dates and Occasions, then Coming up, as many as fit, each with when ("Today", "In 3 days", then the date), then "3 people are due" when anyone is. Rows show the avatar (photo or initials) and the same title as Upcoming. With nothing to show: "All caught up".
- **Taps**: a row opens Person detail or the Occasion screen; the header and the Due line open Upcoming (at Due). Taps go through the non-exported `.OpenFromNotification` alias, so App lock applies.
- **App lock**: while notifications are discreet (App lock on, "Show details in notifications" off), the widget shows counts only, with no names or photos: "1 date today · 2 coming up · 3 due".
- **Updates**: every `reconcile()` refreshes it (data changes, Restore, App lock rendering, the alarm), and while a widget is placed the alarm also wakes at the next midnight. Text follows the UI language through `localizedContext()`.
- **Colours**: Orbit's colour scheme, light or dark with the phone.

## Build order

Each slice goes through the v1 gate (debug and release builds, unit tests with the age CLI, lint, Roborazzi) and the en/fa × light/dark screenshots.

11. **Send wishes**: the sheet and its texts, the contact intents with text, the Today buttons, Person detail on the day, the notification action, Log as a message.
12. **Lunar Hijri**: the Calendar, its month names, the picker toggle, the adjustment setting, schema 2 with its migration test.
13. **Occasions**: the tables (schema 3), Settings → Occasions with the built-in list, the Occasion screen, Upcoming rows, the planner's Occasion Reminders and the Occasions channel.
14. **Widget**: Glance, the widget and its discreet form, refresh from `reconcile()` and at midnight, licences.
