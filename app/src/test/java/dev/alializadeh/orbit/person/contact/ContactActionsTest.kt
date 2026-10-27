package dev.alializadeh.orbit.person.contact

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactActionsTest {

    private fun uris(type: ContactType, value: String) = ContactActions.of(type, value).map { it.intentAction to it.uri }

    private fun view(uri: String) = listOf(Intent.ACTION_VIEW to uri)

    @Test
    fun aPhoneDialsAndTexts() {
        assertEquals(
            listOf(Intent.ACTION_DIAL to "tel:+491512345", Intent.ACTION_SENDTO to "smsto:+491512345"),
            uris(ContactType.Phone, "+49 (151) 23-45"),
        )
        assertEquals(
            listOf(ContactActionKind.Call, ContactActionKind.Sms),
            ContactActions.of(ContactType.Phone, "1").map(ContactAction::kind),
        )
    }

    @Test
    fun messengersOpenTheirLinks() {
        assertEquals(view("https://wa.me/491512345678"), uris(ContactType.WhatsApp, "+49 151 2345678"))
        assertEquals(view("https://wa.me/989121234567"), uris(ContactType.WhatsApp, "0098 912 123 4567"))
        assertEquals(view("https://t.me/sara_a"), uris(ContactType.Telegram, "@sara_a"))
        assertEquals(view("https://t.me/+989121234567"), uris(ContactType.Telegram, "+98 912 123 4567"))
        assertEquals(view("https://signal.me/#p/+491512345678"), uris(ContactType.Signal, "+49 151 2345678"))
        assertEquals(emptyList<Any>(), uris(ContactType.Signal, "sara.42"))
    }

    @Test
    fun socialHandlesAndLinks() {
        assertEquals(view("https://www.instagram.com/sara.draws/"), uris(ContactType.Instagram, "@sara.draws"))
        assertEquals(view("https://x.com/sara"), uris(ContactType.X, "sara"))
        assertEquals(view("https://www.linkedin.com/in/sara-ahmadi"), uris(ContactType.LinkedIn, "sara-ahmadi"))
        assertEquals(view("https://linkedin.com/in/sara"), uris(ContactType.LinkedIn, "linkedin.com/in/sara"))
        assertEquals(view("https://x.com/sara"), uris(ContactType.X, "https://x.com/sara"))
        assertEquals(view("https://sara.example"), uris(ContactType.Link, "sara.example"))
        assertEquals(view("http://sara.example/a"), uris(ContactType.Link, "http://sara.example/a"))
    }

    @Test
    fun emailAndAddress() {
        assertEquals(listOf(Intent.ACTION_SENDTO to "mailto:sara@example.com"), uris(ContactType.Email, " sara@example.com "))
        assertEquals(view("geo:0,0?q=10%20Downing%20St%2C%20London"), uris(ContactType.Address, "10 Downing St, London"))
        assertEquals(view("geo:0,0?q=%D8%AA%D9%87%D8%B1%D8%A7%D9%86"), uris(ContactType.Address, "تهران"))
    }

    @Test
    fun aBlankValueHasNoAction() {
        assertEquals(emptyList<Any>(), uris(ContactType.Phone, "  "))
    }
}
