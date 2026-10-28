# Orbit

Orbit is a personal, offline relationship manager for one user: it remembers the people in their life, what matters to them, and when to reach out.

## People

**Person**:
Someone the user keeps in Orbit, with their photo, birthday, preferences, and how to reach them. Only a name is required.
_Persian_: فرد (plural افراد)
_Avoid_: Contact, friend, entry, record

**Archived**:
A Person set aside without being deleted: hidden from lists, the Upcoming feed and reminders until unarchived.
_Persian_: بایگانی‌شده
_Avoid_: Hidden, inactive, deleted

**Contact method**:
One way to reach a Person, such as a phone number, email address, or messenger handle, from a fixed set of types. At most one per Person is their **primary** Contact method, the one offered as the quick action.
_Persian_: راه ارتباطی
_Avoid_: Contact info, contact

**Likes**:
Things a Person enjoys, kept as a list of short items belonging to that Person alone.
_Persian_: دوست دارد
_Avoid_: Preferences, interests

**Dislikes**:
Things a Person avoids or doesn't enjoy, kept as a list of short items.
_Persian_: دوست ندارد
_Avoid_: Preferences, hates

**Can help with**:
Skills, access, or expertise a Person could offer the user's network, kept as a list of short items belonging to that Person alone.
_Persian_: در این‌ها کمک می‌کند
_Avoid_: Useful for, networking value, skills

**Ring**:
How close a Person is to the user; every Person sits in exactly one Ring, which sets their default Cadence. Rings are an ordered list, innermost first, that the user can edit.
_Persian_: حلقه
_Avoid_: Circle, group, tier, level

**Tag**:
A free-form label grouping People by context, such as Work or Gym; a Person can have many, and one Tag is shared by every Person it's on.
_Persian_: برچسب
_Avoid_: Category, label, group

## Staying in touch

**Interaction**:
A logged moment of contact with one or more People (a call, a message, meeting in person) on a date, with an optional note.
_Persian_: ارتباط
_Avoid_: Activity, event, touchpoint

**Cadence**:
How often the user wants to be in touch with a Person, e.g. every 30 days. It comes from their Ring unless overridden for that Person, and may be none.
_Persian_: فاصلهٔ ارتباط
_Avoid_: Frequency, interval, schedule

**Due**:
A Person is Due once the days since their latest Interaction (or since they were added, if there is none) reach their Cadence, and stays Due until an Interaction is logged or they're snoozed.
_Persian_: موعد ارتباط
_Avoid_: Overdue, late, neglected

**Snooze**:
Pushing back when a Due Person is next brought up, by a few days to a month, without logging an Interaction or changing their Cadence.
_Persian_: تعویق
_Avoid_: Dismiss, postpone

**Important date**:
A recurring date tied to one Person: their **Birthday** (at most one), an **Anniversary**, or a custom date the user names. The year is optional. It is kept in the Calendar it was entered in and recurs in that Calendar.
_Persian_: تاریخ مهم (Birthday تولد, Anniversary سالگرد)
_Avoid_: Event, Occasion (an Occasion isn't tied to one Person)

**Calendar**:
The calendar system a date is expressed in: **Gregorian** (میلادی), **Solar Hijri** (شمسی), or, for Important dates and Occasions only, **Lunar Hijri** (قمری). Orbit computes Lunar Hijri as Umm al-Qura plus the user's **Lunar date adjustment** of up to two days either way.
_Persian_: تقویم (Gregorian میلادی, Solar Hijri شمسی, Lunar Hijri قمری; Lunar date adjustment تنظیم تاریخ قمری)
_Avoid_: Shamsi, Jalali, Persian calendar, Qamari, Islamic calendar (in English)

**Primary calendar**:
The Calendar the user prefers to see dates in, chosen independently of the UI language: Gregorian or Solar Hijri, never Lunar Hijri.
_Persian_: تقویم اصلی
_Avoid_: Display calendar, default calendar

**Occasion**:
A yearly day that isn't one Person's, such as Nowruz, Eid al-Fitr, Teacher's Day or one the user names, on which the user wants to reach out to a group: everyone, or the People in chosen Rings, Tags or by name. Built-in Occasions come from a fixed list, and none is added until the user adds it.
_Persian_: مناسبت
_Avoid_: Holiday, event, Important date (an Important date belongs to one Person)

**Wishes**:
The message the user sends someone on their Important date or an Occasion. Orbit drafts it, the user edits it, and **Send wishes** hands it to the Person's messaging app.
_Persian_: تبریک (Send wishes فرستادن تبریک)
_Avoid_: Greeting card, congratulations, share

**Reminder**:
A notification ahead of, or on, an Important date. When it comes is set by the date's **lead times** (e.g. 7 days before and on the day), which are the user's defaults unless the date overrides them, and every Reminder arrives at the one **Reminder time** of day.
_Persian_: یادآوری
_Avoid_: Alert, alarm, notification (for this specific meaning)

**Upcoming**:
The Home screen: the Important dates coming soon, the People who are Due, and anything needing the user's attention.
_Persian_: پیش رو
_Avoid_: Feed, dashboard, timeline

**Orbit view**:
The tab that draws the user at the centre and every non-Archived Person on their Ring, with People who are Due drifting outward.
_Persian_: مدار
_Avoid_: Map, radar, galaxy

**Gift idea**:
A possible present noted for a Person, either still an idea or **given** (on a date).
_Persian_: ایدهٔ هدیه
_Avoid_: Wishlist

## Safety

**Backup**:
A file, encrypted with the Backup passphrase, holding all of the user's People data, photos and preferences, made by hand and saved wherever the user chooses.
_Persian_: پشتیبان
_Avoid_: Export, dump, archive

**Backup passphrase**:
The secret a Backup is encrypted with, chosen by the user and remembered by Orbit so later Backups need no typing. It can't be recovered if forgotten.
_Persian_: عبارت عبور پشتیبان
_Avoid_: Password, PIN, key

**Restore**:
Replacing everything in Orbit with the contents of a Backup, on the same or another device. Nothing is merged.
_Persian_: بازیابی
_Avoid_: Import, sync, merge

**App lock**:
An optional gate that asks for the phone's own screen lock (fingerprint, face, or the phone's PIN, pattern or password) before Orbit's content is shown, both when Orbit opens and when the user comes back after a while away. Orbit has no PIN or password of its own.
_Persian_: قفل برنامه
_Avoid_: Login, password, Orbit PIN
