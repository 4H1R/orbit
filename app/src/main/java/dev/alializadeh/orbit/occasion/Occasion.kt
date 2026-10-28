package dev.alializadeh.orbit.occasion

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.person.Person
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.reminders.LeadTime
import dev.alializadeh.orbit.ring.Ring

/**
 * The built-in Occasions (PRD v1.1). Their names and Wishes follow the UI language; their day is
 * fixed. Stored by name: never rename a constant.
 */
enum class BuiltInOccasion(val date: RecurringDate) {
    Nowruz(RecurringDate(CalendarSystem.SolarHijri, 1, 1)),
    Yalda(RecurringDate(CalendarSystem.SolarHijri, 9, 30)),
    Sepandarmazgan(RecurringDate(CalendarSystem.SolarHijri, 11, 29)),
    TeachersDay(RecurringDate(CalendarSystem.SolarHijri, 2, 12)),
    MothersDay(RecurringDate(CalendarSystem.LunarHijri, 6, 20)),
    FathersDay(RecurringDate(CalendarSystem.LunarHijri, 7, 13)),
    EidAlFitr(RecurringDate(CalendarSystem.LunarHijri, 10, 1)),
    EidAlAdha(RecurringDate(CalendarSystem.LunarHijri, 12, 10)),
    EidAlGhadir(RecurringDate(CalendarSystem.LunarHijri, 12, 18)),
    NewYearsDay(RecurringDate(CalendarSystem.Gregorian, 1, 1)),
    Christmas(RecurringDate(CalendarSystem.Gregorian, 12, 25)),
    ValentinesDay(RecurringDate(CalendarSystem.Gregorian, 2, 14)),
}

/**
 * A yearly day that isn't one Person's, on which the user reaches out to a group (glossary:
 * Occasion). Either [builtIn] (named by the app) or the user's own, named [label]. The [date] never
 * has a year. Who it's for is [forEveryone], or the Rings, Tags and People in [OccasionRing],
 * [OccasionTag] and [OccasionPerson].
 */
@Entity(tableName = "occasion")
data class Occasion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val builtIn: BuiltInOccasion? = null,
    /** The user's name for their own Occasion; null for a built-in one. */
    val label: String? = null,
    /** (Calendar, month, day), as columns `calendar`, `month`, `day`, `year` (always null). */
    @Embedded val date: RecurringDate,
    val forEveryone: Boolean = false,
    /** Null: the default lead times; empty: No reminders (it still shows on Upcoming). */
    val leadTimes: Set<LeadTime>? = null,
)

@Entity(
    tableName = "occasion_ring",
    primaryKeys = ["occasionId", "ringId"],
    foreignKeys = [
        ForeignKey(entity = Occasion::class, parentColumns = ["id"], childColumns = ["occasionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Ring::class, parentColumns = ["id"], childColumns = ["ringId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("ringId")],
)
data class OccasionRing(val occasionId: Long, val ringId: Long)

@Entity(
    tableName = "occasion_tag",
    primaryKeys = ["occasionId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = Occasion::class, parentColumns = ["id"], childColumns = ["occasionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class OccasionTag(val occasionId: Long, val tagId: Long)

@Entity(
    tableName = "occasion_person",
    primaryKeys = ["occasionId", "personId"],
    foreignKeys = [
        ForeignKey(entity = Occasion::class, parentColumns = ["id"], childColumns = ["occasionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId")],
)
data class OccasionPerson(val occasionId: Long, val personId: Long)

/** An Occasion with who it's for. */
data class OccasionWithAudience(
    val occasion: Occasion,
    val ringIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val personIds: Set<Long> = emptySet(),
) {
    val id: Long get() = occasion.id
}

/** An Occasion as the editor holds it: a new one when [id] is null. */
data class OccasionDraft(
    val id: Long? = null,
    val builtIn: BuiltInOccasion? = null,
    val label: String? = null,
    val date: RecurringDate,
    val forEveryone: Boolean = false,
    val ringIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val personIds: Set<Long> = emptySet(),
    val leadTimes: Set<LeadTime>? = null,
) {
    /**
     * What's stored: a built-in Occasion keeps its own day and no label; the user's own has a
     * trimmed label; the date never has a year; Everyone needs no list.
     */
    fun normalised(): OccasionDraft = copy(
        label = if (builtIn != null) null else label?.trim()?.takeIf(String::isNotEmpty),
        date = builtIn?.date ?: date.copy(year = null),
        ringIds = if (forEveryone) emptySet() else ringIds,
        tagIds = if (forEveryone) emptySet() else tagIds,
        personIds = if (forEveryone) emptySet() else personIds,
    )

    /** A built-in one needs nothing more; the user's own needs a name. */
    val canSave: Boolean get() = builtIn != null || !label.isNullOrBlank()

    companion object {
        fun of(occasion: OccasionWithAudience) = OccasionDraft(
            id = occasion.id,
            builtIn = occasion.occasion.builtIn,
            label = occasion.occasion.label,
            date = occasion.occasion.date,
            forEveryone = occasion.occasion.forEveryone,
            ringIds = occasion.ringIds,
            tagIds = occasion.tagIds,
            personIds = occasion.personIds,
            leadTimes = occasion.occasion.leadTimes,
        )

        /** A new built-in Occasion: for everyone, with the default lead times. */
        fun builtIn(occasion: BuiltInOccasion) = OccasionDraft(builtIn = occasion, date = occasion.date, forEveryone = true)
    }
}
