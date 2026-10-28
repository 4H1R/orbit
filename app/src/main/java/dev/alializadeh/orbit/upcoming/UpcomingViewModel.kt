package dev.alializadeh.orbit.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alializadeh.orbit.person.photo.PhotoStore
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface UpcomingUiState {
    data object Loading : UpcomingUiState

    data class Loaded(
        val today: LocalDate,
        val feed: UpcomingFeed,
        /** Each Person's photo file by Person id, for those who have one. */
        val photos: Map<Long, File> = emptyMap(),
        /** "Show all (n)" was tapped: every Due Person shows, not only the first [DUE_ROWS]. */
        val showAllDue: Boolean = false,
    ) : UpcomingUiState {
        /** Nothing to show in any section: the screen shows an empty state instead. */
        val isEmpty: Boolean get() = feed.today.isEmpty() && feed.due.isEmpty() && feed.comingUp.isEmpty()

        /** The Due rows on screen: the first [DUE_ROWS], or all of them after "Show all". */
        val dueShown: List<DuePerson> get() = if (showAllDue) feed.due else feed.due.take(DUE_ROWS)

        /** Whether there are more Due People than [DUE_ROWS], so "Show all (n)" / "Show fewer" appears. */
        val dueHasMore: Boolean get() = feed.due.size > DUE_ROWS
    }

    companion object {
        /** The Due section shows this many rows until "Show all (n)". */
        const val DUE_ROWS = 5
    }
}

/**
 * Upcoming (Home). The feed is recomputed whenever People change and whenever the date does
 * (midnight while open, or [checkDate] when the screen resumes).
 */
@HiltViewModel
class UpcomingViewModel @Inject constructor(
    upcomingRepository: UpcomingRepository,
    photoStore: PhotoStore,
    clock: Clock,
) : ViewModel() {

    private val dateChecks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val showAllDue = MutableStateFlow(false)

    val uiState: StateFlow<UpcomingUiState> =
        combine(upcomingRepository.observePeople(), todayFlow(clock, dateChecks), showAllDue) { people, today, showAll ->
            val feed = UpcomingFeed.of(people, today)
            UpcomingUiState.Loaded(
                today = today,
                feed = feed,
                photos = people
                    .filter { !it.isArchived && it.photoFileName != null }
                    .associate { it.id to photoStore.file(it.photoFileName!!) },
                showAllDue = showAll,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpcomingUiState.Loading)

    /** "Show all (n)" (true) expands the Due section in place; "Show fewer" (false) folds it back. */
    fun showAllDue(show: Boolean) {
        showAllDue.value = show
    }

    /** The screen resumed: the day may have changed while Orbit was in the background. */
    fun checkDate() {
        dateChecks.tryEmit(Unit)
    }
}
