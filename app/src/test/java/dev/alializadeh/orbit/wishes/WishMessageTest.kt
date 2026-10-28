package dev.alializadeh.orbit.wishes

import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.PersonNames
import org.junit.Assert.assertEquals
import org.junit.Test

class WishMessageTest {

    @Test
    fun theMessageIsInTheLanguageOfTheNamesScript() {
        assertEquals(OrbitLanguage.English, PersonNames("Sara Ahmadi").messageLanguage(OrbitLanguage.Persian))
        assertEquals(OrbitLanguage.Persian, PersonNames("سارا احمدی", otherScriptName = "Sara").messageLanguage(OrbitLanguage.English))
        // No letters to tell by: the UI's language.
        assertEquals(OrbitLanguage.Persian, PersonNames("۰۹۱۲").messageLanguage(OrbitLanguage.Persian))
    }

    @Test
    fun theNicknameInTheMessagesScriptElseTheFirstWordOfTheNameInIt() {
        assertEquals("Sari", PersonNames("Sara Ahmadi", nickname = "Sari").nameForMessage(OrbitLanguage.English))
        assertEquals("Sara", PersonNames("Sara Ahmadi").nameForMessage(OrbitLanguage.English))
        // Written to in Persian: the Persian-script name, whichever field holds it.
        assertEquals("سارا", PersonNames("Sara Ahmadi", otherScriptName = "سارا احمدی", nickname = "Sari").nameForMessage(OrbitLanguage.Persian))
        assertEquals("سارا", PersonNames("سارا  احمدی", otherScriptName = "Sara").nameForMessage(OrbitLanguage.Persian))
        assertEquals("ساری", PersonNames("سارا احمدی", nickname = " ساری ").nameForMessage(OrbitLanguage.Persian))
        // Nothing in that script: the Name's first word as written.
        assertEquals("Sara", PersonNames("Sara Ahmadi").nameForMessage(OrbitLanguage.Persian))
    }
}
