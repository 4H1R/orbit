package dev.alializadeh.orbit.person.list

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.DefaultDispatcher
import dev.alializadeh.orbit.data.PreferencesRepository
import dev.alializadeh.orbit.due.DueState
import dev.alializadeh.orbit.person.DateSoon
import dev.alializadeh.orbit.person.PeopleSort
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingRepository
import dev.alializadeh.orbit.search.FilterFacts
import dev.alializadeh.orbit.search.PeopleFilterState
import dev.alializadeh.orbit.search.PeopleFilters
import dev.alializadeh.orbit.search.PeopleRequest
import dev.alializadeh.orbit.search.PeopleRequests
import dev.alializadeh.orbit.search.SearchHit
import dev.alializadeh.orbit.search.SearchIndex
import dev.alializadeh.orbit.upcoming.todayFlow
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row of the People list: who, their photo, where they stand with their Cadence, and the date badge. */
data class PersonRow(
    val summary: PersonSummary,
    val photo: File?,
    val due: DueState,
    /** An Important date today or in the next 30 days. */
    val dateSoon: DateSoon? = null,
    /** With a query: how they matched (rank, highlights, "why it matched"). */
    val hit: SearchHit? = null,
) {
    val filterFacts: FilterFacts
        get() = FilterFacts(summary.ringId, summary.tagIds, isDue = due.isDue, hasDateSoon = dateSoon != null)
}

sealed interface PeopleUiState {
    data object Loading : PeopleUiState

    /**
     * What the list shows, unsorted: the screen orders [people] (by [sort], or by rank with a query)
     * in the UI language's collation, which can change while this ViewModel lives (a language
     * switch recreates the activity, not the ViewModel).
     */
    data class Loaded(
        /** Everyone the filters (or the Archived view) and the query leave in. */
        val people: List<PersonRow>,
        val today: LocalDate = LocalDate.ofEpochDay(0),
        val sort: PeopleSort = PeopleSort.Name,
        /** A query is on: rows are ranked and say why they matched. */
        val searching: Boolean = false,
        val filters: PeopleFilters = PeopleFilters(),
        /** Only Archived People: "Archived · 7", no filter chips. */
        val archivedView: Boolean = false,
        /** Anyone who isn't Archived, whatever the filters and query. */
        val hasPeople: Boolean = people.isNotEmpty(),
        val archivedCount: Int = 0,
        /** With a query, outside the Archived view: the Archived People it finds. */
        val archivedMatches: Int = 0,
        /** For the Ring filter sheet, innermost first. */
        val rings: List<Ring> = emptyList(),
        /** For the Tags filter sheet. */
        val tags: List<Tag> = emptyList(),
    ) : PeopleUiState
}

/** What the People list's controls do (the ViewModel; screenshot tests pass a no-op). */
interface PeopleListActions {
    fun setSort(sort: PeopleSort)

    fun setRingFilter(ids: Set<Long>)

    fun setTagFilter(ids: Set<Long>)

    fun toggleDue()

    fun toggleDateSoon()

    fun clearFilters()

    fun openArchived()

    fun closeArchived()
}

/**
 * The People tab: everyone, searched through an in-memory [SearchIndex] rebuilt whenever their
 * data changes, filtered with a [PeopleFilterState] and sorted by the backed-up [PeopleSort].
 *
 * The [query], the filters and the Archived view live here only: they survive switching tabs and
 * opening a Person (this ViewModel stays with the tab's root entry) and are gone with the process.
 * Nothing about them is saved, not even for a process-death restore.
 */
@HiltViewModel
class PeopleViewModel @Inject constructor(
    personRepository: PersonRepository,
    ringRepository: RingRepository,
    private val preferences: PreferencesRepository,
    photoStore: PhotoStore,
    clock: Clock,
    requests: PeopleRequests,
    @DefaultDispatcher indexDispatcher: CoroutineDispatcher,
) : ViewModel(), PeopleListActions {

    /** The search bar's text. */
    val query = TextFieldState()

    private val filterState = PeopleFilterState()
    private val archivedView = MutableStateFlow(false)
    private val dateChecks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private class Indexed(val people: List<PersonSummary>, val index: SearchIndex)

    private class DayRows(val rows: List<PersonRow>, val index: SearchIndex, val today: LocalDate)

    private class Controls(val query: String, val filters: PeopleFilters, val archivedView: Boolean, val sort: PeopleSort)

    private val indexed = personRepository.observeEveryone()
        .map { everyone -> Indexed(everyone, SearchIndex(everyone.map(PersonSummary::search))) }
        .flowOn(indexDispatcher)

    private val dayRows = combine(indexed, todayFlow(clock, dateChecks)) { indexed, today ->
        DayRows(
            rows = indexed.people.map {
                PersonRow(
                    summary = it,
                    photo = it.photoFileName?.let(photoStore::file),
                    due = it.keepInTouch.on(today),
                    dateSoon = DateSoon.of(it.importantDates, today),
                )
            },
            index = indexed.index,
            today = today,
        )
    }

    private val controls = combine(
        snapshotFlow { query.text.toString() },
        filterState.filters,
        archivedView,
        preferences.preferences.map { it.peopleSort }.distinctUntilChanged(),
        ::Controls,
    )

    val uiState: StateFlow<PeopleUiState> =
        combine(dayRows, ringRepository.observeRings(), personRepository.observeTags(), controls) { day, rings, tags, controls ->
            loaded(day, rings, tags, controls)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState.Loading)

    init {
        // "Who else likes jazz?" and Tags → Show people, sent from other tabs.
        viewModelScope.launch {
            requests.pending.filterNotNull().collect { request ->
                when (request) {
                    is PeopleRequest.Search -> {
                        query.setTextAndPlaceCursorAtEnd(request.text)
                        filterState.clear()
                    }
                    is PeopleRequest.ShowTag -> {
                        query.clearText()
                        filterState.set(PeopleFilters(tagIds = setOf(request.tagId)))
                    }
                }
                archivedView.value = false
                requests.consume(request)
            }
        }
    }

    private fun loaded(day: DayRows, rings: List<Ring>, tags: List<Tag>, controls: Controls): PeopleUiState.Loaded {
        val filters = controls.filters.existing(rings.mapTo(HashSet(), Ring::id), tags.mapTo(HashSet(), Tag::id))
        val hits = day.index.search(controls.query)
        val (archived, active) = day.rows.partition { it.summary.isArchived }
        fun List<PersonRow>.matching(): List<PersonRow> =
            if (hits == null) this else mapNotNull { row -> hits[row.summary.id]?.let { row.copy(hit = it) } }
        val people = if (controls.archivedView) {
            archived.matching()
        } else {
            active.filter { filters.matches(it.filterFacts) }.matching()
        }
        return PeopleUiState.Loaded(
            people = people,
            today = day.today,
            sort = controls.sort,
            searching = hits != null,
            filters = filters,
            archivedView = controls.archivedView,
            hasPeople = active.isNotEmpty(),
            archivedCount = archived.size,
            archivedMatches = if (hits == null || controls.archivedView) 0 else archived.count { it.summary.id in hits },
            rings = rings.sortedBy(Ring::position),
            tags = tags,
        )
    }

    /** The screen resumed: the day may have changed while Orbit was in the background. */
    fun checkDate() {
        dateChecks.tryEmit(Unit)
    }

    override fun setSort(sort: PeopleSort) {
        viewModelScope.launch { preferences.update { it.copy(peopleSort = sort) } }
    }

    override fun setRingFilter(ids: Set<Long>) = filterState.setRings(ids)

    override fun setTagFilter(ids: Set<Long>) = filterState.setTags(ids)

    override fun toggleDue() = filterState.toggleDue()

    override fun toggleDateSoon() = filterState.toggleDateSoon()

    override fun clearFilters() = filterState.clear()

    override fun openArchived() {
        archivedView.value = true
    }

    override fun closeArchived() {
        archivedView.value = false
    }
}
