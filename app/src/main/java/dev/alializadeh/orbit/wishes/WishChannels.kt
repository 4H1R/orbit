package dev.alializadeh.orbit.wishes

import android.content.Intent
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.contact.ContactAction
import dev.alializadeh.orbit.person.contact.ContactActionKind
import dev.alializadeh.orbit.person.contact.ContactActions
import dev.alializadeh.orbit.person.contact.ContactType
import java.net.URLEncoder

/** One way Wishes can be sent: through one of the Person's Contact methods, or any app (the share sheet). */
sealed interface WishChannel {
    /** Stable across recompositions and process death: the Contact method's id, or [OTHER_APP_KEY]. */
    val key: String

    /** Through [method], whose action is [kind] (SMS for a Phone, the messenger for the rest). */
    data class Contact(val method: ContactMethod, val kind: ContactActionKind) : WishChannel {
        override val key: String get() = "${method.id}"
    }

    /** The system share sheet, for any app. Always offered, last. */
    data object OtherApp : WishChannel {
        override val key: String get() = OTHER_APP_KEY
    }

    companion object {
        const val OTHER_APP_KEY = "other"
    }
}

/**
 * What Send does: start [intentAction] on [uri] (or, for the share sheet, a chooser over
 * `ACTION_SEND` with [sendText]). [smsBody] goes into the SMS app's text box. When [copyFirst],
 * the app can't take a text: the message is copied for the user to paste first.
 */
data class WishLaunch(
    val intentAction: String,
    val uri: String? = null,
    val smsBody: String? = null,
    val sendText: String? = null,
    val copyFirst: Boolean = false,
)

/**
 * Which of a Person's Contact methods can carry Wishes, and the intent that sends them (PRD v1.1).
 * The text is filled in for SMS, WhatsApp numbers, Telegram usernames and Email; the other
 * messengers only open the chat, so the message is copied first. Address and Link can't carry a
 * message. Pure, so the URIs are tested on the JVM.
 */
object WishChannels {

    /** Every channel for [methods], in their order, then [WishChannel.OtherApp]. */
    fun of(methods: List<ContactMethod>): List<WishChannel> =
        methods.sortedBy(ContactMethod::position).mapNotNull(::channelOf) + WishChannel.OtherApp

    /** The channel Send with starts on: the primary Contact method if it can carry a message, else the first that can. */
    fun default(channels: List<WishChannel>): WishChannel =
        channels.firstOrNull { it is WishChannel.Contact && it.method.isPrimary }
            ?: channels.firstOrNull { it is WishChannel.Contact }
            ?: WishChannel.OtherApp

    /** Whether [channel] needs the message copied, because its app can't take a text. */
    fun copiesFirst(channel: WishChannel): Boolean = launch(channel, "").copyFirst

    /** How [text] is sent through [channel]. */
    fun launch(channel: WishChannel, text: String): WishLaunch = when (channel) {
        WishChannel.OtherApp -> WishLaunch(Intent.ACTION_SEND, sendText = text)
        is WishChannel.Contact -> contactLaunch(channel.method, text)
    }

    private fun channelOf(method: ContactMethod): WishChannel? {
        if (method.type == ContactType.Address || method.type == ContactType.Link) return null
        val kind = when (method.type) {
            // A Phone's message action is SMS, not Call.
            ContactType.Phone -> ContactActionKind.Sms
            else -> ContactActions.of(method).firstOrNull()?.kind ?: return null
        }
        return WishChannel.Contact(method, kind)
    }

    private fun contactLaunch(method: ContactMethod, text: String): WishLaunch {
        val v = method.value.trim()
        val opens = ContactActions.of(method)
        return when (method.type) {
            ContactType.Phone -> {
                val sms = opens.first { it.kind == ContactActionKind.Sms }
                WishLaunch(sms.intentAction, sms.uri, smsBody = text)
            }
            ContactType.WhatsApp -> if (isLink(v)) {
                copyAndOpen(opens)
            } else {
                WishLaunch(Intent.ACTION_VIEW, "https://wa.me/${international(v)}?text=${encode(text)}")
            }
            ContactType.Telegram -> if (isLink(v) || isPhoneNumber(v)) {
                copyAndOpen(opens)
            } else {
                WishLaunch(Intent.ACTION_VIEW, "https://t.me/${v.removePrefix("@").trim()}?text=${encode(text)}")
            }
            ContactType.Email -> WishLaunch(Intent.ACTION_SENDTO, "mailto:$v?body=${encode(text)}")
            else -> copyAndOpen(opens)
        }
    }

    private fun copyAndOpen(opens: List<ContactAction>): WishLaunch {
        val open = opens.first()
        return WishLaunch(open.intentAction, open.uri, copyFirst = true)
    }

    // The same reading of a value as ContactActions: a link has a scheme or a slash.
    private fun isLink(value: String): Boolean = SCHEME.containsMatchIn(value) || '/' in value

    private fun isPhoneNumber(value: String): Boolean =
        value.all { it.isDigit() || it in PHONE_PUNCTUATION } && value.count(Char::isDigit) >= MIN_PHONE_DIGITS

    private fun international(value: String): String = value.filter(Char::isDigit).removePrefix("00")

    /** Percent-encoded for a URI query: spaces as `%20` (`+` reads as a plus in `mailto:`), UTF-8 for Persian. */
    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private const val PHONE_PUNCTUATION = "+-() ."
    private const val MIN_PHONE_DIGITS = 5
}
