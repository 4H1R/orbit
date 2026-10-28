package dev.alializadeh.orbit.wishes

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.interaction.InteractionDraft
import dev.alializadeh.orbit.interaction.InteractionRepository
import dev.alializadeh.orbit.interaction.InteractionType
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.occasion.OccasionRepository
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.PersonRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The open Wishes sheet: to whom, for what, and the choices made in it. The text is [WishesViewModel.message]. */
data class WishesSheetState(
    val personId: Long,
    val names: PersonNames,
    val kind: WishKind,
    /** Every way to send, [WishChannel.OtherApp] last. */
    val channels: List<WishChannel>,
    val channelKey: String,
    val language: OrbitLanguage,
    val logAsMessage: Boolean = true,
) {
    val channel: WishChannel get() = channels.firstOrNull { it.key == channelKey } ?: WishChannel.OtherApp
}

/**
 * The Wishes sheet (PRD v1.1), for the whole app: Upcoming, Person detail, a Reminder's Send wishes
 * and later Occasions open it, and `WishesHost` in the shell draws it. Activity-scoped, so a message
 * being written survives App lock and switching tabs.
 */
@HiltViewModel
class WishesViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val interactionRepository: InteractionRepository,
    private val occasionRepository: OccasionRepository,
    private val texts: WishTexts,
    private val clock: Clock,
) : ViewModel() {

    private val sheet = MutableStateFlow<WishesSheetState?>(null)
    val state: StateFlow<WishesSheetState?> = sheet.asStateFlow()

    /** The message being written. */
    val message = TextFieldState()

    private var opening: Job? = null

    /**
     * Opens Wishes to [personId] for their Important date [dateId], in the language of their Name
     * ([uiLanguage] when it has no letters). Nothing opens if either is gone.
     */
    fun openForDate(personId: Long, dateId: Long, uiLanguage: OrbitLanguage) {
        opening?.cancel()
        opening = viewModelScope.launch {
            val profile = personRepository.getProfile(personId) ?: return@launch
            val date = profile.importantDates.firstOrNull { it.id == dateId } ?: return@launch
            open(profile.names, profile.contactMethods, personId, WishKind.Date(date.type), uiLanguage)
        }
    }

    /** Opens Wishes to [personId] on the Occasion [occasionId]. Nothing opens if either is gone. */
    fun openForOccasion(personId: Long, occasionId: Long, uiLanguage: OrbitLanguage) {
        opening?.cancel()
        opening = viewModelScope.launch {
            val profile = personRepository.getProfile(personId) ?: return@launch
            val occasion = occasionRepository.get(occasionId) ?: return@launch
            open(profile.names, profile.contactMethods, personId, WishKind.Occasion(occasion.occasion.builtIn), uiLanguage)
        }
    }

    private fun open(names: PersonNames, methods: List<ContactMethod>, personId: Long, kind: WishKind, uiLanguage: OrbitLanguage) {
        val channels = WishChannels.of(methods)
        val language = names.messageLanguage(uiLanguage)
        message.setTextAndPlaceCursorAtEnd(texts.defaultText(kind, language, names.nameForMessage(language)))
        sheet.value = WishesSheetState(
            personId = personId,
            names = names,
            kind = kind,
            channels = channels,
            channelKey = WishChannels.default(channels).key,
            language = language,
        )
    }

    /** Writes in [language] instead: the message goes back to that language's default text. */
    fun setLanguage(language: OrbitLanguage) {
        val current = sheet.value ?: return
        if (current.language == language) return
        message.setTextAndPlaceCursorAtEnd(texts.defaultText(current.kind, language, current.names.nameForMessage(language)))
        sheet.value = current.copy(language = language)
    }

    fun setChannel(key: String) {
        sheet.update { it?.copy(channelKey = key) }
    }

    fun setLogAsMessage(log: Boolean) {
        sheet.update { it?.copy(logAsMessage = log) }
    }

    /** What Send starts, or null when there's nothing to send. */
    fun launch(): WishLaunch? {
        val current = sheet.value ?: return null
        val text = message.text.toString().trim()
        if (text.isEmpty()) return null
        return WishChannels.launch(current.channel, text)
    }

    /** The messaging app opened: log the message if asked, and close. */
    fun sent() {
        val current = sheet.value ?: return
        if (current.logAsMessage) {
            val draft = InteractionDraft(type = InteractionType.Message, date = LocalDate.now(clock), personIds = listOf(current.personId))
            viewModelScope.launch { interactionRepository.save(draft) }
        }
        dismiss()
    }

    /** The sheet has gone. */
    fun dismiss() {
        opening?.cancel()
        sheet.value = null
    }
}
