package dev.alializadeh.orbit.orbitview

import dev.alializadeh.orbit.calendar.CalendarSystem
import dev.alializadeh.orbit.calendar.RecurringDate
import dev.alializadeh.orbit.due.KeepInTouch
import dev.alializadeh.orbit.person.ContactMethod
import dev.alializadeh.orbit.person.ImportantDate
import dev.alializadeh.orbit.person.ImportantDateType
import dev.alializadeh.orbit.person.PersonNames
import dev.alializadeh.orbit.person.PersonSummary
import dev.alializadeh.orbit.person.Tag
import dev.alializadeh.orbit.person.contact.ContactType
import dev.alializadeh.orbit.ring.Cadence
import dev.alializadeh.orbit.ring.Ring
import java.time.LocalDate

/** The day the Orbit view's tests and screenshots are taken on. */
internal val OrbitToday: LocalDate = LocalDate.of(2026, 10, 28)

internal val EnOrbitRings = listOf(
    Ring(1, "Inner", 0, Cadence.days(14)),
    Ring(2, "Close", 1, Cadence.days(30)),
    Ring(3, "Friends", 2, Cadence.days(90)),
    Ring(4, "Acquaintances", 3, null),
)

internal val FaOrbitRings = listOf(
    Ring(1, "صمیمی", 0, Cadence.days(14)),
    Ring(2, "نزدیک", 1, Cadence.days(30)),
    Ring(3, "دوستان", 2, Cadence.days(90)),
    Ring(4, "آشنایان", 3, null),
)

internal val EnOrbitTags = listOf(Tag(1, "Work"), Tag(2, "Gym"), Tag(3, "University"))
internal val FaOrbitTags = listOf(Tag(1, "کار"), Tag(2, "باشگاه"), Tag(3, "دانشگاه"))

/** Someone for the Orbit view's tests: their Ring, how long since they were last in touch, a date in some days. */
internal fun orbitSummary(
    id: Long,
    name: String,
    ring: Ring,
    lastDaysAgo: Long?,
    nickname: String? = null,
    otherScriptName: String? = null,
    birthdayInDays: Long? = null,
    snoozedForDays: Long? = null,
    tagIds: Set<Long> = emptySet(),
    phone: String? = null,
    archived: Boolean = false,
): PersonSummary {
    val birthday = birthdayInDays?.let { OrbitToday.plusDays(it) }
    return PersonSummary(
        id = id,
        names = PersonNames(name, otherScriptName, nickname),
        photoFileName = null,
        ringName = ring.name,
        keepInTouch = KeepInTouch(
            cadence = ring.cadence,
            addedOn = OrbitToday.minusDays(400),
            lastInteraction = lastDaysAgo?.let(OrbitToday::minusDays),
            snoozedUntil = snoozedForDays?.let(OrbitToday::plusDays),
            isArchived = archived,
        ),
        ringPosition = ring.position,
        primaryContactMethod = phone?.let { ContactMethod(id, id, ContactType.Phone, it, isPrimary = true, position = 0) },
        importantDates = listOfNotNull(
            birthday?.let {
                ImportantDate(id = id, personId = id, type = ImportantDateType.Birthday, date = RecurringDate(CalendarSystem.Gregorian, it.monthValue, it.dayOfMonth, 1990), position = 0)
            },
        ),
        ringId = ring.id,
        tagIds = tagIds,
    )
}

private val EnFirst = listOf(
    "Sara", "Reza", "Mina", "Ali", "Laleh", "Kian", "Nazanin", "Omid", "Parisa", "Babak", "Camille", "Darius", "Élodie",
    "Farid", "Golnar", "Hana", "Robert", "Tom", "Yasmin", "Zahra", "Amir", "Bernard", "Leila", "Navid", "Shirin", "Arash",
    "Maryam", "Hamid", "Jonas", "Ines", "Marco", "Nora", "Lukas", "Emma", "Theo", "Ava", "Noah", "Mia", "Leo", "Ida",
)
private val EnLast = listOf("Ahmadi", "Tehrani", "Karimi", "Rezaei", "Moradi", "Jafari", "Hosseini", "Bagheri", "Roux", "Martin", "Klein", "Suzuki")
private val FaFirst = listOf(
    "سارا", "رضا", "مینا", "علی", "لاله", "کیان", "نازنین", "امید", "پریسا", "بابک", "کامیلا", "داریوش", "الهام",
    "فرید", "گلنار", "هانا", "رابرت", "تارا", "یاسمن", "زهرا", "امیر", "بهرام", "لیلا", "نوید", "شیرین", "آرش",
    "مریم", "حمید", "یونس", "ایران", "مهسا", "نرگس", "لقمان", "اِما", "تینا", "آوا", "نوا", "میترا", "لادن", "ایدا",
)
private val FaLast = listOf("احمدی", "تهرانی", "کریمی", "رضایی", "مرادی", "جعفری", "حسینی", "باقری", "روحانی", "مارتین", "کلاین", "سوزوکی")

/**
 * About twenty People across the four Rings: a few Due at different drifts (one on the outermost
 * Ring would be: it has no Cadence), one Snoozed, date badges today and soon, a nickname.
 */
internal fun orbitFewPeople(persian: Boolean): List<PersonSummary> {
    val rings = if (persian) FaOrbitRings else EnOrbitRings
    val first = if (persian) FaFirst else EnFirst
    val last = if (persian) FaLast else EnLast
    fun name(i: Int) = "${first[i]} ${last[i % last.size]}"
    val (inner, close, friends, acquaintances) = rings
    return listOf(
        orbitSummary(1, name(0), inner, 20, birthdayInDays = 5, phone = "+98 912 345 6789", tagIds = setOf(3)),
        orbitSummary(2, name(1), inner, 3, tagIds = setOf(1)),
        orbitSummary(3, name(2), inner, 30, tagIds = setOf(2)),
        orbitSummary(4, name(3), inner, 8, nickname = if (persian) "علی‌جان" else "Al"),
        orbitSummary(5, name(4), close, 12, birthdayInDays = 0),
        orbitSummary(6, name(5), close, 45, tagIds = setOf(2)),
        orbitSummary(7, name(6), close, 34, snoozedForDays = 4),
        orbitSummary(8, name(7), close, 2, tagIds = setOf(1)),
        orbitSummary(9, name(8), close, 70),
        orbitSummary(10, name(9), close, 20, tagIds = setOf(1, 2)),
        orbitSummary(11, name(10), friends, 120),
        orbitSummary(12, name(11), friends, 10, birthdayInDays = 21),
        orbitSummary(13, name(12), friends, 300, tagIds = setOf(3)),
        orbitSummary(14, name(13), friends, 40),
        orbitSummary(15, name(14), friends, 60, tagIds = setOf(1)),
        orbitSummary(16, name(15), friends, 5),
        orbitSummary(17, name(16), friends, 95),
        orbitSummary(18, name(17), acquaintances, 200),
        orbitSummary(19, name(18), acquaintances, null),
        orbitSummary(20, name(19), acquaintances, 400, birthdayInDays = 12),
        orbitSummary(21, name(20), friends, 800, archived = true),
    )
}

/** About two hundred People: the outer Rings crowd into Small and Dots. */
internal fun orbitManyPeople(persian: Boolean): List<PersonSummary> {
    val rings = if (persian) FaOrbitRings else EnOrbitRings
    val first = if (persian) FaFirst else EnFirst
    val last = if (persian) FaLast else EnLast
    val sizes = listOf(6, 22, 60, 112)
    var id = 0L
    return rings.flatMapIndexed { ringIndex, ring ->
        List(sizes[ringIndex]) {
            id++
            val i = id.toInt()
            val cadenceDays = ring.cadence?.count ?: 0
            // Every fifth is Due (drifting by different amounts), every eleventh has a birthday soon.
            val lastDaysAgo = when {
                cadenceDays > 0 && i % 5 == 0 -> cadenceDays + (i * 7L % cadenceDays)
                cadenceDays > 0 -> (i * 3L) % cadenceDays
                else -> 100L + i
            }
            orbitSummary(
                id = id,
                name = "${first[i % first.size]} ${last[(i / first.size + i) % last.size]}",
                ring = ring,
                lastDaysAgo = lastDaysAgo,
                birthdayInDays = if (i % 11 == 0) (i % 31).toLong() else null,
                tagIds = setOf((i % 3 + 1).toLong()),
            )
        }
    }
}
