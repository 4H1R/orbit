package dev.alializadeh.orbit.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the filters look at for one Person on one day. */
data class FilterFacts(
    val ringId: Long,
    val tagIds: Set<Long>,
    /** Due now (Snoozed People aren't). */
    val isDue: Boolean,
    /** An Important date today or in the next 30 days. */
    val hasDateSoon: Boolean,
)

/**
 * The People filters (ticket 12): Ring (any of [ringIds]), Tags (any of [tagIds]), Due, and
 * Important date soon. Different filters combine with AND; an empty set or false is "not
 * filtering". Archived isn't a filter but a separate view. The People list and the Orbit view
 * (slice 9) share these definitions, each with its own [PeopleFilterState].
 */
data class PeopleFilters(
    val ringIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val due: Boolean = false,
    val dateSoon: Boolean = false,
) {
    /** Any filter is on: the "Filtered · Clear" bar shows. */
    val isActive: Boolean get() = ringIds.isNotEmpty() || tagIds.isNotEmpty() || due || dateSoon

    fun matches(person: FilterFacts): Boolean =
        (ringIds.isEmpty() || person.ringId in ringIds) &&
            (tagIds.isEmpty() || person.tagIds.any { it in tagIds }) &&
            (!due || person.isDue) &&
            (!dateSoon || person.hasDateSoon)

    /** Without the Rings and Tags that no longer exist (removed or merged while picked). */
    fun existing(ringIds: Set<Long>, tagIds: Set<Long>): PeopleFilters {
        val rings = this.ringIds.intersect(ringIds)
        val tags = this.tagIds.intersect(tagIds)
        return if (rings.size == this.ringIds.size && tags.size == this.tagIds.size) this else copy(ringIds = rings, tagIds = tags)
    }
}

/**
 * One screen's filter state: a tab owns one in its ViewModel, so it survives tab switches and
 * Person detail but not a new process. Nothing here is ever stored.
 */
class PeopleFilterState(initial: PeopleFilters = PeopleFilters()) {
    private val state = MutableStateFlow(initial)

    val filters: StateFlow<PeopleFilters> = state.asStateFlow()

    fun set(filters: PeopleFilters) {
        state.value = filters
    }

    fun setRings(ids: Set<Long>) = state.update { it.copy(ringIds = ids) }

    fun setTags(ids: Set<Long>) = state.update { it.copy(tagIds = ids) }

    fun toggleDue() = state.update { it.copy(due = !it.due) }

    fun toggleDateSoon() = state.update { it.copy(dateSoon = !it.dateSoon) }

    fun clear() {
        state.value = PeopleFilters()
    }
}
