package dev.alializadeh.orbit.orbitview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.DefaultDispatcher
import dev.alializadeh.orbit.person.PersonRepository
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.ring.RingRepository
import dev.alializadeh.orbit.search.PeopleFilterState
import dev.alializadeh.orbit.upcoming.todayFlow
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** What the Orbit view's filter controls do (the ViewModel; screenshot tests pass a no-op). */
interface OrbitFilterActions {
    fun setRingFilter(ids: Set<Long>)

    fun setTagFilter(ids: Set<Long>)

    fun toggleDue()

    fun toggleDateSoon()

    fun clearFilters()
}

/**
 * The Orbit tab: everyone who isn't Archived on their Ring, with Due and the date badge worked out
 * for today (the date-change flow moves the drift at midnight), and the tab's own filters.
 *
 * The filters live here only, separate from the People tab's: they survive switching tabs and
 * opening a Person (this ViewModel stays with the tab's root entry) and are gone with the process.
 */
@HiltViewModel
class OrbitViewModel @Inject constructor(
    personRepository: PersonRepository,
    ringRepository: RingRepository,
    photoStore: PhotoStore,
    clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel(), OrbitFilterActions {

    private val filterState = PeopleFilterState()
    private val dateChecks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val uiState: StateFlow<OrbitUiState> = combine(
        personRepository.observeEveryone(),
        ringRepository.observeRings(),
        personRepository.observeTags(),
        todayFlow(clock, dateChecks),
        filterState.filters,
    ) { everyone, rings, tags, today, filters ->
        orbitState(everyone, rings, tags, today, filters, photoStore::file)
    }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrbitUiState.Loading)

    /** The screen resumed: the day may have changed while Orbit was in the background. */
    fun checkDate() {
        dateChecks.tryEmit(Unit)
    }

    override fun setRingFilter(ids: Set<Long>) = filterState.setRings(ids)

    override fun setTagFilter(ids: Set<Long>) = filterState.setTags(ids)

    override fun toggleDue() = filterState.toggleDue()

    override fun toggleDateSoon() = filterState.toggleDateSoon()

    override fun clearFilters() = filterState.clear()
}
