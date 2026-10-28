package dev.alializadeh.orbit.occasion

import dev.alializadeh.orbit.upcoming.UpcomingPerson

/**
 * Who [this] Occasion is for, among [people]: everyone who isn't Archived, or those in any of its
 * Rings, Tags or People (any of them is enough). In Upcoming's order: innermost Ring first, then
 * the order they were added. The one place an Occasion's People are decided.
 */
fun OccasionWithAudience.audience(people: List<UpcomingPerson>): List<UpcomingPerson> =
    people
        .filterNot(UpcomingPerson::isArchived)
        .filter { person ->
            occasion.forEveryone ||
                person.id in personIds ||
                person.ringId in ringIds ||
                person.tagIds.any(tagIds::contains)
        }
        .sortedWith(compareBy<UpcomingPerson> { it.ringPosition }.thenBy { it.id })
