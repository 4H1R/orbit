package dev.alializadeh.orbit.person.contact

import android.content.Intent
import androidx.annotation.StringRes
import dev.alializadeh.orbit.R
import dev.alializadeh.orbit.person.ContactMethod
import java.net.URLEncoder

/** What a Contact method's action does, which names and draws its button. */
enum class ContactActionKind(@StringRes val labelRes: Int) {
    Call(R.string.contact_action_call),
    Sms(R.string.contact_action_sms),
    WhatsApp(R.string.contact_type_whatsapp),
    Telegram(R.string.contact_type_telegram),
    Signal(R.string.contact_type_signal),
    Email(R.string.contact_action_email),
    Instagram(R.string.contact_type_instagram),
    LinkedIn(R.string.contact_type_linkedin),
    X(R.string.contact_type_x),
    Map(R.string.contact_action_map),
    Open(R.string.contact_action_open),
}

/**
 * One action on a Contact method: an implicit intent ([intentAction] on [uri]) for another app.
 * No permission is involved: dialling uses `ACTION_DIAL`, which only fills in the number.
 */
data class ContactAction(val kind: ContactActionKind, val intentAction: String, val uri: String)

/**
 * The actions of each Contact method type (copy deck: Phone offers Call and SMS, Address opens
 * Maps, the rest open their app or the link). Pure, so the URIs are tested on the JVM.
 */
object ContactActions {

    fun of(method: ContactMethod): List<ContactAction> = of(method.type, method.value)

    fun of(type: ContactType, value: String): List<ContactAction> {
        val v = value.trim()
        if (v.isEmpty()) return emptyList()
        return when (type) {
            ContactType.Phone -> listOf(
                ContactAction(ContactActionKind.Call, Intent.ACTION_DIAL, "tel:${dialable(v)}"),
                ContactAction(ContactActionKind.Sms, Intent.ACTION_SENDTO, "smsto:${dialable(v)}"),
            )
            ContactType.WhatsApp -> view(ContactActionKind.WhatsApp, linkOr(v) { "https://wa.me/${international(v)}" })
            ContactType.Telegram -> view(
                ContactActionKind.Telegram,
                linkOr(v) { if (isPhoneNumber(v)) "https://t.me/+${international(v)}" else "https://t.me/${handle(v)}" },
            )
            ContactType.Signal -> if (isLink(v) || isPhoneNumber(v)) {
                view(ContactActionKind.Signal, linkOr(v) { "https://signal.me/#p/+${international(v)}" })
            } else {
                // A Signal username has no public link form; the value is still shown and copyable.
                emptyList()
            }
            ContactType.Email -> listOf(ContactAction(ContactActionKind.Email, Intent.ACTION_SENDTO, "mailto:$v"))
            ContactType.Instagram ->
                view(ContactActionKind.Instagram, linkOr(v) { "https://www.instagram.com/${handle(v)}/" })
            ContactType.LinkedIn ->
                view(ContactActionKind.LinkedIn, linkOr(v) { "https://www.linkedin.com/in/${handle(v)}" })
            ContactType.X -> view(ContactActionKind.X, linkOr(v) { "https://x.com/${handle(v)}" })
            ContactType.Address -> view(ContactActionKind.Map, "geo:0,0?q=${encodeQuery(v)}")
            ContactType.Link -> view(ContactActionKind.Open, withScheme(v))
        }
    }

    private fun view(kind: ContactActionKind, uri: String) = listOf(ContactAction(kind, Intent.ACTION_VIEW, uri))

    /** A value that's already a link ("https://…", "linkedin.com/in/sara") opens as one. */
    private inline fun linkOr(value: String, otherwise: () -> String): String =
        if (isLink(value)) withScheme(value) else otherwise()

    private fun isLink(value: String): Boolean = hasScheme(value) || '/' in value

    private fun hasScheme(value: String): Boolean = SCHEME.containsMatchIn(value)

    private fun withScheme(value: String): String = if (hasScheme(value)) value else "https://$value"

    /** Digits, `+`, and the separators people type in numbers, with at least a few digits. */
    private fun isPhoneNumber(value: String): Boolean =
        value.all { it.isDigit() || it in PHONE_PUNCTUATION } && value.count(Char::isDigit) >= MIN_PHONE_DIGITS

    /** The number for `tel:`: digits and a leading `+`, without spaces or punctuation. */
    private fun dialable(value: String): String =
        value.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    /** An international number as wa.me and t.me want it: digits only, without `+` or `00`. */
    private fun international(value: String): String = value.filter(Char::isDigit).removePrefix("00")

    private fun handle(value: String): String = value.removePrefix("@").trim()

    private fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private const val PHONE_PUNCTUATION = "+-() ."
    private const val MIN_PHONE_DIGITS = 5
}
