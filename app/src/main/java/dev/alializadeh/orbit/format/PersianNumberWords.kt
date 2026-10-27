package dev.alializadeh.orbit.format

/**
 * Persian ordinals in words, from 1 to 999: «اولین», «سومین», «بیست‌ویکمین», «سی‌امین»…
 * (copy deck: Persian ordinals are always spelled out). Parts are joined with ZWNJ + «و», as the
 * copy deck writes «بیست‌ویکمین».
 */
internal object PersianNumberWords {

    const val MAX = 999

    private val ONES = listOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    private val TEENS = listOf(
        "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده",
    )
    private val TENS = listOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val HUNDREDS = listOf(
        "", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد",
    )
    private const val ZWNJ = "\u200C"
    private const val JOINER = "${ZWNJ}و"

    /** The words of [n] (1..999) as parts: 121 → [صد, بیست, یک]. */
    private fun cardinalParts(n: Int): List<String> {
        require(n in 1..MAX) { "$n is out of 1..$MAX" }
        val parts = mutableListOf<String>()
        if (n >= 100) parts += HUNDREDS[n / 100]
        val belowHundred = n % 100
        when {
            belowHundred >= 20 -> {
                parts += TENS[belowHundred / 10]
                if (belowHundred % 10 != 0) parts += ONES[belowHundred % 10]
            }
            belowHundred >= 10 -> parts += TEENS[belowHundred - 10]
            belowHundred > 0 -> parts += ONES[belowHundred]
        }
        return parts
    }

    /** [n] in words: 21 → «بیست‌ویک». */
    fun cardinal(n: Int): String = cardinalParts(n).joinToString(JOINER)

    /** The «…مین» ordinal of [n]: 1 «اولین», 3 «سومین», 21 «بیست‌ویکمین», 30 «سی‌امین». */
    fun ordinal(n: Int): String {
        if (n == 1) return "اولین"
        val parts = cardinalParts(n)
        val last = when (val word = parts.last()) {
            "یک" -> "یکم"
            "سه" -> "سوم"
            else -> if (word.endsWith('ی')) "$word${ZWNJ}ام" else "${word}م"
        }
        return (parts.dropLast(1) + last).joinToString(JOINER) + "ین"
    }
}
