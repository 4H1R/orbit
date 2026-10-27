package dev.alializadeh.orbit.person.contact

import androidx.annotation.StringRes
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.format.Digits

/**
 * The fixed Contact method types (copy deck: Contact method types), in the order the type menu
 * lists them. Stored by name: never rename a constant.
 */
enum class ContactType(
    @StringRes val nameRes: Int,
    @StringRes val valueLabelRes: Int,
    val input: ContactInput,
) {
    Phone(R.string.contact_type_phone, R.string.contact_value_number, ContactInput.PhoneNumber),
    WhatsApp(R.string.contact_type_whatsapp, R.string.contact_value_number, ContactInput.PhoneNumber),
    Telegram(R.string.contact_type_telegram, R.string.contact_value_username_or_number, ContactInput.Handle),
    Signal(R.string.contact_type_signal, R.string.contact_value_number, ContactInput.PhoneNumber),
    Email(R.string.contact_type_email, R.string.contact_value_email, ContactInput.Email),
    Instagram(R.string.contact_type_instagram, R.string.contact_value_username, ContactInput.Handle),
    LinkedIn(R.string.contact_type_linkedin, R.string.contact_value_link, ContactInput.Link),
    X(R.string.contact_type_x, R.string.contact_value_username, ContactInput.Handle),
    Address(R.string.contact_type_address, R.string.contact_value_address, ContactInput.Text),
    Link(R.string.contact_type_link, R.string.contact_value_link, ContactInput.Link),
    ;

    /**
     * The value as stored: trimmed, and for the types that hold phone numbers, Persian and Arabic
     * digits as ASCII (an Address keeps its digits as typed).
     */
    fun normalise(value: String): String {
        val trimmed = value.trim()
        return if (holdsPhoneNumber) Digits.toAscii(trimmed) else trimmed
    }

    /** Types whose value may be a phone number (Telegram may hold a username or a number). */
    private val holdsPhoneNumber: Boolean
        get() = input == ContactInput.PhoneNumber || this == Telegram
}

/** What kind of text a Contact method's value is, which picks the keyboard. */
enum class ContactInput { PhoneNumber, Email, Handle, Link, Text }
