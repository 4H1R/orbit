# Orbit v1 copy deck

The rules for writing Orbit's words in English and Persian, and the wording of the strings that are hard to get right. Every other string (button labels, field names, settings rows) is written during implementation under these rules. Settled in [Translation and copy](../.scratch/orbit-v1/issues/15-translation-and-copy.md).

Domain vocabulary, with each term's Persian UI word, is in the [glossary](../GLOSSARY.md).

## Rules

### Where strings live

- Android string resources: `values/strings.xml` (English) and `values-fa/strings.xml` (Persian). Counts use `<plurals>`.
- Persian's plural categories are `one` (which includes 0) and `other`. Both are usually the same text, because Persian nouns stay singular after a number (۳ روز). A count of 0 gets its own string, never a plural.
- Every count is a plural, even one that is always 2 or more. A sentence with two counts is a plural on the first, and takes the second already formatted as its own plural ("Backed up 84 people · 41 photos" = `backed_up_result` with `backup_result_photos` as `%2$s`). Numbers written into a sentence (12 characters, 30 days) are placeholders too, so the digit setting applies.
- Keys are `screen_element_purpose`, e.g. `person_detail_delete_confirm_title`. The keys below are the resource names.
- **Numbers are never formatted with `%d`.** They go through `OrbitNumberFormatter` and are passed as `%s`, so "Use Latin digits" holds everywhere.
- **Every user-supplied value is bidi-isolated** when it's placed in a sentence: names, Tags, Ring names, custom date labels, Contact values.

### Keeping the two languages in step

- Persian is written in the same change as the English, never afterwards. An agent may draft it. The user reviews every new or changed Persian string in that change's review. Nothing ships machine-translated and unread.
- Lint `MissingTranslation` and `ExtraTranslation` are errors that fail the build.
- A JVM test checks that both files have the same keys, the same placeholders (count and order) and the required plural quantities.
- Roborazzi screenshots in en and fa catch layout breakage.
- **Stale translations**: when an English string's *meaning* changes, rename its key. The old Persian becomes an extra translation and the new key a missing one, so the build fails until the Persian is rewritten. Changes to wording only keep the key.

### Tone

- **English**:
  - Warm and brief, in sentence case, with British spelling ("colour", "centre", "licences").
  - No exclamation marks and no emoji.
  - No pronouns for People: write "Sara's gift ideas", never "her".
  - Domain terms aren't capitalised mid-sentence in the UI ("Log interaction", "3 people"). The capitals are a docs convention only.
- **Persian**:
  - Standard written register (نوشتاری), not colloquial: «می‌خواهید», not «می‌خوای».
  - The user is addressed as **شما**.
  - Buttons are short verbs or nouns («ذخیره»، «لغو»، «افزودن»).
  - Warmth comes from word choice («احوال‌پرسی»), not slang.
  - Spelling: a half-space (ZWNJ) in prefixes, suffixes and compounds («می‌شود», «برچسب‌ها», «یک‌شنبه», «به‌صورت»), and «جست‌وجو» for search.
  - **Never attach a suffix to a placeholder**, because the name's ending is unknown. Write «امروز تولد %s است», never a contraction like «…ست».

### Separator

- Every "A · B" line joins its parts with one string resource, `separator`: `" · "` in English and `"، "` in Persian. The rule holds whatever the digit setting. (Next to Persian digits, «·» reads as zero.)
- Lists inside a line (Gift ideas, several names) also use «، » in Persian. So a list never shares a line with the separator: Gift ideas go on their own line.
- Exception: a label followed by a button ("Filtered · Clear") isn't an "A · B" line.

### Names and seed data

- The app name stays **Orbit** in Latin script in both languages: the launcher label, notifications and copy. Only the Orbit tab is translated: **مدار**.
- **Seed Rings** are created once, when first run finishes, in the language chosen on Welcome. After that they're user data and never re-translated. A Restore brings the Backup's own Rings.

  | key | English | Persian | default Cadence |
  |---|---|---|---|
  | `seed_ring_inner` | Inner | صمیمی | 14 days |
  | `seed_ring_close` | Close | نزدیک | 30 days |
  | `seed_ring_friends` | Friends | دوستان | 90 days |
  | `seed_ring_acquaintances` | Acquaintances | آشنایان | none |

- **Contact method types** are app vocabulary, so they follow the UI language. Their labels ("work", "home") are user text.

  | key | English | Persian | action |
  |---|---|---|---|
  | `contact_type_phone` | Phone | تلفن | Call «تماس», SMS «پیامک» |
  | `contact_type_whatsapp` | WhatsApp | واتس‌اپ | WhatsApp «واتس‌اپ» |
  | `contact_type_telegram` | Telegram | تلگرام | Telegram «تلگرام» |
  | `contact_type_signal` | Signal | سیگنال | Signal «سیگنال» |
  | `contact_type_email` | Email | ایمیل | Email «ایمیل» |
  | `contact_type_instagram` | Instagram | اینستاگرام | Instagram «اینستاگرام» |
  | `contact_type_linkedin` | LinkedIn | لینکدین | LinkedIn «لینکدین» |
  | `contact_type_x` | X | ایکس | X «ایکس» |
  | `contact_type_address` | Address | نشانی | Map «نقشه» |
  | `contact_type_link` | Website / Other link | وب‌سایت / پیوند دیگر | Open «باز کردن» |

### Ordinals

- English: digits plus a suffix ("1st", "2nd", "3rd", "11th", "21st").
- Persian: always spelled out, from a small number-to-words formatter (up to 999): «اولین», «سومین», «پنجمین», «بیست‌ویکمین», «سی‌امین». There are no digits, so the digit setting doesn't apply.

## Strings

`%1$s`, `%2$s`… are placeholders. Names are the Person's displayed name. Numbers are already formatted.

### Relative time and Cadence

| key | English | Persian |
|---|---|---|
| `time_today` | Today | امروز |
| `time_tomorrow` | Tomorrow | فردا |
| `time_in_days` (2–7) | In %1$s days | %1$s روز دیگر |
| `time_yesterday` | yesterday | دیروز |
| `time_days_ago` (2–59) | %1$s days ago | %1$s روز پیش |
| `time_months_ago` (60 days–23 months) | %1$s months ago | %1$s ماه پیش |
| `time_years_ago` (2+ years) | %1$s years ago | %1$s سال پیش |
| `last_interaction` | Last talked %1$s | آخرین ارتباط: %1$s |
| `no_interactions_yet` | No interactions yet | هنوز ارتباطی ثبت نشده |
| `cadence_every_days` | every %1$s days | هر %1$s روز |
| `cadence_every_weeks` | every %1$s weeks | هر %1$s هفته |
| `cadence_every_months` | every %1$s months | هر %1$s ماه |
| `cadence_none` | No cadence | بدون فاصلهٔ ارتباط |

The 1-unit forms ("every week", «هر هفته»…) are plurals. Beyond 7 days ahead, Upcoming shows the date itself.

### Reminder notifications (full)

| key | English | Persian |
|---|---|---|
| `reminder_title_birthday` | %1$s's birthday · %2$s | تولد %1$s، %2$s |
| `reminder_title_anniversary` | %1$s's anniversary · %2$s | سالگرد %1$s، %2$s |
| `reminder_title_custom` | %1$s's %2$s · %3$s | %2$s %1$s، %3$s |
| `reminder_title_birthday_today` | Today is %1$s's birthday | امروز تولد %1$s است |
| `reminder_title_anniversary_today` | Today is %1$s's anniversary | امروز سالگرد %1$s است |
| `reminder_title_custom_today` | Today is %1$s's %2$s | امروز %2$s %1$s است |
| `years_birthday` | Turns %1$s | %1$s ساله می‌شود |
| `years_anniversary` | %1$s anniversary | %1$s سالگرد |
| `years_custom` | %1$s years | %1$s سال |
| `reminder_gift_ideas` | Gift ideas: %1$s | ایده‌های هدیه: %1$s |
| `list_and_more` | %1$s and %2$s more | %1$s و %2$s مورد دیگر |
| `action_mark_as_contacted` | Mark as contacted | ارتباط گرفتم |
| `channel_important_dates_name` | Important dates | تاریخ‌های مهم |
| `channel_important_dates_description` | Reminders before birthdays, anniversaries and other important dates | یادآوری پیش از تولدها، سالگردها و دیگر تاریخ‌های مهم |

- In the titles, the "when" is "in 7 days" / «۷ روز دیگر», or "tomorrow" / «فردا».
- `years_anniversary` takes the ordinal ("5th" / «پنجمین»).
- The Gift ideas line names two ideas, then uses `list_and_more`.

### Cadence digest (full)

| key | English | Persian |
|---|---|---|
| `digest_title` | Time to catch up with %1$s | وقت احوال‌پرسی از %1$s است |
| `digest_body_one` | Last talked %1$s | آخرین ارتباط: %1$s |
| `digest_body_one_never` | Added %1$s, no interactions yet | %1$s اضافه شد، هنوز ارتباطی ثبت نشده |
| `names_two` | %1$s and %2$s | %1$s و %2$s |
| `names_three` | %1$s, %2$s and %3$s | %1$s، %2$s و %3$s |
| `names_more` | %1$s, %2$s and %3$s others | %1$s، %2$s و %3$s نفر دیگر |
| `digest_body_several` | %1$s people are due | موعد ارتباط با %1$s نفر رسیده است |
| `action_snooze_week` | Snooze 1 week | یک هفته بعد |
| `channel_keep_in_touch_name` | Keep in touch | در ارتباط ماندن |
| `channel_keep_in_touch_description` | A daily note of who it's time to catch up with | یادداشتی روزانه از کسانی که وقت احوال‌پرسی از آن‌هاست |

### Discreet notifications

These are used while App lock is on (without "Show details in notifications"), and as the public version of every notification. Custom date labels are never shown.

| key | English | Persian |
|---|---|---|
| `discreet_birthday_soon` | A birthday is coming up · %1$s | تولدی در پیش است، %1$s |
| `discreet_anniversary_soon` | An anniversary is coming up · %1$s | سالگردی در پیش است، %1$s |
| `discreet_date_soon` | An important date is coming up · %1$s | تاریخ مهمی در پیش است، %1$s |
| `discreet_birthday_today` | There's a birthday today | امروز تولد یک نفر است |
| `discreet_anniversary_today` | There's an anniversary today | امروز سالگرد یک نفر است |
| `discreet_date_today` | There's an important date today | امروز یک تاریخ مهم دارید |
| `discreet_digest_one` | Time to catch up with someone | وقت احوال‌پرسی از یک نفر است |
| `discreet_digest_several` | Time to catch up with %1$s people | وقت احوال‌پرسی از %1$s نفر است |

### First run and App lock

| key | English | Persian |
|---|---|---|
| `welcome_title` | Welcome to Orbit | به Orbit خوش آمدید |
| `welcome_line` | Keep the people who matter in orbit. | کسانی را که برایتان مهم‌اند در مدار نگه دارید. |
| `welcome_language` | Language | زبان |
| `welcome_primary_calendar` | Primary calendar | تقویم اصلی |
| `welcome_latin_digits` | Use Latin digits | استفاده از ارقام لاتین |
| `welcome_continue` | Continue | ادامه |
| `welcome_restore` | Restore from a backup | بازیابی از پشتیبان |
| `app_lock_offer_title` | Lock Orbit with your fingerprint or screen lock? | Orbit با اثر انگشت یا قفل صفحهٔ گوشی قفل شود؟ |
| `app_lock_offer_body` | Orbit will ask for it each time you open it. You can change this in Settings. | هر بار که Orbit را باز کنید از شما خواسته می‌شود. بعداً در تنظیمات می‌توانید تغییرش دهید. |
| `app_lock_offer_turn_on` | Turn on | روشن کردن |
| `common_not_now` | Not now | فعلاً نه |
| `locked_title` | Orbit is locked | Orbit قفل است |
| `locked_unlock` | Unlock | باز کردن قفل |
| `locked_prompt_title` | Unlock Orbit | باز کردن قفل Orbit |
| `app_lock_needs_screen_lock` | Set a screen lock on your phone first | اول برای گوشی قفل صفحه تنظیم کنید |
| `app_lock_paused` | App lock is paused: this phone has no screen lock | قفل برنامه متوقف است: این گوشی قفل صفحه ندارد |
| `app_lock_set_screen_lock` | Set screen lock | تنظیم قفل صفحه |

The language picker shows each language in its own script: English / فارسی.

### Empty states and search

| key | English | Persian |
|---|---|---|
| `empty_no_people_title` | Keep the people who matter in orbit | کسانی را که برایتان مهم‌اند در مدار نگه دارید |
| `empty_no_people_body` | Add someone you want to stay close to. | کسی را اضافه کنید که می‌خواهید با او در ارتباط بمانید. |
| `action_add_person` | Add person | افزودن فرد |
| `upcoming_all_caught_up` | All caught up | فعلاً خبری نیست |
| `upcoming_next` | Next: %1$s | بعدی: %1$s |
| `search_no_matches` | No one matches | کسی پیدا نشد |
| `search_clear_filters` | Clear filters | پاک کردن فیلترها |
| `filter_bar_filtered` | Filtered | فیلتر شده |
| `filter_bar_clear` | Clear | پاک کردن |
| `search_archived_matches` | %1$s archived people match | %1$s فرد بایگانی‌شده هم پیدا شد |
| `archived_empty` | No archived people | هیچ فردی بایگانی نشده |
| `orbit_empty` | People you add appear here, on their ring | افرادی که اضافه کنید این‌جا روی حلقهٔ خودشان دیده می‌شوند |

`upcoming_next` takes the date and when, e.g. "Sara's birthday · 12 Jan" / «تولد سارا، ۲۲ دی».

### Confirmations and snackbars

| key | English | Persian |
|---|---|---|
| `delete_person_title` | Delete %1$s? | %1$s حذف شود؟ |
| `delete_person_body` | This can't be undone. Shared interactions stay for the others. | این کار برگشت‌پذیر نیست. ارتباط‌های مشترک برای دیگران می‌مانند. |
| `common_delete` / `common_cancel` | Delete / Cancel | حذف / لغو |
| `archived_snackbar` | %1$s archived | %1$s بایگانی شد |
| `common_undo` | Undo | واگرد |
| `snoozed_snackbar` | Snoozed %1$s for %2$s | ارتباط با %1$s %2$s به تعویق افتاد |
| `discard_title` | Discard changes? | تغییرات کنار گذاشته شود؟ |
| `discard_confirm` / `discard_keep` | Discard / Keep editing | کنار گذاشتن / ادامهٔ ویرایش |
| `duplicate_name_title` | You already have someone called %1$s | فردی به نام %1$s از قبل دارید |
| `duplicate_save_anyway` / `duplicate_go_back` | Save anyway / Go back | باز هم ذخیره شود / برگشت |
| `tag_delete_title` | Remove %1$s from %2$s people? | برچسب %1$s از %2$s نفر برداشته شود؟ |
| `tag_delete_confirm` | Remove | برداشتن |
| `tag_merge_title` | Merge into %1$s? | در %1$s ادغام شود؟ |
| `tag_merge_body` | Everyone tagged %1$s will be tagged %2$s. | همهٔ افراد دارای برچسب %1$s برچسب %2$s می‌گیرند. |
| `tag_merge_confirm` | Merge | ادغام |
| `restore_replace_title` | Replace everything in Orbit? | همه‌چیز در Orbit جایگزین شود؟ |
| `restore_replace_body` | Your %1$s people, their photos and your settings will be replaced by this backup from %2$s (%3$s). This can't be undone. | %1$s فرد فعلی، عکس‌هایشان و تنظیمات شما با این پشتیبانِ %2$s (%3$s) جایگزین می‌شوند. این کار برگشت‌پذیر نیست. |
| `restore_replace_confirm` | Replace | جایگزینی |
| `restore_back_up_first` | Back up current data first | اول از داده‌های فعلی پشتیبان بگیرید |
| `restored_snackbar` | Restored %1$s people | %1$s نفر بازیابی شدند |
| `backed_up_result` | Backed up %1$s people · %2$s | از %1$s نفر و %2$s پشتیبان گرفته شد |
| `backup_result_photos` | %1$s photos | %1$s عکس |

`restore_replace_body`'s `%3$s` is `restore_summary_people` ("84 people" / «۸۴ نفر»).

`snoozed_snackbar` takes a duration: "3 days" / «۳ روز», "1 week" / «یک هفته», "1 month" / «یک ماه».

### Backup passphrase and nudge

| key | English | Persian |
|---|---|---|
| `passphrase_warning` | If you forget this passphrase, no one can open your backups, not even Orbit. | اگر این عبارت عبور را فراموش کنید، هیچ‌کس نمی‌تواند پشتیبان‌هایتان را باز کند، حتی Orbit. |
| `passphrase_warning_ack` | I understand | متوجه هستم |
| `passphrase_rule_length` | At least %1$s characters | دست‌کم %1$s نویسه |
| `passphrase_rule_ascii` | Only English letters, numbers and symbols | فقط حروف انگلیسی، عدد و نماد |
| `passphrase_check_body` | Type your backup passphrase to check you still remember it | عبارت عبور پشتیبان را وارد کنید تا مطمئن شویم هنوز آن را به خاطر دارید |
| `restore_wrong_passphrase` | That passphrase doesn't open this backup | این عبارت عبور این پشتیبان را باز نمی‌کند |
| `restore_newer_version` | This backup was made by a newer version of Orbit. Update Orbit to restore it. | این پشتیبان با نسخهٔ جدیدتری از Orbit ساخته شده است. برای بازیابی، Orbit را به‌روز کنید. |
| `backup_nudge_last` | Last backup %1$s | آخرین پشتیبان: %1$s |
| `backup_nudge_never` | You haven't made a backup yet | هنوز پشتیبانی نگرفته‌اید |
| `backup_now` | Back up now | پشتیبان‌گیری |

The nudge card's dismiss button is `common_not_now`.

### Orbit view

| key | English | Persian |
|---|---|---|
| `orbit_legend_title` | Reading your orbit | راهنمای مدار |
| `orbit_legend_rings` | You're in the centre. Each circle is a ring, closest people innermost. | شما در مرکزید. هر دایره یک حلقه است و نزدیک‌ترین افراد در درونی‌ترین حلقه‌اند. |
| `orbit_legend_drift` | A glowing person drifting outward is due. The further out, the longer it's been. | فردی که می‌درخشد و به بیرون می‌لغزد، موعد ارتباطش رسیده است. هرچه دورتر، مدت بیشتری گذشته. |
| `orbit_legend_badge` | An important date in the next %1$s days. Filled means today. | تاریخ مهمی در %1$s روز آینده. توپُر یعنی امروز. |
| `orbit_legend_angle` | Where someone sits around the circle doesn't mean anything. | جای هر فرد روی دایره معنای خاصی ندارد. |
| `orbit_center_you` | You | شما |
| `orbit_ring_count` | %1$s · %2$s | %1$s، %2$s |
| `orbit_a11y_ring` | %1$s, %2$s people, %3$s due | %1$s، %2$s نفر، %3$s نفر در موعد ارتباط |
| `orbit_a11y_person_due` | due | در موعد ارتباط |

- Each legend row has a small drawn example. The text never names a colour.
- The TalkBack Person label joins its parts with commas (", " in English, «، » in Persian), because TalkBack pauses on commas: "Sara Ahmadi, due, last talked 34 days ago, birthday in 5 days" / «سارا احمدی، در موعد ارتباط، آخرین ارتباط ۳۴ روز پیش، تولد ۵ روز دیگر».

## v1.1 strings

Added with [v1.1](prd-v1.1.md). The same rules apply.

### Send wishes

The default Wishes are sent to the Person, in the user's voice, so the Persian ones address the Person as «تو» (warm and personal) rather than the UI's «شما». A Custom date's text stays neutral, because its label can be anything, a memorial included. The name is the Nickname or the first word of the name (`%1$s`).

| key | English | Persian |
|---|---|---|
| `wishes_send` | Send wishes | فرستادن تبریک |
| `wishes_sheet_title` | Wishes for %1$s | تبریک برای %1$s |
| `wishes_send_with` | Send with | فرستادن با |
| `wishes_other_app` | Other app | برنامهٔ دیگر |
| `wishes_copy_hint` | This app can't take a message, so Orbit copies it for you to paste in the chat | این برنامه پیام را نمی‌پذیرد؛ Orbit آن را کپی می‌کند تا در گفت‌وگو بچسبانید |
| `wishes_copied_snackbar` | Message copied. Paste it in the chat. | پیام کپی شد. آن را در گفت‌وگو بچسبانید. |
| `wishes_log_as_message` | Log as a message | ثبت به‌عنوان ارتباط پیامی |
| `wishes_default_birthday` | Happy birthday, %1$s. Wishing you a wonderful year ahead. | %1$s عزیز، تولدت مبارک. سالی پر از شادی و سلامتی برایت آرزو می‌کنم. |
| `wishes_default_anniversary` | Happy anniversary, %1$s. Wishing you many more happy years. | %1$s عزیز، سالگردت مبارک. سال‌های شاد بسیاری برایت آرزو می‌کنم. |
| `wishes_default_custom` | Thinking of you today, %1$s. | %1$s عزیز، امروز به یادت هستم. |

### Lunar Hijri

| key | English | Persian |
|---|---|---|
| `calendar_lunar_hijri` | Lunar Hijri | قمری |
| `language_dates_lunar_title` | Lunar Hijri dates | تاریخ‌های قمری |
| `lunar_adjustment_none` | As calculated | طبق محاسبه |
| `lunar_adjustment_earlier` | %1$s day(s) earlier | %1$s روز زودتر |
| `lunar_adjustment_later` | %1$s day(s) later | %1$s روز دیرتر |
| `lunar_adjustment_footer` | Orbit calculates Lunar Hijri dates with the Umm al-Qura calendar. When Iran's official calendar starts a month on another day, move every Lunar Hijri date by a day or two to match. | Orbit تاریخ‌های قمری را با تقویم ام‌القری محاسبه می‌کند. اگر تقویم رسمی ایران ماه را در روز دیگری آغاز کند، همهٔ تاریخ‌های قمری را یک یا دو روز جابه‌جا کنید تا یکی شوند. |

Month names are data in `format/CalendarNames`: Muharram … Dhu al-Hijjah / محرم … ذی‌الحجه. Each adjustment row previews today's Lunar Hijri date with it, so the user picks the one that matches Iran's calendar.

### Occasions

Built-in names follow the UI language; the user's own are user text (bidi-isolated in sentences). The Wishes texts follow the Send wishes rules above.

| key | English | Persian |
|---|---|---|
| `occasion_name_*` | Nowruz, Yalda night, Sepandarmazgan, Teacher's Day, Mother's Day, Father's Day, Eid al-Fitr, Eid al-Adha, Eid al-Ghadir, New Year's Day, Christmas, Valentine's Day | نوروز، شب یلدا، سپندارمذگان، روز معلم، روز مادر، روز پدر، عید فطر، عید قربان، عید غدیر، سال نو میلادی، کریسمس، ولنتاین |
| `wishes_occasion_nowruz` | Happy Nowruz, %1$s. Wishing you a wonderful new year. | %1$s عزیز، نوروزت پیروز. سال نو مبارک. |
| `wishes_occasion_eid_al_fitr` | Eid Mubarak, %1$s. | %1$s عزیز، عید فطر مبارک. |
| `settings_occasions_title` | Occasions | مناسبت‌ها |
| `occasions_empty` | Add Nowruz, Eid or a day of your own, and Orbit reminds you whom to send wishes to. | نوروز، عیدها یا روزی دلخواه را اضافه کنید تا Orbit یادآوری کند به چه کسانی تبریک بگویید. |
| `occasion_who` | Who it's for | برای چه کسانی |
| `occasion_reaches` | Reaches %1$s people | برای %1$s نفر |
| `occasion_reached` | Reached %1$s of %2$s | با %1$s نفر از %2$s نفر ارتباط گرفتید |
| `occasion_delete_body` | Its reminders stop. Your people aren't changed. | یادآوری‌هایش متوقف می‌شود. افرادتان تغییری نمی‌کنند. |
| `reminder_title_occasion` | %1$s · %2$s | %1$s، %2$s |
| `reminder_title_occasion_today` | Today is %1$s | امروز %1$s است |
| `reminder_occasion_people` | %1$s people to send wishes to | تبریک به %1$s نفر |
| `reminder_occasion_people_today` | Send wishes to %1$s people | به %1$s نفر تبریک بگویید |
| `discreet_occasion_soon` | An occasion is coming up · %1$s | مناسبتی در پیش است، %1$s |
| `discreet_occasion_today` | There's an occasion today | امروز یک مناسبت دارید |
| `channel_occasions_name` | Occasions | مناسبت‌ها |

### Widget

| key | English | Persian |
|---|---|---|
| `widget_upcoming_description` | Today's dates and occasions, what's coming up, and who's due | تاریخ‌ها و مناسبت‌های امروز، آنچه در پیش است و کسانی که موعد ارتباطشان رسیده |
| `widget_discreet_today` / `_coming_up` / `_due` | %1$s today · %1$s coming up · %1$s due | %1$s مورد امروز، %1$s مورد در پیش، %1$s نفر در موعد ارتباط |

The widget's name in the launcher's list is `nav_tab_upcoming`. Discreet counts join with `separator`.
