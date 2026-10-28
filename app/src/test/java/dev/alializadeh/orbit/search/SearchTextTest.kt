package dev.alializadeh.orbit.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every normalisation rule of ticket 12, on the query and the text alike. */
class SearchTextTest {

    private fun n(text: String) = SearchText.normalise(text)

    private companion object {
        val ZWNJ = Char(0x200C)
        val FSI = Char(0x2068)
        val PDI = Char(0x2069)
    }

    @Test
    fun arabicYehAndKafAreThePersianLetters() {
        assertEquals(n("علی"), n("علي"))
        assertEquals(n("کریم"), n("كريم"))
        // Alef maksura reads as ی too.
        assertEquals(n("مصطفی"), n("مصطفى"))
    }

    @Test
    fun alefFormsAreAlef() {
        assertEquals("احمد", n("أحمد"))
        assertEquals("اسماعیل", n("إسماعیل"))
        assertEquals("ارش", n("آرش"))
    }

    @Test
    fun tehMarbutaIsHeh() {
        assertEquals(n("فاطمه"), n("فاطمة"))
    }

    @Test
    fun harakatTatweelAndZwnjAreRemoved() {
        assertEquals("محمد", n("مُحَمَّد"))
        assertEquals("سلام", n("ســـلام"))
        assertEquals(n("میروم"), n("می${ZWNJ}روم"))
        assertEquals("میروم", n("می${ZWNJ}روم"))
    }

    @Test
    fun hamzaOnYehAndHehIsDropped() {
        assertEquals(n("خانه"), n("خانۀ"))
        assertEquals(n("پاییز"), n("پائیز"))
    }

    @Test
    fun latinIgnoresCaseAndAccents() {
        assertEquals("elodie", n("Élodie"))
        assertEquals("francois muller", n("FRANÇOIS Müller"))
        assertEquals("istanbul", n("İstanbul"))
    }

    @Test
    fun persianAndArabicIndicDigitsAreAscii() {
        assertEquals("0912 345 6789", n("۰۹۱۲ ۳۴۵ ۶۷۸۹"))
        assertEquals("0912", n("٠٩١٢"))
    }

    @Test
    fun bidiMarksAreRemoved() {
        assertEquals("sara", n("${FSI}Sara$PDI"))
    }

    @Test
    fun queryWordsAreNormalisedAndSplitOnAnySpace() {
        assertEquals(listOf("ali", "gym"), SearchText.words("  Ali  GYM "))
        assertEquals(listOf("ali"), SearchText.words("ali ALI"))
        assertTrue(SearchText.words(" $ZWNJ ").isEmpty())
    }

    @Test
    fun aPhoneQueryIgnoresPunctuationAndTheLeadingPlusOrZeros() {
        assertEquals("912345", SearchText.phoneDigits("0912 345"))
        assertEquals("98912", SearchText.phoneDigits("+98 912"))
        assertEquals("98912", SearchText.phoneDigits("0098-912"))
        assertEquals("21555", SearchText.phoneDigits("(021) 555"))
        assertEquals("912345", SearchText.phoneDigits("۰۹۱۲ ۳۴۵"))
    }

    @Test
    fun aQueryWithLettersIsNotAPhoneQuery() {
        assertNull(SearchText.phoneDigits("room 12"))
        assertNull(SearchText.phoneDigits("sara"))
        assertNull(SearchText.phoneDigits("- ( )"))
        // Nothing left once the zeros go: no phone match (plain matching still applies).
        assertNull(SearchText.phoneDigits("00"))
    }

    @Test
    fun rangesMapBackToTheOriginalText() {
        val mapped = SearchText.map("می${ZWNJ}روم به Élodie")
        val at = mapped.text.indexOf("روم")
        assertEquals(3..5, mapped.originalRange(at, at + 3))
        val e = mapped.text.indexOf("elo")
        assertEquals("Élo", mapped.original.substring(mapped.originalRange(e, e + 3)))
    }

    @Test
    fun wordStartsAreAfterNonLetters() {
        val mapped = SearchText.map("Sara-Jane ali")
        assertTrue(mapped.startsWord(0))
        assertTrue(mapped.startsWord(mapped.text.indexOf("jane")))
        assertTrue(mapped.startsWord(mapped.text.indexOf("ali")))
        assertFalse(mapped.startsWord(mapped.text.indexOf("ara")))
    }
}
