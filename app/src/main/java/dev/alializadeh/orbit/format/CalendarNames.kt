package dev.alializadeh.orbit.format

import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.language.OrbitLanguage
import java.time.DayOfWeek

/**
 * Month and weekday names per language and Calendar. Like digits, these are formatting data rather
 * than sentences, so they live here (keeping the date formatter pure and JVM-tested) instead of in
 * string resources; neither ICU nor `java.time` is used for them (ICU's Persian-calendar output
 * differs between Android versions).
 *
 * English Solar Hijri months use the transliterations Farvardin… Esfand; Persian Gregorian months
 * are ژانویه… دسامبر.
 */
internal object CalendarNames {

    private val ENGLISH_GREGORIAN_SHORT = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    private val ENGLISH_GREGORIAN_FULL = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val ENGLISH_SOLAR_HIJRI = listOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand",
    )
    private val PERSIAN_GREGORIAN = listOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر",
    )
    private val PERSIAN_SOLAR_HIJRI = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )
    private val ENGLISH_LUNAR_HIJRI = listOf(
        "Muharram", "Safar", "Rabi' al-awwal", "Rabi' al-thani", "Jumada al-ula", "Jumada al-akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah",
    )
    private val PERSIAN_LUNAR_HIJRI = listOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
        "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه",
    )

    // Monday first, as java.time.DayOfWeek numbers them.
    private val ENGLISH_WEEKDAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val PERSIAN_WEEKDAYS = listOf(
        "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه", "یک‌شنبه",
    )

    /**
     * The name of [month] (1–12). English Gregorian months are abbreviated ("Oct") unless [full];
     * the other names have no short form.
     */
    fun month(language: OrbitLanguage, calendar: CalendarSystem, month: Int, full: Boolean = false): String {
        val names = when (language) {
            OrbitLanguage.English -> when (calendar) {
                CalendarSystem.Gregorian -> if (full) ENGLISH_GREGORIAN_FULL else ENGLISH_GREGORIAN_SHORT
                CalendarSystem.SolarHijri -> ENGLISH_SOLAR_HIJRI
                CalendarSystem.LunarHijri -> ENGLISH_LUNAR_HIJRI
            }
            OrbitLanguage.Persian -> when (calendar) {
                CalendarSystem.Gregorian -> PERSIAN_GREGORIAN
                CalendarSystem.SolarHijri -> PERSIAN_SOLAR_HIJRI
                CalendarSystem.LunarHijri -> PERSIAN_LUNAR_HIJRI
            }
        }
        return names[month - 1]
    }

    /** "Tue" in English, «سه‌شنبه» in Persian (which has no short weekday names). */
    fun weekday(language: OrbitLanguage, dayOfWeek: DayOfWeek): String = when (language) {
        OrbitLanguage.English -> ENGLISH_WEEKDAYS_SHORT
        OrbitLanguage.Persian -> PERSIAN_WEEKDAYS
    }[dayOfWeek.value - 1]
}
