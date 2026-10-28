package dev.alializadeh.orbit.search

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What another screen asks the People tab to show. */
sealed interface PeopleRequest {
    /** A People search for [text]: a Likes, Dislikes or Can help with chip on Person detail. */
    data class Search(val text: String) : PeopleRequest

    /** Everyone with Tag [tagId]: Settings → Tags → Show people. */
    data class ShowTag(val tagId: Long) : PeopleRequest
}

/**
 * Hands a [PeopleRequest] to the People tab's ViewModel, which may not exist yet (the tab not
 * opened since the app started): it waits here until consumed. In memory only.
 */
@Singleton
class PeopleRequests @Inject constructor() {
    private val state = MutableStateFlow<PeopleRequest?>(null)

    val pending: StateFlow<PeopleRequest?> = state.asStateFlow()

    fun send(request: PeopleRequest) {
        state.value = request
    }

    fun consume(request: PeopleRequest) {
        state.compareAndSet(request, null)
    }
}

/** The shell's way to send [PeopleRequests] (then it switches to the People tab). */
@HiltViewModel
class PeopleLinksViewModel @Inject constructor(private val requests: PeopleRequests) : ViewModel() {
    fun search(text: String) = requests.send(PeopleRequest.Search(text))

    fun showTag(tagId: Long) = requests.send(PeopleRequest.ShowTag(tagId))
}
