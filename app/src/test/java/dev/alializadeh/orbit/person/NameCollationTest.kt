package dev.alializadeh.orbit.person

import dev.alializadeh.orbit.language.OrbitLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Persian collation needs ICU's CLDR data (the JDK's collator has no `fa` rules), so this runs
 * under Robolectric, whose android-all carries Android's ICU.
 */
@RunWith(RobolectricTestRunner::class)
class NameCollationTest {

    @Test
    fun persianLettersSortInPersianAlphabetOrder() {
        val names = listOf("یاسمن", "گلناز", "تینا", "پریا", "بابک", "کاوه", "ژاله", "وحید", "هما", "چکاوک", "آرش", "احمد")

        val sorted = names.sortedWith(nameComparator(OrbitLanguage.Persian))

        assertEquals(
            listOf("آرش", "احمد", "بابک", "پریا", "تینا", "چکاوک", "ژاله", "کاوه", "گلناز", "وحید", "هما", "یاسمن"),
            sorted,
        )
    }

    @Test
    fun englishIgnoresCase() {
        val sorted = listOf("sara", "Babak", "ali", "Mina").sortedWith(nameComparator(OrbitLanguage.English))

        assertEquals(listOf("ali", "Babak", "Mina", "sara"), sorted)
    }

    @Test
    fun peopleSortByTheNameTheyAreShownBy() {
        val people = listOf(
            PersonNames("Sara", otherScriptName = "سارا"),
            PersonNames("بابک", otherScriptName = "Babak"),
            PersonNames("Tom"),
            PersonNames("Ali", otherScriptName = "علی"),
        )

        fun sorted(language: OrbitLanguage) =
            sortByDisplayName(people, language, names = { it }, tieBreak = { 0L }).map { it.displayName(language) }

        assertEquals(listOf("Ali", "Babak", "Sara", "Tom"), sorted(OrbitLanguage.English))
        // CLDR's Persian collation puts the Persian script before Latin.
        assertEquals(listOf("بابک", "سارا", "علی", "Tom"), sorted(OrbitLanguage.Persian))
    }
}
