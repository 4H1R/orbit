package dev.alializadeh.orbit.format

import dev.alializadeh.orbit.language.OrbitLanguage
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitNumberFormatterTest {

    private val english = OrbitNumberFormatter(OrbitLanguage.English, useLatinDigits = false)
    private val persian = OrbitNumberFormatter(OrbitLanguage.Persian, useLatinDigits = false)
    private val persianLatin = OrbitNumberFormatter(OrbitLanguage.Persian, useLatinDigits = true)

    @Test
    fun digitsFollowTheLanguageAndTheLatinDigitsSetting() {
        assertEquals("1405", english.format(1405))
        assertEquals("۱۴۰۵", persian.format(1405))
        assertEquals("1405", persianLatin.format(1405))
        assertEquals("۰", persian.format(0))
        assertEquals("-۷", persian.format(-7))
        assertEquals("۹۸۷۶۵۴۳۲۱۰", persian.format(9_876_543_210L))
    }

    @Test
    fun englishIgnoresTheLatinDigitsSetting() {
        assertFalse(OrbitNumberFormatter(OrbitLanguage.English, useLatinDigits = false).usesPersianDigits)
        assertTrue(persian.usesPersianDigits)
    }

    @Test
    fun englishOrdinals() {
        val expected = mapOf(
            1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 10 to "10th", 11 to "11th", 12 to "12th",
            13 to "13th", 21 to "21st", 22 to "22nd", 23 to "23rd", 101 to "101st", 111 to "111th",
            112 to "112th",
        )
        expected.forEach { (n, ordinal) -> assertEquals(ordinal, english.ordinal(n)) }
    }

    @Test
    fun persianOrdinalsAreSpelledOutWhateverTheDigitSetting() {
        val zwnj = "‌"
        val expected = mapOf(
            1 to "اولین",
            2 to "دومین",
            3 to "سومین",
            5 to "پنجمین",
            10 to "دهمین",
            11 to "یازدهمین",
            19 to "نوزدهمین",
            20 to "بیستمین",
            21 to "بیست${zwnj}ویکمین",
            23 to "بیست${zwnj}وسومین",
            30 to "سی${zwnj}امین",
            33 to "سی${zwnj}وسومین",
            40 to "چهلمین",
            99 to "نود${zwnj}ونهمین",
            100 to "صدمین",
            101 to "صد${zwnj}ویکمین",
            130 to "صد${zwnj}وسی${zwnj}امین",
            200 to "دویستمین",
            999 to "نهصد${zwnj}ونود${zwnj}ونهمین",
        )
        expected.forEach { (n, ordinal) ->
            assertEquals("$n", ordinal, persian.ordinal(n))
            assertEquals("$n", ordinal, persianLatin.ordinal(n))
        }
    }

    @Test
    fun theCopyDecksOrdinalsMatchByteForByte() {
        // Written with their ZWNJs, as the strings use them.
        assertEquals("بیست‌ویکمین", persian.ordinal(21))
        assertEquals("سی‌امین", persian.ordinal(30))
        assertEquals("سومین", persian.ordinal(3))
        assertEquals("پنجمین", persian.ordinal(5))
    }

    @Test
    fun persianCardinalWords() {
        assertEquals("یک", PersianNumberWords.cardinal(1))
        assertEquals("بیست‌ویک", PersianNumberWords.cardinal(21))
        assertEquals("سیصد‌وپانزده", PersianNumberWords.cardinal(315))
    }

    @Test
    fun digitNormalisation() {
        assertEquals("09121234567", Digits.toAscii("۰۹۱۲۱۲۳۴۵۶۷"))
        assertEquals("+98 912 123", Digits.toAscii("+۹۸ ٩١٢ 123"))
        assertEquals("abc", Digits.toAscii("abc"))
        assertEquals("سارا ۲", Digits.toPersian("سارا 2"))
    }

    @Test
    fun bidiIsolation() {
        assertEquals("⁨سارا⁩", bidiIsolate("سارا"))
        assertEquals("⁦+98 912⁩", bidiIsolateLtr("+98 912"))
    }

    @Test
    fun persianWeeksStartOnSaturday() {
        assertEquals(DayOfWeek.SATURDAY, OrbitLanguage.Persian.firstDayOfWeek)
        assertEquals(DayOfWeek.MONDAY, OrbitLanguage.English.firstDayOfWeek)
    }
}
