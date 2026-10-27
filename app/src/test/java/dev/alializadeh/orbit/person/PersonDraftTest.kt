package dev.alializadeh.orbit.person

import dev.alializadeh.orbit.person.contact.ContactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PersonDraftTest {

    private val base = PersonDraft(name = "Sara", ringId = 1)

    @Test
    fun normalisingTrimsAndDropsBlanks() {
        val draft = base.copy(
            name = "  Sara ",
            nickname = "   ",
            howWeMet = " At work ",
            tags = listOf(" Gym", "gym", ""),
            likes = listOf(" Jazz ", "  "),
        ).normalised()

        assertEquals("Sara", draft.name)
        assertEquals(null, draft.nickname)
        assertEquals("At work", draft.howWeMet)
        assertEquals(listOf("Gym"), draft.tags)
        assertEquals(listOf("Jazz"), draft.likes)
    }

    @Test
    fun phoneNumbersGetAsciiDigitsAndOneIsPrimary() {
        val methods = base.copy(
            contactMethods = listOf(
                ContactMethodDraft(ContactType.Phone, "۰۹۱۲-۱۲۳"),
                ContactMethodDraft(ContactType.Signal, "٠٩١٢"),
                ContactMethodDraft(ContactType.Telegram, "+۹۸۹۱۲"),
                ContactMethodDraft(ContactType.Email, " sara@example.com "),
                ContactMethodDraft(ContactType.Address, "پلاک ۱۲"),
            ),
        ).normalised().contactMethods

        assertEquals(
            listOf("0912-123", "0912", "+98912", "sara@example.com", "پلاک ۱۲"),
            methods.map(ContactMethodDraft::value),
        )
        assertEquals(listOf(true, false, false, false, false), methods.map(ContactMethodDraft::isPrimary))
    }

    @Test
    fun whitespaceOnlyEditsAreNoChange() {
        assertEquals(base.normalised(), base.copy(name = "Sara ", notes = " ").normalised())
        assertNotEquals(base.normalised(), base.copy(notes = "x").normalised())
    }
}
