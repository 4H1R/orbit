package dev.alializadeh.orbit.wishes

import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.interaction.InteractionType
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.testing.FakeInteractionRepository
import dev.alializadeh.orbit.testing.FakePersonRepository
import dev.alializadeh.orbit.testing.MainDispatcherRule
import dev.alializadeh.orbit.testing.profileOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WishesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC)
    private val birthday = ImportantDate(id = 5, personId = 1, type = ImportantDateType.Birthday, date = RecurringDate(CalendarSystem.Gregorian, 10, 28), position = 0)
    private val whatsApp = ContactMethod(id = 9, personId = 1, type = ContactType.WhatsApp, value = "+49 151 2345", isPrimary = true, position = 0)
    private val sara = profileOf(1, "Sara Ahmadi", otherScriptName = "سارا احمدی")
        .copy(importantDates = listOf(birthday), contactMethods = listOf(whatsApp))
    private val people = FakePersonRepository(initial = listOf(sara))
    private val interactions = FakeInteractionRepository(people, clock)

    /** "kind|language|name", so the tests see what was asked for. */
    private val texts = WishTexts { kind, language, name -> "$kind|$language|$name" }

    private fun viewModel() = WishesViewModel(people, interactions, texts, clock)

    @Test
    fun opensInTheNamesLanguageOnThePrimaryWithTheDefaultText() = runTest {
        val viewModel = viewModel()
        viewModel.openForDate(personId = 1, dateId = 5, uiLanguage = OrbitLanguage.Persian)
        runCurrent()
        val state = viewModel.state.value!!
        assertEquals(OrbitLanguage.English, state.language)
        assertEquals(whatsApp.id.toString(), state.channelKey)
        assertTrue(state.logAsMessage)
        assertEquals("${WishKind.Date(ImportantDateType.Birthday)}|English|Sara", viewModel.message.text.toString())
    }

    @Test
    fun switchingLanguagePutsThatLanguagesTextBack() = runTest {
        val viewModel = viewModel()
        viewModel.openForDate(1, 5, OrbitLanguage.English)
        runCurrent()
        viewModel.setLanguage(OrbitLanguage.Persian)
        assertEquals(OrbitLanguage.Persian, viewModel.state.value!!.language)
        assertEquals("${WishKind.Date(ImportantDateType.Birthday)}|Persian|سارا", viewModel.message.text.toString())
    }

    @Test
    fun sendLaunchesTheChosenChannelLogsAMessageAndCloses() = runTest {
        val viewModel = viewModel()
        viewModel.openForDate(1, 5, OrbitLanguage.English)
        runCurrent()
        viewModel.message.edit { replace(0, length, " Happy birthday ") }
        viewModel.setChannel(WishChannel.OTHER_APP_KEY)
        assertEquals(WishLaunch(android.content.Intent.ACTION_SEND, sendText = "Happy birthday"), viewModel.launch())
        viewModel.sent()
        runCurrent()
        assertNull(viewModel.state.value)
        val (interaction, ids) = interactions.interactions.value.values.single()
        assertEquals(InteractionType.Message, interaction.type)
        assertEquals(LocalDate.of(2026, 10, 28), interaction.date)
        assertNull(interaction.note)
        assertEquals(listOf(1L), ids)
    }

    @Test
    fun withoutLogAsMessageNothingIsLoggedAndABlankMessageCantBeSent() = runTest {
        val viewModel = viewModel()
        viewModel.openForDate(1, 5, OrbitLanguage.English)
        runCurrent()
        viewModel.setLogAsMessage(false)
        viewModel.sent()
        runCurrent()
        assertTrue(interactions.interactions.value.isEmpty())

        viewModel.openForDate(1, 5, OrbitLanguage.English)
        runCurrent()
        viewModel.message.edit { replace(0, length, "   ") }
        assertNull(viewModel.launch())
    }

    @Test
    fun nothingOpensForAMissingPersonOrDate() = runTest {
        val viewModel = viewModel()
        viewModel.openForDate(personId = 2, dateId = 5, uiLanguage = OrbitLanguage.English)
        viewModel.openForDate(personId = 1, dateId = 6, uiLanguage = OrbitLanguage.English)
        runCurrent()
        assertNull(viewModel.state.value)
    }
}
