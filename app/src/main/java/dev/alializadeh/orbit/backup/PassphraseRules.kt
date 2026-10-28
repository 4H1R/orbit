package dev.alializadeh.orbit.backup

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import dev.alializadeh.orbit.format.Digits

/**
 * The Backup passphrase rules (ADR 0004): at least [MIN_LENGTH] printable ASCII characters
 * (letters, digits, symbols, spaces), so a Backup opens with `age -d` on any computer. Persian and
 * Arabic digits are converted to ASCII as they're typed; any other non-ASCII character is refused
 * with the copy deck's rule. The same rules apply to a passphrase typed for a Restore.
 */
object PassphraseRules {

    const val MIN_LENGTH = 12

    enum class Problem {
        /** Fewer than [MIN_LENGTH] characters. */
        TooShort,

        /** Something other than printable ASCII (after digit conversion). */
        NotAscii,
    }

    /** What the user typed, as it's stored and used: Persian/Arabic digits become ASCII. */
    fun normalise(input: CharSequence): String = Digits.toAscii(input.toString())

    /** Whether [passphrase] (already [normalise]d) breaks a rule; NotAscii wins over TooShort. */
    fun problem(passphrase: String): Problem? = when {
        passphrase.any { it !in PRINTABLE_ASCII } -> Problem.NotAscii
        passphrase.length < MIN_LENGTH -> Problem.TooShort
        else -> null
    }

    fun isAscii(text: CharSequence): Boolean = text.all { it in PRINTABLE_ASCII }

    private val PRINTABLE_ASCII = ' '..'~'
}

/** Converts Persian and Arabic digits to ASCII as they're typed into a passphrase field. */
object PassphraseInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        for (index in text.indices) {
            val converted = Digits.toAscii(text[index].toString())
            if (converted[0] != text[index]) replace(index, index + 1, converted)
        }
    }
}
