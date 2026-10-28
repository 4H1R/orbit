package dev.alializadeh.orbit.orbitview

import dev.alializadeh.orbit.due.DueState
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.language.OrbitLanguage
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.DateSoon
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.NameScript
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.search.FilterFacts
import dev.alializadeh.orbit.search.PeopleFilters
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A Ring as the Orbit view draws it: [index] 0 is the innermost. */
data class OrbitRing(val id: Long, val name: String, val index: Int)

/** A Person's nearest Important date within the badge's 30 days (TalkBack: "birthday in 5 days"). */
data class NearestDate(val type: ImportantDateType, val label: String?, val daysAway: Int)

/** One non-Archived Person on the Orbit view. */
data class OrbitPerson(
    val id: Long,
    val names: PersonNames,
    val photo: File?,
    /** Their Ring's place, 0 innermost. */
    val ringIndex: Int,
    val due: DueState,
    /** How far they've drifted out, 0 to 1 (see [driftOf]). */
    val drift: Float,
    /** The apricot badge: an Important date in the next 30 days, filled when today. */
    val dateSoon: DateSoon?,
    val nearestDate: NearestDate?,
    /** The filters leave them in (always, with no filter on); the others are dimmed. */
    val matches: Boolean,
    val primaryContactMethod: ContactMethod?,
) {
    val isDue: Boolean get() = due.isDue

    /** Due or date-badged: never smaller than Small, and drawn on top. */
    val isProminent: Boolean get() = due.isDue || dateSoon != null
}

sealed interface OrbitUiState {
    data object Loading : OrbitUiState

    data class Loaded(
        /** Innermost first, at most 6. */
        val rings: List<OrbitRing>,
        /** Everyone who isn't Archived, oldest first. */
        val people: List<OrbitPerson>,
        val today: LocalDate,
        val filters: PeopleFilters = PeopleFilters(),
        /** For the Ring filter sheet. */
        val allRings: List<Ring> = emptyList(),
        /** For the Tags filter sheet. */
        val tags: List<Tag> = emptyList(),
    ) : OrbitUiState {
        val hasPeople: Boolean get() = people.isNotEmpty()

        /** Filters are on and match nobody: "No one matches · Clear". */
        val nobodyMatches: Boolean get() = filters.isActive && people.isNotEmpty() && people.none(OrbitPerson::matches)
    }
}

/**
 * How far a Due Person drifts: `min(days past Due ÷ Cadence, 1)`, with the Cadence in days from
 * their last contact. 0 for anyone not Due (Snoozed People included) or without a Cadence.
 */
fun driftOf(keepInTouch: KeepInTouch, due: DueState): Float {
    if (!due.isDue) return 0f
    val cadenceDueOn = keepInTouch.cadenceDueOn ?: return 0f
    val cadenceDays = ChronoUnit.DAYS.between(keepInTouch.lastContact, cadenceDueOn)
    if (cadenceDays <= 0) return 1f
    return (due.daysOverdue.toFloat() / cadenceDays).coerceIn(0f, 1f)
}

/** The Important date nearest to [today], if it's within the badge's 30 days. */
fun nearestDate(dates: List<ImportantDate>, today: LocalDate): NearestDate? {
    val nearest = dates.minByOrNull { ChronoUnit.DAYS.between(today, it.date.nextOccurrence(today).date) } ?: return null
    val days = ChronoUnit.DAYS.between(today, nearest.date.nextOccurrence(today).date).toInt()
    return if (days <= DateSoon.DATE_SOON_DAYS) NearestDate(nearest.type, nearest.label, days) else null
}

/**
 * The Orbit view's model of [everyone] (Archived People left out) on [rings] on [today], with
 * [filters] deciding who's dimmed. Filters naming removed Rings or Tags are pruned first.
 */
fun orbitState(
    everyone: List<PersonSummary>,
    rings: List<Ring>,
    tags: List<Tag>,
    today: LocalDate,
    filters: PeopleFilters,
    photo: (String) -> File?,
): OrbitUiState.Loaded {
    val ordered = rings.sortedWith(compareBy(Ring::position, Ring::id)).take(Ring.MAX_COUNT)
    val indexById = ordered.withIndex().associate { (index, ring) -> ring.id to index }
    val existing = filters.existing(rings.mapTo(HashSet(), Ring::id), tags.mapTo(HashSet(), Tag::id))
    val people = everyone.asSequence()
        .filterNot(PersonSummary::isArchived)
        .sortedBy(PersonSummary::id)
        .map { summary ->
            val due = summary.keepInTouch.on(today)
            val dateSoon = DateSoon.of(summary.importantDates, today)
            OrbitPerson(
                id = summary.id,
                names = summary.names,
                photo = summary.photoFileName?.let(photo),
                // A Person always has a Ring; past the sixth (never stored) they'd sit on the outermost.
                ringIndex = indexById[summary.ringId] ?: (ordered.size - 1).coerceAtLeast(0),
                due = due,
                drift = driftOf(summary.keepInTouch, due),
                dateSoon = dateSoon,
                nearestDate = if (dateSoon != null) nearestDate(summary.importantDates, today) else null,
                matches = existing.matches(FilterFacts(summary.ringId, summary.tagIds, isDue = due.isDue, hasDateSoon = dateSoon != null)),
                primaryContactMethod = summary.primaryContactMethod,
            )
        }
        .toList()
    return OrbitUiState.Loaded(
        rings = ordered.mapIndexed { index, ring -> OrbitRing(ring.id, ring.name, index) },
        people = people,
        today = today,
        filters = existing,
        allRings = ordered,
        tags = tags,
    )
}

/** A Ring for TalkBack: its heading counts only the People the filters leave in. */
data class OrbitRingReading(val ring: OrbitRing, val people: List<OrbitPerson>) {
    val dueCount: Int get() = people.count(OrbitPerson::isDue)
}

/**
 * TalkBack's reading order: Ring by Ring, innermost first; within a Ring the Due People first
 * (longest since their last contact first), then everyone else, by name with [names] (the UI
 * language's collation). People dimmed by a filter are left out.
 */
fun talkBackOrder(rings: List<OrbitRing>, people: List<OrbitPerson>, displayName: (OrbitPerson) -> String, names: Comparator<String>): List<OrbitRingReading> {
    val byName = Comparator<OrbitPerson> { a, b -> names.compare(displayName(a), displayName(b)) }
    val order = compareByDescending<OrbitPerson> { it.isDue }
        .then { a, b -> if (a.isDue && b.isDue) b.due.daysSinceContact.compareTo(a.due.daysSinceContact) else 0 }
        .then(byName)
        .thenBy(OrbitPerson::id)
    val matching = people.filter(OrbitPerson::matches).groupBy(OrbitPerson::ringIndex)
    return rings.map { ring -> OrbitRingReading(ring, matching[ring.index].orEmpty().sortedWith(order)) }
}

/**
 * The name under an Avatar: their Nickname when it's written in the UI's script, otherwise the
 * first word of their displayed name ("Sara" for Sara Ahmadi).
 */
fun PersonNames.orbitLabel(language: OrbitLanguage): String {
    val nickname = nickname?.trim()?.takeIf(String::isNotEmpty)
    if (nickname != null && NameScript.detect(nickname) == NameScript.of(language)) return nickname
    val shown = displayName(language).trim()
    return shown.split(Whitespace).firstOrNull { it.isNotEmpty() } ?: shown
}

private val Whitespace = Regex("\\s+")
