package dev.alializadeh.orbit.testing

import dev.alializadeh.orbit.person.PeopleSort
import dev.alializadeh.orbit.person.list.PeopleListActions

/** People list controls that do nothing (screenshots). */
object NoPeopleListActions : PeopleListActions {
    override fun setSort(sort: PeopleSort) = Unit

    override fun setRingFilter(ids: Set<Long>) = Unit

    override fun setTagFilter(ids: Set<Long>) = Unit

    override fun toggleDue() = Unit

    override fun toggleDateSoon() = Unit

    override fun clearFilters() = Unit

    override fun openArchived() = Unit

    override fun closeArchived() = Unit
}
