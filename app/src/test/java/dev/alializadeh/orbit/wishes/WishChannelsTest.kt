package dev.alializadeh.orbit.wishes

import android.content.Intent
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.contact.ContactActionKind
import dev.alializadeh.orbit.person.contact.ContactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WishChannelsTest {

    private var nextId = 1L

    private fun method(type: ContactType, value: String, primary: Boolean = false, position: Int = 0) =
        ContactMethod(id = nextId++, personId = 1, type = type, value = value, isPrimary = primary, position = position)

    private fun launch(type: ContactType, value: String, text: String = "Happy birthday, Sara.") =
        WishChannels.launch(WishChannels.of(listOf(method(type, value))).first(), text)

    @Test
    fun everyMessagingMethodInOrderThenOtherAppButNoAddressOrLink() {
        val phone = method(ContactType.Phone, "+49 151 2345", position = 0)
        val address = method(ContactType.Address, "10 Downing St", position = 1)
        val link = method(ContactType.Link, "sara.example", position = 2)
        val whatsApp = method(ContactType.WhatsApp, "+49 151 2345", position = 3)
        val signalName = method(ContactType.Signal, "sara.42", position = 4)
        val channels = WishChannels.of(listOf(whatsApp, link, phone, signalName, address))
        assertEquals(
            listOf(WishChannel.Contact(phone, ContactActionKind.Sms), WishChannel.Contact(whatsApp, ContactActionKind.WhatsApp), WishChannel.OtherApp),
            channels,
        )
    }

    @Test
    fun startsOnThePrimaryIfItCanCarryAMessageElseTheFirstElseOtherApp() {
        val email = method(ContactType.Email, "sara@example.com", position = 0)
        val whatsApp = method(ContactType.WhatsApp, "+49 151 2345", primary = true, position = 1)
        assertEquals(whatsApp, (WishChannels.default(WishChannels.of(listOf(email, whatsApp))) as WishChannel.Contact).method)

        val address = method(ContactType.Address, "Tehran", primary = true, position = 0)
        val telegram = method(ContactType.Telegram, "@sara", position = 1)
        assertEquals(telegram, (WishChannels.default(WishChannels.of(listOf(address, telegram))) as WishChannel.Contact).method)

        assertEquals(WishChannel.OtherApp, WishChannels.default(WishChannels.of(listOf(address))))
        assertEquals(WishChannel.OtherApp, WishChannels.default(WishChannels.of(emptyList())))
    }

    @Test
    fun smsWhatsAppTelegramUsernamesAndEmailTakeTheText() {
        assertEquals(
            WishLaunch(Intent.ACTION_SENDTO, "smsto:+491512345", smsBody = "Happy birthday, Sara."),
            launch(ContactType.Phone, "+49 (151) 23-45"),
        )
        assertEquals(
            WishLaunch(Intent.ACTION_VIEW, "https://wa.me/491512345678?text=Happy%20birthday%2C%20Sara."),
            launch(ContactType.WhatsApp, "0049 151 2345678"),
        )
        assertEquals(
            WishLaunch(Intent.ACTION_VIEW, "https://t.me/sara_a?text=Happy%20birthday%2C%20Sara."),
            launch(ContactType.Telegram, "@sara_a"),
        )
        assertEquals(
            WishLaunch(Intent.ACTION_SENDTO, "mailto:sara@example.com?body=Happy%20birthday%2C%20Sara."),
            launch(ContactType.Email, " sara@example.com "),
        )
    }

    @Test
    fun persianTextIsEncodedAsUtf8() {
        assertEquals(
            "https://wa.me/989121234567?text=%D8%B3%D8%A7%D8%B1%D8%A7%20%D8%B9%D8%B2%DB%8C%D8%B2",
            launch(ContactType.WhatsApp, "+98 912 123 4567", text = "سارا عزیز").uri,
        )
    }

    @Test
    fun appsThatCantTakeATextOpenTheChatAndCopyFirst() {
        assertEquals(WishLaunch(Intent.ACTION_VIEW, "https://t.me/+989121234567", copyFirst = true), launch(ContactType.Telegram, "+98 912 123 4567"))
        assertEquals(WishLaunch(Intent.ACTION_VIEW, "https://signal.me/#p/+491512345678", copyFirst = true), launch(ContactType.Signal, "+49 151 2345678"))
        assertEquals(WishLaunch(Intent.ACTION_VIEW, "https://www.instagram.com/sara.draws/", copyFirst = true), launch(ContactType.Instagram, "@sara.draws"))
        assertEquals(WishLaunch(Intent.ACTION_VIEW, "https://x.com/sara", copyFirst = true), launch(ContactType.X, "sara"))
        // A WhatsApp value that's already a link opens as it is.
        assertEquals(WishLaunch(Intent.ACTION_VIEW, "https://wa.me/4915", copyFirst = true), launch(ContactType.WhatsApp, "wa.me/4915"))
        assertTrue(WishChannels.copiesFirst(WishChannels.of(listOf(method(ContactType.LinkedIn, "sara"))).first()))
        assertFalse(WishChannels.copiesFirst(WishChannel.OtherApp))
    }

    @Test
    fun otherAppIsTheShareSheet() {
        assertEquals(WishLaunch(Intent.ACTION_SEND, sendText = "Hi"), WishChannels.launch(WishChannel.OtherApp, "Hi"))
    }
}
