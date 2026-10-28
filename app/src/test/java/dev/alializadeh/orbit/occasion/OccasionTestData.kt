package dev.alializadeh.orbit.occasion

import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.testing.NeverDue
import dev.alializadeh.orbit.upcoming.UpcomingPerson

/** A Person for Occasion tests: in Ring [ringId] (at [ringPosition]) with [tagIds]. */
fun occasionPerson(id: Long, ringId: Long = 1, ringPosition: Int = 0, tagIds: Set<Long> = emptySet(), archived: Boolean = false) =
    UpcomingPerson(
        id = id,
        names = PersonNames("Person $id"),
        photoFileName = null,
        isArchived = archived,
        primaryContactMethod = null,
        importantDates = emptyList(),
        openGiftIdeas = emptyList(),
        keepInTouch = NeverDue,
        ringPosition = ringPosition,
        ringId = ringId,
        tagIds = tagIds,
    )

fun occasionOf(
    id: Long,
    date: RecurringDate,
    forEveryone: Boolean = false,
    ringIds: Set<Long> = emptySet(),
    tagIds: Set<Long> = emptySet(),
    personIds: Set<Long> = emptySet(),
    builtIn: BuiltInOccasion? = null,
    label: String? = if (builtIn == null) "Book club" else null,
    leadTimes: Set<LeadTime>? = null,
) = OccasionWithAudience(
    occasion = Occasion(id = id, builtIn = builtIn, label = label, date = date, forEveryone = forEveryone, leadTimes = leadTimes),
    ringIds = ringIds,
    tagIds = tagIds,
    personIds = personIds,
)
