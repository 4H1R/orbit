package dev.alializadeh.orbit.format

/** Digit-script conversions. Displayed numbers go through [OrbitNumberFormatter] instead. */
object Digits {

    private const val PERSIAN_ZERO = '۰'
    private const val PERSIAN_NINE = '۹'
    private const val ARABIC_INDIC_ZERO = '٠'
    private const val ARABIC_INDIC_NINE = '٩'

    /**
     * Persian (۰–۹) and Arabic-Indic (٠–٩) digits in [text] as ASCII, everything else untouched:
     * for what the user types into phone numbers, and for search.
     */
    fun toAscii(text: String): String {
        if (text.none { it.isPersianDigit() || it.isArabicIndicDigit() }) return text
        return buildString(text.length) {
            for (c in text) {
                append(
                    when {
                        c.isPersianDigit() -> '0' + (c - PERSIAN_ZERO)
                        c.isArabicIndicDigit() -> '0' + (c - ARABIC_INDIC_ZERO)
                        else -> c
                    },
                )
            }
        }
    }

    /** ASCII digits in [text] as Persian digits (۰–۹). */
    fun toPersian(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') PERSIAN_ZERO + (c - '0') else c)
    }

    private fun Char.isPersianDigit() = this in PERSIAN_ZERO..PERSIAN_NINE

    private fun Char.isArabicIndicDigit() = this in ARABIC_INDIC_ZERO..ARABIC_INDIC_NINE
}
