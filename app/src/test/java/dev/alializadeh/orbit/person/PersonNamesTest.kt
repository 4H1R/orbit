package dev.alializadeh.orbit.person

import dev.alializadeh.orbit.language.OrbitLanguage.English
import dev.alializadeh.orbit.language.OrbitLanguage.Persian
import dev.alializadeh.orbit.person.ui.AvatarColours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonNamesTest {

    @Test
    fun scriptIsDetectedPerName() {
        assertEquals(NameScript.Latin, NameScript.detect("Sara Ahmadi"))
        assertEquals(NameScript.Persian, NameScript.detect("سارا احمدی"))
        assertEquals(NameScript.Persian, NameScript.detect("سارا احمدی (Sara)"))
        assertEquals(NameScript.Latin, NameScript.detect("Dr. Sara احمد"))
        // No letters at all: Latin.
        assertEquals(NameScript.Latin, NameScript.detect("123"))
        // A tie goes to the first letter.
        assertEquals(NameScript.Persian, NameScript.detect("سب ab"))
        // Other scripts count as "not Persian".
        assertEquals(NameScript.Latin, NameScript.detect("Анна"))
    }

    @Test
    fun theNameInTheUiScriptIsDisplayedAndTheOtherIsTheSubtitle() {
        val names = PersonNames(name = "Sara Ahmadi", otherScriptName = "سارا احمدی", nickname = "Sari")

        assertEquals("Sara Ahmadi", names.displayName(English))
        assertEquals(listOf("سارا احمدی", "Sari"), names.subtitleParts(English))
        assertEquals("سارا احمدی", names.displayName(Persian))
        assertEquals(listOf("Sara Ahmadi", "Sari"), names.subtitleParts(Persian))
    }

    @Test
    fun thePersianNameCanBeTheMainOne() {
        val names = PersonNames(name = "بابک", otherScriptName = "Babak")

        assertEquals("Babak", names.displayName(English))
        assertEquals(listOf("بابک"), names.subtitleParts(English))
        assertEquals("بابک", names.displayName(Persian))
        assertEquals(listOf("Babak"), names.subtitleParts(Persian))
    }

    @Test
    fun withoutANameInTheUiScriptTheNameIsShown() {
        assertEquals("Tom", PersonNames("Tom").displayName(Persian))
        assertEquals("Tom", PersonNames("Tom", otherScriptName = "Thomas").displayName(Persian))
        assertEquals(listOf("Thomas"), PersonNames("Tom", otherScriptName = "Thomas").subtitleParts(Persian))
        assertEquals(emptyList<String>(), PersonNames("Tom", otherScriptName = " ").subtitleParts(English))
    }

    @Test
    fun avatarsTakeTheFirstLetterAndAStableColour() {
        assertEquals("S", AvatarColours.initial(" sara"))
        assertEquals("س", AvatarColours.initial("سارا"))
        assertEquals("", AvatarColours.initial(""))
        assertEquals(AvatarColours.hueIndex("Sara"), AvatarColours.hueIndex(" sara "))
        val hues = listOf("Sara", "Reza", "Mina", "Ali", "Babak", "Tom", "Leila", "Omid").map(AvatarColours::hueIndex)
        assertTrue("different names spread over the hues: $hues", hues.toSet().size > 3)
    }
}
